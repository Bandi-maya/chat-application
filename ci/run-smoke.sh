#!/usr/bin/env bash
# Runs inside the emulator-runner (which executes each script line separately,
# so a multi-line inline script cannot propagate the gradle exit code).
mkdir -p screenshots
adb logcat -c || true
# Job-lifetime process watch: timestamp qemu presence/RSS every 5s so a guest
# death is placed on the test timeline (run-127 emoji-real: logcat ended
# 06:39:25, qemu RSS froze, then qemu vanished) - discriminates crash vs kill.
( while true; do
    line=$(ps -eo pid,rss,command 2>/dev/null | grep -i "[q]emu" | head -3 | tr '\n' '|')
    if [ -z "$line" ]; then echo "$(date +%H:%M:%S) QEMU-GONE" >> screenshots/qemu-watch.txt; else echo "$(date +%H:%M:%S) $line" >> screenshots/qemu-watch.txt; fi
    sleep 5
  done ) &
QWATCH=$!
# Logcat reconnect: if the stream dies while the emulator is still up (run-127
# emoji-real logcat ended mid-run), restart it and mark the gap.
( while true; do
    adb logcat -v threadtime >> screenshots/logcat-full.txt 2>&1
    echo "$(date +%H:%M:%S) LOGCAT-STREAM-ENDED rc=$?" >> screenshots/qemu-watch.txt
    pgrep -f qemu >/dev/null 2>&1 || break
    sleep 2
  done ) &
LOGCATW=$!
gradle :app:connectedDebugAndroidTest --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
rc=$?
kill $QWATCH $LOGCATW 2>/dev/null || true
echo "--- qemu/logcat watch (job-lifetime) ---"
tail -30 screenshots/qemu-watch.txt 2>/dev/null || true
# Permission restore belongs outside instrumentation: pm revoke kills the app process.
if adb shell test -f /sdcard/Android/data/com.logybyte.chat/files/camera-permission-cleanup; then
  adb shell rm /sdcard/Android/data/com.logybyte.chat/files/camera-permission-cleanup
  adb shell pm revoke com.logybyte.chat android.permission.CAMERA || true
fi
adb pull /sdcard/Android/data/com.logybyte.chat/files/screenshots/. screenshots/ || true
sleep 2
adb logcat -b crash -d > screenshots/logcat-crash.txt 2>&1 || true
adb logcat -d -t 4000 > screenshots/logcat.txt 2>&1 || true
adb exec-out screencap -p > screenshots/final-screen.png 2>/dev/null || true
echo "--- final screen texts (uiautomator) ---"
adb shell uiautomator dump /sdcard/w.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/w.xml 2>/dev/null | grep -o 'text="[^"]*"' | head -60 || true
echo "--- crash buffer (adb logcat -b crash) ---"
tail -80 screenshots/logcat-crash.txt || true
echo "--- app lifecycle/crash lines from the full run log (AppsFilter noise removed) ---"
grep -v "AppsFilter\|UiDevice: Searching" screenshots/logcat-full.txt | grep -i "FATAL EXCEPTION\|Process: com.logybyte.chat\|AndroidRuntime\|Caused by\|has died\|ANR in\|PixelProof\|ActivityTaskManager.*com.logybyte.chat\|ActivityManager.*com.logybyte.chat\|Start proc.*com.logybyte.chat\|libjnidispatch\|UnsatisfiedLink\|ChatyApplication\|SIGSEGV\|DEBUG  " | head -150 || true
echo "--- logcat excerpt (app/crash) ---"
grep -i "AndroidRuntime\|FATAL EXCEPTION\|com.logybyte.chat\|chaty" screenshots/logcat.txt | grep -v "UiDevice: Searching\|AppsFilter" | tail -120 || true
bash ci/restart-check.sh
rrc=$?
echo "SMOKE-RC gradle=$rc restart=$rrc"
[ $rc -ne 0 ] && exit $rc
exit $rrc
