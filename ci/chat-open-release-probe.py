#!/usr/bin/env python3
"""CI-only release UI probe. Synthetic offline demo only; no real credentials.

Tests existing demo history and a cleared-empty demo room, three opens each.
A cleared room is NOT a brand-new-contact test. No app source is modified.
"""
import json
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = 'com.logybyte.chaty'
OUT = pathlib.Path('chat-open-evidence')
OUT.mkdir(exist_ok=True)
RESULT = {'scope': 'x86_64 release; offline demo; API 35',
          'new_contact_route': 'NOT_TESTED: needs authorized live test account/recipient',
          'existing': [], 'cleared_empty': [], 'status': 'RUNNING'}


def adb(*args, check=True, timeout=30):
    return subprocess.run(['adb', *args], capture_output=True, timeout=timeout, check=check).stdout


def note(text):
    with (OUT / 'timeline.txt').open('a') as f:
        f.write(f'{time.monotonic():.3f} {text}\n')
    print(text, flush=True)


def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/chat-open.xml', timeout=20)
    raw = adb('shell', 'cat', '/sdcard/chat-open.xml')
    (OUT / 'current.xml').write_bytes(raw)
    return ET.fromstring(raw)


def labels(root):
    return [n.get(k, '') for n in root.iter('node') for k in ('text', 'content-desc')]



def node_matches(node, label):
    if label in (node.get('text'), node.get('content-desc')):
        return True
    # ConversationRow exposes one merged accessibility node. Only accept the
    # exact configured title followed by its summary delimiter, never substring.
    return (label == 'Aisha (sample)' and node.get('package') == PKG
            and node.get('clickable') == 'true'
            and node.get('content-desc', '').startswith(label + ', '))


def contains_label(root, label):
    return any(node_matches(n, label) for n in root.iter('node'))


def wait(label, timeout=30):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if not adb('shell', 'pidof', PKG, check=False).strip():
            raise RuntimeError(f'APP_PROCESS_DIED awaiting {label}')
        root = ui()
        if contains_label(root, label):
            return root
        time.sleep(1)
    raise RuntimeError(f'UI_NOT_REACHED: {label}')


def tap(label, timeout=30):
    root = wait(label, timeout)
    nodes = [n for n in root.iter('node') if node_matches(n, label)]
    nodes.sort(key=lambda n: n.get('clickable') != 'true')
    for n in nodes:
        b = list(map(int, re.findall(r'\d+', n.get('bounds', ''))))
        if len(b) == 4 and b[2] > b[0] and b[3] > b[1]:
            adb('shell', 'input', 'tap', str((b[0]+b[2])//2), str((b[1]+b[3])//2))
            note('tap ' + label)
            return
    raise RuntimeError('No tappable bounds: ' + label)



def reveal_demo_entry(max_swipes=5):
    """Reveal the current login link, without unbounded scrolling or fallback taps."""
    wait('Welcome back', 60)
    for attempt in range(max_swipes + 1):
        root = ui()
        if 'Try demo' in labels(root):
            return
        if attempt == max_swipes:
            break
        scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true'
                   and n.get('package') == PKG]
        if not scrolls:
            raise RuntimeError('DEMO_ENTRY_NOT_VISIBLE: no app scroll container')
        bounds = list(map(int, re.findall(r'\d+', scrolls[0].get('bounds', ''))))
        if len(bounds) != 4 or bounds[3] <= bounds[1] or bounds[2] <= bounds[0]:
            raise RuntimeError('DEMO_ENTRY_NOT_VISIBLE: invalid scroll bounds')
        x1, y1, x2, y2 = bounds
        x = (x1 + x2) // 2
        height = y2 - y1
        adb('shell', 'input', 'swipe', str(x), str(y1 + height * 4 // 5),
            str(x), str(y1 + height // 3), '400')
        note(f'demo-entry scroll {attempt + 1}/{max_swipes}')
        time.sleep(1)
    raise RuntimeError('DEMO_ENTRY_NOT_VISIBLE: Try demo after bounded scroll')


def shot(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    try:
        root = ui()
        (OUT / (name + '.xml')).write_bytes(ET.tostring(root))
    except Exception as e:
        note('UI capture failed: ' + str(e))
    (OUT / (name + '-activities.txt')).write_bytes(adb('shell', 'dumpsys', 'activity', 'activities', check=False))



def onboarding_action(found):
    """Only known, source-identified first-login overlays may be dismissed."""
    if 'Chaty changelog' in found and 'Close' in found:
        return 'Close'
    if 'Stay Connected with Chaty' in found and 'Not Now' in found:
        return 'Not Now'
    # Compatibility with older first-login builds. Current AppRoot explicitly
    # does not solicit lock setup; never dismiss an actual locked-app gate.
    if 'Enable App Lock?' in found and 'Not now' in found:
        return 'Not now'
    return None


def dismiss_demo_onboarding():
    end = time.monotonic() + 60
    dismissals = 0
    stable_home = 0
    while time.monotonic() < end:
        if not adb('shell', 'pidof', PKG, check=False).strip():
            raise RuntimeError('APP_PROCESS_DIED during onboarding')
        root = ui()
        found = labels(root)
        action = onboarding_action(found)
        if action:
            stable_home = 0
            dismissals += 1
            if dismissals > 8:
                raise RuntimeError('ONBOARDING_OVERLAY_LOOP: more than eight identified prompts')
            shot(f'onboarding-{dismissals}')
            tap(action, 5)
        elif contains_label(root, 'Aisha (sample)'):
            stable_home += 1
            if stable_home >= 2:
                return
        else:
            stable_home = 0
        time.sleep(1)
    raise RuntimeError('ONBOARDING_NOT_FINISHED: expected demo home without known overlays')


def assert_chat(expected, name):
    wait('Chat options')
    wait(expected)
    pid = adb('shell', 'pidof', PKG).strip()
    time.sleep(3)
    if adb('shell', 'pidof', PKG, check=False).strip() != pid:
        raise RuntimeError('APP_PROCESS_CHANGED while chat visible')
    root = ui()
    if 'Chat options' not in labels(root) or expected not in labels(root):
        raise RuntimeError('Chat disappeared during idle observation')
    shot(name)
    note(name + ' PASS pid=' + pid.decode())


def run(apk):
    adb('install', '-r', apk, timeout=120)
    # This runner is disposable and contains no user's app/account data.
    adb('shell', 'pm', 'clear', PKG)
    adb('logcat', '-c')
    log = (OUT / 'logcat-full.txt').open('wb')
    logger = subprocess.Popen(['adb', 'logcat', '-v', 'threadtime'], stdout=log, stderr=subprocess.STDOUT)
    recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '180', '/sdcard/chat-open.mp4'], stdout=(OUT / 'screenrecord-output.txt').open('wb'), stderr=subprocess.STDOUT)
    try:
        adb('shell', 'am', 'start', '-W', '-n', PKG + '/com.example.chat.MainActivity')
        tap('Log In', 60)
        reveal_demo_entry()
        tap('Try demo', 15)
        dismiss_demo_onboarding()
        shot('demo-home')
        for i in range(1, 4):
            tap('Aisha (sample)')
            assert_chat('Exactly. Everything here is offline demo data.', f'existing-{i}')
            RESULT['existing'].append('PASS')
            tap('Back')
            wait('Aisha (sample)')
        # Only synthetic sample messages are removed, via the actual app UI.
        tap('Aisha (sample)')
        wait('Chat options')
        tap('Chat options')
        tap('More')
        tap('Clear conversation')
        tap('Clear')
        wait('No messages yet')
        shot('demo-cleared')
        tap('Back')
        for i in range(1, 4):
            tap('Aisha (sample)')
            assert_chat('No messages yet', f'cleared-empty-{i}')
            RESULT['cleared_empty'].append('PASS')
            tap('Back')
            wait('Aisha (sample)')
        RESULT['status'] = 'PASS_LIMITED_SCOPE'
    except Exception as e:
        RESULT['status'] = 'FAIL'
        RESULT['failure'] = str(e)
        note('FAIL ' + str(e))
        try:
            shot('failure')
        except Exception as capture_error:
            note('Failure screenshot unavailable: ' + str(capture_error))
    finally:
        adb('shell', 'pkill', '-2', 'screenrecord', check=False)
        try:
            recorder.wait(timeout=15)
        except subprocess.TimeoutExpired:
            recorder.terminate()
            recorder.wait(timeout=5)
        adb('pull', '/sdcard/chat-open.mp4', str(OUT / 'chat-open.mp4'), check=False)
        (OUT / 'logcat-crash.txt').write_bytes(adb('logcat', '-b', 'crash', '-d', check=False))
        (OUT / 'exit-info.txt').write_bytes(adb('shell', 'dumpsys', 'activity', 'exit-info', PKG, check=False))
        (OUT / 'package.txt').write_bytes(adb('shell', 'dumpsys', 'package', PKG, check=False))
        logger.terminate()
        logger.wait(timeout=10)
        log.close()
        (OUT / 'verdict.json').write_text(json.dumps(RESULT, indent=2) + '\n')
        print(json.dumps(RESULT, indent=2), flush=True)
    return 0 if RESULT['status'] == 'PASS_LIMITED_SCOPE' else 1


if __name__ == '__main__':
    sys.exit(run(sys.argv[1]))
