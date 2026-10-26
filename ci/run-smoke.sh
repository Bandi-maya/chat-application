#!/usr/bin/env bash
# Runs inside the emulator-runner (which executes each script line separately,
# so a multi-line inline script cannot propagate the gradle exit code).
mkdir -p screenshots
adb logcat -c || true
(adb logcat -v threadtime > screenshots/logcat-full.txt 2>&1 &) 
gradle :app:connectedDebugAndroidTest --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
rc=$?
# Permission restore belongs outside instrumentation: pm revoke kills the app process.
if adb shell test -f /sdcard/Android/data/com.example.chat/files/camera-permission-cleanup; then
  adb shell rm /sdcard/Android/data/com.example.chat/files/camera-permission-cleanup
  adb shell pm revoke com.example.chat android.permission.CAMERA || true
fi
adb pull /sdcard/Android/data/com.example.chat/files/screenshots/. screenshots/ || true
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
grep -v "AppsFilter\|UiDevice: Searching" screenshots/logcat-full.txt | grep -i "FATAL EXCEPTION\|Process: com.example.chat\|AndroidRuntime\|Caused by\|has died\|ANR in\|PixelProof\|ActivityTaskManager.*com.example.chat\|ActivityManager.*com.example.chat\|Start proc.*com.example.chat\|libjnidispatch\|UnsatisfiedLink\|ChatyApplication\|SIGSEGV\|DEBUG  " | head -150 || true
echo "--- logcat excerpt (app/crash) ---"
grep -i "AndroidRuntime\|FATAL EXCEPTION\|com.example.chat\|chaty" screenshots/logcat.txt | grep -v "UiDevice: Searching\|AppsFilter" | tail -120 || true
bash ci/restart-check.sh
rrc=$?
echo "SMOKE-RC gradle=$rc restart=$rrc"
[ $rc -ne 0 ] && exit $rc
exit $rrc
