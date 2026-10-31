#!/usr/bin/env bash
# Runs inside the emulator-runner (which executes each script line separately,
# so a multi-line inline script cannot propagate the gradle exit code).
mkdir -p screenshots
# Passive host-side evidence (retry-job pattern): qemu RSS + MemAvailable sampler, so a
# qemu disappearance has host telemetry instead of inference from guest logs alone.
while :; do p=$(pgrep -f qemu-system | head -1); if [ -n "$p" ]; then echo "{\"t\":$(date +%s),\"pid\":$p,\"rss_kb\":\"$(awk '/VmRSS/{print $2}' /proc/$p/status 2>/dev/null)\",\"memavail_kb\":\"$(awk '/MemAvailable/{print $2}' /proc/meminfo)\"}"; fi; sleep 5; done > screenshots/host-watch.jsonl &
hostwatch_pid=$!
# Evidence only: no camera opening or real account/hardware use. Bounds preserve failure artifacts.
timeout 20 adb shell dumpsys media.camera > screenshots/camera-inventory-before.txt 2>&1 || true
timeout 10 adb shell getprop ro.kernel.qemu > screenshots/emulator-identity.txt 2>&1 || true
python3 ci/collect-runtime-resources.py &
resource_pid=$!
finish_resources() { kill "$resource_pid" 2>/dev/null || true; wait "$resource_pid" 2>/dev/null || true; }
trap finish_resources EXIT
adb logcat -c || true
(adb logcat -v threadtime > screenshots/logcat-full.txt 2>&1 &) 
# Each isolated runner restores the same immutable candidate and gets a disjoint class partition.
classes=$(python3 ci/test-shards.py "${CHATY_TEST_SHARD:-all}")
filter_rc=$?
if [ "$filter_rc" -ne 0 ] || [ -z "$classes" ]; then
  echo "Invalid or empty instrumentation shard: ${CHATY_TEST_SHARD:-all}" >&2
  exit 2
fi
printf '%s\n' "$classes" > screenshots/instrumentation-class-filter.txt
printf '%s\n' "TEST-SHARD ${CHATY_TEST_SHARD:-all} classes=$classes"
(cd runtime-apks && sha256sum -c SHA256SUMS) || exit 2
adb install -r -t runtime-apks/app-debug.apk || exit 2
adb install -r -t runtime-apks/app-debug-androidTest.apk || exit 2
python3 ci/run-instrumentation-shard.py "$classes"
rc=$?
kill "$hostwatch_pid" 2>/dev/null || true
# Host kill-path evidence: kernel OOM/kill records, cgroup events, memory pressure.
( sudo dmesg 2>/dev/null | tail -60 || echo "dmesg unavailable" ) > screenshots/host-dmesg-tail.txt 2>&1 || true
( cat /sys/fs/cgroup/memory.events 2>/dev/null ; cat /proc/pressure/memory 2>/dev/null ; free -m ) > screenshots/host-memory-evidence.txt 2>&1 || true
# Emulator process stdout/stderr traces where the runner wrote them.
mkdir -p screenshots/emulator-crash
for f in /tmp/android-runner/*.log /tmp/android-runner/stdout* /tmp/android-runner/stderr* /tmp/android-runner/emulator*; do
  [ -f "$f" ] && cp "$f" screenshots/emulator-crash/ 2>/dev/null || true
done
timeout 20 adb shell dumpsys media.camera > screenshots/camera-inventory-after.txt 2>&1 || true
# A disconnected emulator must not block artifact preservation for the job's full timeout.
# Bounds each post-run adb operation and is inherited by restart-check.sh.
CHATY_ADB_BINARY=$(command -v adb)
export CHATY_ADB_BINARY
adb() { timeout 60 "$CHATY_ADB_BINARY" "$@"; }
export -f adb
# Permission restore belongs outside instrumentation: pm revoke kills the app process.
if adb shell test -f /sdcard/Android/data/com.logybyte.chat/files/camera-permission-cleanup; then
  adb shell rm /sdcard/Android/data/com.logybyte.chat/files/camera-permission-cleanup
  adb shell pm revoke com.logybyte.chat android.permission.CAMERA || true
fi
adb pull /sdcard/Android/data/com.logybyte.chat/files/screenshots/. screenshots/ || true
# Bounded audit: missing producer diagnostics must remain visible, not be inferred from source.
find screenshots -maxdepth 1 -type f -name 'hdr-*' -printf '%f %s bytes\n' | sort > screenshots/header-artifact-inventory.txt

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
