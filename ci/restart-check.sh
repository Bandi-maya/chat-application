#!/usr/bin/env bash
# Host-side process-restart check (synthetic session only, no real account traffic).
# The debug APK is left installed by the instrumentation run. Seed a FAKE REST session
# (token "synthetic-test-token") into the app's own prefs via run-as, kill the app process
# from the host, cold start MainActivity twice, and observe from outside the app process:
# PID changes between launches, process alive, no FATAL EXCEPTION, authenticated shell on screen.
PKG=com.logybyte.chat
mkdir -p screenshots
RES=0
# Emulator exit/status capture (run-128: the emulator vanished silently during
# gradle - qemu alive at gradle end, host RAM fine, zero console error - then
# later shards lost the device). Record qemu/adb state at entry and exit so a
# dead emulator is attributed in the log instead of failing adb calls opaquely.
QEMU_PID=$(pgrep -f qemu-system | head -1 || true)
echo "RESTART-QEMU entry pid=${QEMU_PID:-none} state=$([ -n "$QEMU_PID" ] && echo alive || echo missing)"
adb devices -l | tr -d '\r' | sed 's/^/RESTART-ADB /'
# Kill-mechanism evidence (run-130: RESTART-QEMU proves the qemu PROCESS exits;
# whether the kernel OOM-killed it is unresolved). host-dmesg-tail exists but
# ends early; grep the ring buffer for kill signatures explicitly.
dmesg 2>/dev/null | grep -iE 'oom|killed process|out of memory|segfault' | tail -5 | sed 's/^/RESTART-KERNEL /' || echo "RESTART-KERNEL dmesg unavailable-or-no-matches"
adb shell am force-stop $PKG
USER_JSON='{&quot;id&quot;:&quot;synthetic-user-1&quot;,&quot;username&quot;:&quot;testuser&quot;,&quot;displayName&quot;:&quot;Test User&quot;,&quot;avatarInitials&quot;:&quot;T&quot;,&quot;avatarColorHex&quot;:&quot;0xFF6366F1&quot;,&quot;about&quot;:&quot;Synthetic&quot;,&quot;email&quot;:&quot;&quot;,&quot;phone&quot;:&quot;&quot;,&quot;avatarUrl&quot;:null}'
cat > /tmp/chaty_api_backend.xml <<XMLEOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="api_hub_token">synthetic-test-token</string>
    <string name="api_hub_user">${USER_JSON}</string>
</map>
XMLEOF
adb push /tmp/chaty_api_backend.xml /data/local/tmp/chaty_api_backend.xml >/dev/null
echo "RESTART-SEED begin"
adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs; cat > shared_prefs/chaty_api_backend.xml' < /data/local/tmp/chaty_api_backend.xml" || { echo "RESTART-SEED failed"; RES=1; }
adb shell "run-as $PKG ls -l shared_prefs/" | sed 's/^/RESTART-SEED /'
adb shell rm -f /data/local/tmp/chaty_api_backend.xml
adb logcat -c || true
PIDS=""
for i in 1 2; do
  adb shell am force-stop $PKG
  sleep 2
  adb shell am start -W -n $PKG/com.example.chat.MainActivity | tr -d '\r' | grep -i "Status\|LaunchState\|TotalTime" | sed "s/^/RESTART-START$i /"
  sleep 20
  PID=$(adb shell pidof $PKG | tr -d '\r' | awk '{print $1}')
  echo "RESTART-PID launch=$i pid=${PID:-none}"
  [ -z "$PID" ] && RES=1
  PIDS="$PIDS $PID"
  adb shell uiautomator dump /sdcard/r$i.xml >/dev/null 2>&1 || true
  # Reachable first-run notification education sheet: press its real "Not Now" button (tap at the
  # node's centre), as a user would, then re-dump. Pixels of the sheet are kept as 13-restart-launch$i-prompt.
  BOUNDS=$(adb shell cat /sdcard/r$i.xml 2>/dev/null | sed 's/></>\n</g' | grep 'text="Not Now"' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  if [ -n "$BOUNDS" ]; then
    set -- $BOUNDS
    adb exec-out screencap -p > screenshots/13-restart-launch$i-prompt.png 2>/dev/null || true
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    echo "RESTART-PROMPT launch=$i not-now-tapped"
    sleep 3
    adb shell uiautomator dump /sdcard/r$i.xml >/dev/null 2>&1 || true
  fi
  TXT=$(adb shell cat /sdcard/r$i.xml 2>/dev/null | grep -o 'text="[^"]*"' | head -40 | tr '\n' ' ')
  echo "RESTART-SCREEN launch=$i $TXT"
  echo "$TXT" | grep -q 'text="Chats"' && echo "RESTART-SHELL launch=$i authenticated-shell-label-present" || { echo "RESTART-SHELL launch=$i label-missing"; RES=1; }
  adb exec-out screencap -p > screenshots/13-restart-launch$i.png 2>/dev/null || true
  adb logcat -d | tr -d '\r' | grep -c "FATAL EXCEPTION" | sed "s/^/RESTART-FATAL-COUNT launch=$i /"
done
set -- $PIDS
[ "$1" != "$2" ] && echo "RESTART-PID-CHANGED $1 -> $2" || { echo "RESTART-PID-SAME"; RES=1; }
adb logcat -d | tr -d '\r' | grep -q "FATAL EXCEPTION" && { echo "RESTART-FATAL present"; adb logcat -d | grep -A12 "FATAL EXCEPTION" | head -30; RES=1; }
adb shell am force-stop $PKG
adb shell "run-as $PKG rm -f shared_prefs/chaty_api_backend.xml" && echo "RESTART-CLEANUP seeded prefs removed"
QEMU_PID2=$(pgrep -f qemu-system | head -1 || true)
echo "RESTART-QEMU exit pid=${QEMU_PID2:-none} state=$([ -n "$QEMU_PID2" ] && echo alive || echo missing)"
echo "RESTART-RESULT rc=$RES"
exit $RES
