#!/usr/bin/env bash
# Runs inside the emulator-runner (which executes each script line separately,
# so a multi-line inline script cannot propagate the gradle exit code).
mkdir -p screenshots
gradle :app:connectedDebugAndroidTest --no-daemon
rc=$?
adb pull /sdcard/Android/data/com.example.chat/files/screenshots/. screenshots/ || true
adb logcat -d -t 4000 > screenshots/logcat.txt 2>&1 || true
adb exec-out screencap -p > screenshots/final-screen.png 2>/dev/null || true
exit $rc
