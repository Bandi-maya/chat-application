#!/usr/bin/env bash
# Runs inside the emulator-runner (which executes each script line separately,
# so a multi-line inline script cannot propagate the gradle exit code).
mkdir -p screenshots
gradle :app:connectedDebugAndroidTest --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
rc=$?
adb pull /sdcard/Android/data/com.example.chat/files/screenshots/. screenshots/ || true
adb logcat -d -t 4000 > screenshots/logcat.txt 2>&1 || true
adb exec-out screencap -p > screenshots/final-screen.png 2>/dev/null || true
echo "--- final screen texts (uiautomator) ---"
adb shell uiautomator dump /sdcard/w.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/w.xml 2>/dev/null | grep -o 'text="[^"]*"' | head -60 || true
echo "--- logcat excerpt (app/crash) ---"
grep -i "AndroidRuntime\|FATAL EXCEPTION\|com.example.chat\|chaty" screenshots/logcat.txt | grep -v "UiDevice: Searching" | tail -120 || true
exit $rc
