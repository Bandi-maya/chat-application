"""GB WhatsApp reference inspection: install the supplied authorized APK on an
isolated emulator, launch, and capture every reachable pre-login component.
NO account, phone number, OTP, contacts, or messages: capture and stop at any
login/identity wall. Every step screenshots + dumps the UI hierarchy."""
import pathlib, subprocess, time, re
out = pathlib.Path('screenshots'); out.mkdir(exist_ok=True)
def adb(*args, check=False):
    return subprocess.run(['adb', *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60, check=check).stdout

def capture(name):
    time.sleep(2)
    (out/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','uiautomator','dump','/sdcard/gb.xml')
    (out/f'{name}.xml').write_bytes(adb('shell','cat','/sdcard/gb.xml'))

def labels():
    adb('shell','uiautomator','dump','/sdcard/gb.xml')
    xml = adb('shell','cat','/sdcard/gb.xml').decode('utf-8','ignore')
    return xml

def tap_text(cands):
    xml = labels()
    import xml.etree.ElementTree as ET
    try: root = ET.fromstring(xml)
    except Exception: return None
    for node in root.iter('node'):
        t = (node.get('text') or node.get('content-desc') or '')
        if t.strip().lower() in [c.lower() for c in cands] and node.get('clickable') == 'true' or \
           t.strip().lower() in [c.lower() for c in cands]:
            b = list(map(int, re.findall(r'\d+', node.get('bounds',''))))
            if len(b)==4:
                adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
                return t
    return None

inv = []
# Resolve the installed package (filename says com.gbwhatsapp; some builds use com.gbwhatsapp2)
pkg = None
for cand in ('com.gbwhatsapp2','com.gbwhatsapp'):
    if cand in adb('shell','pm','list','packages').decode('utf-8','ignore'):
        pkg = cand; break
inv.append(f'INSTALLED_PACKAGE={pkg}')
if pkg:
    dump = adb('shell','pm','dump',pkg).decode('utf-8','ignore')
    acts = sorted(set(re.findall(r'(com\.[^\s]+/[\w.]+)', dump)))
    (out/'gb-components.txt').write_text(f'package={pkg}\nexported-paths/components seen in pm dump:\n' + '\n'.join(acts[:200]))
    # Launch via known launcher component, fallback to monkey
    r = adb('shell','am','start','-n',pkg+'/com.gbwhatsapp.Icon0').decode('utf-8','ignore')
    if 'Error' in r:
        adb('shell','monkey','-p',pkg,'-c','android.intent.category.LAUNCHER','1')
    inv.append('launcher: ' + r.strip().split(chr(10))[-1][:120])
    time.sleep(8)
    capture('gb-01-launch')
    # Walk pre-login screens: agree/continue style buttons; never grant contacts/phone, never enter identity
    stops = ('enter your phone','phone number','verify your number','restore backup','google drive backup')
    for i in range(2, 10):
        xml = labels().lower()
        if any(s in xml for s in stops):
            capture(f'gb-{i:02}-login-wall')
            inv.append(f'LOGIN_WALL at step {i}: identity/phone/backup prompt reached - STOPPED (no authority)')
            break
        tapped = tap_text(['agree and continue','agree & continue','continue','next','get started','accept'])
        if not tapped:
            capture(f'gb-{i:02}-end')
            inv.append(f'NO further continue control at step {i} - end of reachable path')
            break
        inv.append(f'step {i}: tapped "{tapped}"')
        capture(f'gb-{i:02}-after-tap')
    # Permissions inventory without granting
    perms = sorted(set(re.findall(r'android\.permission\.[A-Z_]+', adb('shell','pm','dump',pkg).decode('utf-8','ignore'))))
    (out/'gb-permissions.txt').write_text('\n'.join(perms))
(out/'gb-inventory.txt').write_text('\n'.join(inv))
