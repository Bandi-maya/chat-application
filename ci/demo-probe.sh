#!/usr/bin/env bash
# CI-only diagnostic probe (capture-only retry runs): fresh-launch the app,
# enter the offline demo, dismiss first-run overlays (App Lock prompt is
# in-memory per login; notification education is a persisted pref we
# pre-seed), then capture pixels + the accessibility tree of the clean home.
set +e
mkdir -p screenshots
adb shell pm clear com.example.chat
adb shell run-as com.example.chat sh -c "mkdir -p shared_prefs && printf '%s' \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><boolean name='chaty_notif_education_shown' value='true' /></map>\" > shared_prefs/FlutterSharedPreferences.xml" || true
cat > /tmp/tap.py <<'PY'
import re, sys
label = sys.argv[1]
try:
    data = open('/tmp/probe-win.xml', encoding='utf-8', errors='replace').read()
except Exception:
    sys.exit(0)
for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', data):
    if m.group(1).strip() == label:
        x1, y1, x2, y2 = map(int, m.group(2, 3, 4, 5))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
tap_text() {
  label="$1"; tries="$2"; i=0
  while [ "$i" -lt "$tries" ]; do
    adb shell uiautomator dump /sdcard/probe-win.xml >/dev/null 2>&1
    adb pull /sdcard/probe-win.xml /tmp/probe-win.xml >/dev/null 2>&1
    PT=$(python3 /tmp/tap.py "$label")
    if [ -n "$PT" ]; then adb shell input tap $PT; echo "probe tapped: $label at $PT"; return 0; fi
    i=$((i + 1)); sleep 3
  done
  echo "probe not found: $label"; return 1
}
adb shell monkey -p com.example.chat -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
# Fresh launch lands on Welcome ("Fast & Private"); the demo card lives on
# the LOGIN screen behind "Log In".
tap_text "Log In" 12
tap_text "Try the demo" 15
sleep 8
adb exec-out screencap -p > screenshots/demo-post-click.png
adb shell uiautomator dump /sdcard/demo-window.xml >/dev/null 2>&1
adb pull /sdcard/demo-window.xml screenshots/demo-window.xml
tap_text "Not now" 6
sleep 2
# Notification sheet ("Not Now", capital N) - distinct control from the App
# Lock prompt; dismissing persists (NotificationEducationStore.markShown).
tap_text "Not Now" 6
sleep 3
adb exec-out screencap -p > screenshots/demo-home-clean.png
adb shell uiautomator dump /sdcard/demo-window-clean.xml >/dev/null 2>&1
adb pull /sdcard/demo-window-clean.xml screenshots/demo-window-clean.xml
adb logcat -d -v threadtime -t 500 > screenshots/demo-probe-logcat.txt
echo "shell-driven probe done" > screenshots/demo-probe-status.txt

# Renderer A/B: force Vulkan-backed HWUI (skiavk) on the same emulator,
# relaunch into the RESTORED demo session (no fresh-login overlays), and
# recapture the same home. If the header paints here but not under the
# default SwiftShader GLES path, the blanking is renderer-layer behavior,
# not app code. Runs last; restores the prop afterwards.
adb shell setprop debug.hwui.renderer skiavk
adb shell getprop debug.hwui.renderer > screenshots/demo-probe-renderer.txt
adb shell am force-stop com.example.chat
adb shell monkey -p com.example.chat -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 12
adb exec-out screencap -p > screenshots/demo-home-vk.png
adb shell uiautomator dump /sdcard/demo-window-vk.xml >/dev/null 2>&1
adb pull /sdcard/demo-window-vk.xml screenshots/demo-window-vk.xml
adb shell setprop debug.hwui.renderer ""
