#!/usr/bin/env bash
# Host-side process-restart check (synthetic session only, no real account traffic).
# The debug APK is left installed by the instrumentation run. Seed a FAKE REST session
# (token "synthetic-test-token") into the app's own prefs via run-as, kill the app process
# from the host, cold start MainActivity twice, and observe from outside the app process:
# PID changes between launches, process alive, no FATAL EXCEPTION, authenticated shell on screen.
PKG=com.example.chat
mkdir -p screenshots
RES=0
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
  adb shell am start -W -n $PKG/.MainActivity | tr -d '\r' | grep -i "Status\|LaunchState\|TotalTime" | sed "s/^/RESTART-START$i /"
  sleep 20
  PID=$(adb shell pidof $PKG | tr -d '\r' | awk '{print $1}')
  echo "RESTART-PID launch=$i pid=${PID:-none}"
  [ -z "$PID" ] && RES=1
  PIDS="$PIDS $PID"
  adb shell uiautomator dump /sdcard/r$i.xml >/dev/null 2>&1 || true
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
echo "RESTART-RESULT rc=$RES"
exit $RES
