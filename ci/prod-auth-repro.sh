#!/usr/bin/env bash
# prod-auth-repro.sh - dispatch-gated live-backend login reproduction probe.
#
# Isolation phase of the phase-gated debugging process (Bandi, 2026-11-08):
# drives a REAL login with a user-authorized Normal demo account against the
# LIVE backend (https://api.chat.logybyte.in) three times on the emulator and records
# whether the reported post-login bounce (Home -> Welcome, no error message)
# reproduces. Credentials come from temporary masked CI secrets, so the
# UI path under test is exactly the login flow Bandi uses. No app code is
# changed; this script only installs, drives and observes.
#
# Evidence collected into prod-auth-evidence/:
#   timeline.log          UI state + app PID every 2s (PID change = process death)
#   verdict.txt           REACHED_HOME / BOUNCE_REPRODUCED / PROCESS_DIED
#   *.png                 screenshots on every state change
#   prod-auth-repro.mp4   full screen recording
#   logcat.txt            complete device log (crash stacks, keystore errors)
set -euo pipefail

PKG="com.logybyte.chaty"
ACTIVITY="com.example.chat.MainActivity"
APK="${1:?usage: prod-auth-repro.sh <path-to-apk>}"
USER_NAME="${CHATY_PROD_USER:?missing CHATY_PROD_USER}"
PASS="${CHATY_PROD_PASS:?missing CHATY_PROD_PASS}"
OUT="prod-auth-evidence"
mkdir -p "$OUT"
TIMELINE="$OUT/timeline.log"
: > "$TIMELINE"

log() { echo "$*" | tee -a "$TIMELINE"; }
cleanup() {
  adb shell pkill -2 screenrecord >/dev/null 2>&1 || true
  adb logcat -d > "$OUT/logcat-final.txt" 2>&1 || true
  adb exec-out screencap -p > "$OUT/final.png" 2>/dev/null || true
}
trap cleanup EXIT

shot() {
  adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null
  ui_dump > "$OUT/$1.xml" || true
  log "screenshot $1.png"
}

ui_dump() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr -d '\r'; }

# tap_text TEXT - tap the center of the first node whose text or content-desc
# matches exactly; substring match as fallback.
tap_text() {
  local want="$1"
  ui_dump > "$OUT/current.xml"
  python3 - "$want" "$OUT/current.xml" <<'PY'
import sys, re, subprocess
want = sys.argv[1]
xml = open(sys.argv[2]).read()
def attr(n, a):
    m = re.search(a + '="([^"]*)"', n)
    return m.group(1) if m else ''
def center(b):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return ((x1 + x2) // 2, (y1 + y2) // 2)
exact, partial = [], []
for n in re.findall(r'<node[^>]*>', xml):
    t, d, b = attr(n, 'text'), attr(n, 'content-desc'), attr(n, 'bounds')
    if not b:
        continue
    if t == want or d == want:
        rank = 0 if attr(n, "class") == "android.widget.EditText" else (1 if d == want else (2 if attr(n, "clickable") == "true" else 3))
        exact.append((rank, b))
    elif want in t or want in d:
        partial.append((4, b))
for cand in (exact, partial):
    if cand:
        c = center(sorted(cand)[0][1])
        if c:
            subprocess.run(['adb', 'shell', 'input', 'tap', str(c[0]), str(c[1])])
            print(f"TAPPED '{want}' at {c}")
            sys.exit(0)
print(f"NOTFOUND '{want}'")
sys.exit(1)
PY
}

ui_state() {
  local dump="$1"
  if grep -q "Welcome to Chaty" <<< "$dump"; then echo "WELCOME"; return; fi
  if grep -q "Welcome back" <<< "$dump"; then echo "LOGIN"; return; fi
  if grep -q "Chaty changelog" <<< "$dump"; then echo "HOME_CHANGELOG"; return; fi
  if grep -q "Confirm your email" <<< "$dump"; then echo "HOME_BANNER"; return; fi
  if grep -q "Tasks" <<< "$dump" && grep -q "Settings" <<< "$dump"; then echo "HOME"; return; fi
  echo "OTHER"
}

# Each iteration is a deliberate fresh local login of the same user-authorized demo account.
# Never clear data inside the observation window: a reset is not a bounce.
: > "$OUT/summary.txt"
adb install -r "$APK" > "$OUT/install.log" 2>&1
cat "$OUT/install.log" >> "$TIMELINE"
grep -q Success "$OUT/install.log" || { log "APK install failed"; exit 1; }
for RUN in 1 2 3; do
OUT="prod-auth-evidence/run-$RUN"
mkdir -p "$OUT"
TIMELINE="$OUT/timeline.log"
: > "$TIMELINE"
log "=== deliberate fixture-only local reset before login $RUN"
adb shell am force-stop "$PKG"
adb shell pm clear "$PKG" >> "$TIMELINE"
adb logcat -c
log "=== installed APK $APK"


log "=== start screen recording"
adb shell 'screenrecord --time-limit 150 /sdcard/prod-auth-repro.mp4' >/dev/null 2>&1 &

log "=== launch app"
adb shell input keyevent 82
adb shell am start -W -n "$PKG/$ACTIVITY" 2>&1 | tail -3 | tee -a "$TIMELINE"

state=""
for i in $(seq 1 20); do
  d=$(ui_dump); state=$(ui_state "$d")
  [ "$state" = "WELCOME" ] && break
  sleep 2
done
log "initial state: $state"
shot 01-welcome

log "=== tap Log In (Welcome)"
tap_text "Log In" | tee -a "$TIMELINE"
for i in $(seq 1 10); do
  d=$(ui_dump); state=$(ui_state "$d")
  [ "$state" = "LOGIN" ] && break
  sleep 1
done
log "after tap: $state"
shot 02-login-screen

log "=== select Normal capsule explicitly"
tap_text "Normal" | tee -a "$TIMELINE"
sleep 1

log "=== type identifier"
tap_text "Email or username" | tee -a "$TIMELINE"
sleep 1
adb shell input text "$USER_NAME"
adb shell input keyevent 4
sleep 1

log "=== type password"
tap_text "Password" | tee -a "$TIMELINE"
sleep 1
adb shell input text "$PASS"
sleep 1

adb shell input keyevent 4
sleep 1

log "=== tap Log In (submit)"
tap_text "Log In" | tee -a "$TIMELINE"

log "=== observe 50s"
BASE_PID="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
REACHED_HOME=0
BOUNCED=0
DIED=0
LAST=""
for i in $(seq 1 25); do
  PID=$(adb shell pidof "$PKG" | tr -d '\r' || true)
  d=$(ui_dump)
  state=$(ui_state "$d")
  [ -z "$BASE_PID" ] && BASE_PID="$PID"
  echo "observe t=$((i*2))s pid=${PID:-none} state=$state" >> "$TIMELINE"
  if [ "$state" != "$LAST" ]; then
    shot "evolve-$(printf '%02d' "$i")-$state"
    LAST="$state"
  fi
  case "$state" in HOME|HOME_BANNER|HOME_CHANGELOG) REACHED_HOME=1;; esac
  if [ "$REACHED_HOME" = "1" ] && [ -z "$PID" ]; then
    DIED=1; log "PROCESS DEATH DETECTED after Home"; break
  fi
  if [ "$REACHED_HOME" = "1" ] && [ -n "$BASE_PID" ] && [ -n "$PID" ] && [ "$PID" != "$BASE_PID" ]; then
    DIED=1; log "PID CHANGED after Home: $BASE_PID -> $PID"; BASE_PID="$PID"
  fi
  if [ "$REACHED_HOME" = "1" ] && [ "$state" = "WELCOME" ]; then
    BOUNCED=1; log "BOUNCE DETECTED: Home -> Welcome in-place"; break
  fi
  sleep 2
done

if [ "$DIED" = "1" ]; then
  log "=== relaunch after process death (does the session restore?)"
  sleep 2
  adb shell am start -W -n "$PKG/$ACTIVITY" 2>&1 | tail -2 | tee -a "$TIMELINE"
  state=""
  for i in $(seq 1 12); do
    d=$(ui_dump); state=$(ui_state "$d")
    echo "relaunch t=$((i*2))s pid=$(adb shell pidof "$PKG" | tr -d '\r' || true) state=$state" >> "$TIMELINE"
    case "$state" in WELCOME|HOME|HOME_CHANGELOG|HOME_BANNER) break;; esac
    sleep 2
  done
  shot 04-relaunch
  [ "$state" = "WELCOME" ] && BOUNCED=1 && log "RELAUNCH LANDED ON WELCOME: session did not survive process death"
  case "$state" in HOME|HOME_CHANGELOG|HOME_BANNER) log "RELAUNCH LANDED ON HOME: session survived (crash-only)";; esac
fi

if [ "$REACHED_HOME" = "1" ] && [ "$BOUNCED" = "0" ] && [ "$DIED" = "0" ]; then
  log "=== secondary: close changelog if present, watch 15s more"
  tap_text "Close" | tee -a "$TIMELINE" || true
  for i in $(seq 1 7); do
    PID=$(adb shell pidof "$PKG" | tr -d '\r' || true)
    d=$(ui_dump); state=$(ui_state "$d")
    echo "post-close t=$((i*2))s pid=${PID:-none} state=$state" >> "$TIMELINE"
    [ -z "$PID" ] && { DIED=1; log "PROCESS DEATH after changelog close"; break; }
    [ "$state" = "WELCOME" ] && { BOUNCED=1; log "BOUNCE after changelog close"; break; }
    sleep 2
  done
  shot 05-final
fi

{
  echo "REACHED_HOME=$REACHED_HOME"
  echo "BOUNCE_REPRODUCED=$BOUNCED"
  echo "PROCESS_DIED=$DIED"
} | tee "$OUT/verdict.txt" | tee -a "$TIMELINE"
{ echo "RUN=$RUN"; cat "$OUT/verdict.txt"; } >> prod-auth-evidence/summary.txt
if [ "$REACHED_HOME" != "1" ]; then
  log "INCONCLUSIVE: login never reached Home; not a passing bounce test"
  echo "INCONCLUSIVE_LOGIN=$RUN" >> prod-auth-evidence/summary.txt
fi

log "=== stop recording + collect evidence"
adb shell pkill -2 screenrecord >/dev/null 2>&1 || true
sleep 3
adb pull /sdcard/prod-auth-repro.mp4 "$OUT/prod-auth-repro.mp4" 2>&1 | tail -1 | tee -a "$TIMELINE" || true
adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
log "=== login $RUN complete"
done
# Preserve all evidence even for a reproduced bounce; fail only incomplete coverage.
if grep -q INCONCLUSIVE_LOGIN prod-auth-evidence/summary.txt; then exit 1; fi
log "=== all three login observations complete"
exit 0
