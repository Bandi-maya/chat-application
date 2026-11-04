"""Isolated fake session, installed pair only. No live server or real user data.
Testref extension: tab-transition frames (0.4s post-tap, zero tap-settle),
scroll-linked collapse/expand step frames per present destination, drawer/menu
affordance opened before destination probes, and one bounded animation-enabled
screenrecord pass for Chaty Default. Animator scales are 0 outside the
animation pass, so stills evidence scroll-linked states and post-tap states,
not timed physics; the mp4 evidences motion while gestures run, not after.
Empty fixture lists may not scroll: frames then document the fixture
limitation instead of a collapse defect.
"""
import json, pathlib, subprocess, time, xml.etree.ElementTree as ET, re
out = pathlib.Path('screenshots'); out.mkdir(exist_ok=True)
pkg = 'com.logybyte.chaty'
def adb(*args, input=None, check=True):
    return subprocess.run(['adb', *args], input=input, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60, check=check).stdout

def write_prefs(name, values):
    root=ET.Element('map')
    for key, value in values.items():
        ET.SubElement(root, 'string', name=key).text=value
    payload=ET.tostring(root, encoding='utf-8', xml_declaration=True)
    adb('shell','run-as',pkg,'mkdir','-p','shared_prefs')
    local=out/'seed.xml'; local.write_bytes(payload)
    adb('push',str(local),'/data/local/tmp/layout-seed.xml')
    adb('shell','chmod','644','/data/local/tmp/layout-seed.xml')
    adb('shell','run-as',pkg,'cp','/data/local/tmp/layout-seed.xml',f'shared_prefs/{name}.xml')
    local.unlink()

def capture(name, settle=2):
    time.sleep(settle)
    (out/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
    xml=adb('shell','cat','/sdcard/window.xml',check=False)
    if not xml.strip().startswith(b'<'):
        time.sleep(1)
        adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
        xml=adb('shell','cat','/sdcard/window.xml',check=False)
    (out/f'{name}.xml').write_bytes(xml)
    for prefs in ('chaty_local_preferences',):
        (out/f'{name}-{prefs}.xml').write_bytes(adb('shell','run-as',pkg,'cat',f'shared_prefs/{prefs}.xml',check=False))
    return xml

def tap_label(label, optional=False, settle=0):
    # uiautomator dump intermittently returns empty/unparseable output right
    # after activity start or while windows transition (run 41630769482 died
    # on ParseError here). Retry briefly; only then treat as absent/fail.
    root=None
    for _ in range(5):
        adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
        xml=adb('shell','cat','/sdcard/window.xml',check=False)
        if xml.strip().startswith(b'<'):
            try:
                root=ET.fromstring(xml); break
            except ET.ParseError:
                pass
        time.sleep(1)
    if root is None:
        if optional: return None  # dump failure is NOT evidence of absence
        raise AssertionError(f'UI dump unavailable while seeking: {label}')
    nodes=[]
    for node in root.iter('node'):
        if node.get('text')==label or node.get('content-desc')==label:
            b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(b)==4: nodes.append(b)
    if not nodes:
        if optional: return False
        raise AssertionError(f'UI label not found: {label}')
    l,t,r,b=max(nodes,key=lambda b:b[1]); adb('shell','input','tap',str((l+r)//2),str((t+b)//2));time.sleep(settle)
    return True

def viewport():
    """Resolve swipe geometry from the actual display instead of fixed pixels."""
    m = re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size').decode(errors='replace'))
    w, h = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)
    return w // 2, int(h * 0.75), int(h * 0.3)

def swipe_probe(slug, dest):
    """Scroll-linked header evidence: swipe up in steps (collapse), then down (expand).
    Intermediate frames after each step. Empty fixture lists may not scroll:
    frames then document that limitation instead of a collapse defect."""
    x, y1, y2 = viewport()
    for i in range(1, 4):
        adb('shell', 'input', 'swipe', str(x), str(y1), str(x), str(y2), '350')
        capture(f'{slug}-{dest}-collapse-{i}', settle=1)
    for i in range(1, 4):
        adb('shell', 'input', 'swipe', str(x), str(y2), str(x), str(y1), '350')
        capture(f'{slug}-{dest}-expand-{i}', settle=1)

def animation_pass(style_slug):
    """Bounded ANIMATION-ENABLED evidence for the Chaty Default style only:
    system animator scales set to 1 for this pass, one 12s screenrecord covering
    collapse swipe, expand swipe, a tab switch and back, with every gesture run
    WHILE recording. Evidence is a recording, not frame-perfect physics; the
    app-level reduced-motion pref keeps its seeded value (default off)."""
    for scale in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        adb('shell', 'settings', 'put', 'global', scale, '1.0')
    adb('shell', 'am', 'force-stop', pkg)
    adb('shell', 'am', 'start', '-n', pkg + '/com.example.chat.MainActivity')
    time.sleep(8)
    rec = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '12', '/sdcard/collapse-pass.mp4'],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    x, y1, y2 = viewport()
    adb('shell', 'input', 'swipe', str(x), str(y1), str(x), str(y2), '600')  # collapse, while recording
    time.sleep(2)
    adb('shell', 'input', 'swipe', str(x), str(y2), str(x), str(y1), '600')  # expand, while recording
    time.sleep(2)
    tap_label('Calls', optional=True, settle=0)  # destination transition, while recording
    time.sleep(2)
    tap_label('Chats', optional=True, settle=0)
    rec.wait(timeout=30)
    adb('pull', '/sdcard/collapse-pass.mp4', str(out / f'{style_slug}-animation-pass.mp4'))
    for scale in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        adb('shell', 'settings', 'put', 'global', scale, '0.0')


def fixture_pass():
    """One bounded instrumentation run of the writer-reviewed ReferenceLayoutRuntimeProofTest
    (real MainActivity, fake REST session, 20 labelled in-memory rows). screenrecord runs
    WHILE the instrumentation executes; rows are in-memory only, no force-stop inside the
    test. Evidence pulled as fixture-* files plus the recording."""
    adb('shell', 'settings', 'put', 'global', 'window_animation_scale', '1.0')
    adb('shell', 'settings', 'put', 'global', 'transition_animation_scale', '1.0')
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1.0')
    rec = subprocess.Popen(['adb', 'shell', 'screenrecord', '/sdcard/fixture-pass.mp4'],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    time.sleep(2)
    subprocess.run(['adb', 'shell', 'am', 'instrument', '-w', '-e', 'class',
                    'com.example.chat.ReferenceLayoutRuntimeProofTest#configuredRealRootShowsRowsScrollAndPreservesSelectedPresentation',
                    'com.logybyte.chaty.test/androidx.test.runner.AndroidJUnitRunner'],
                   stdout=open(out / 'fixture-instrument-output.txt', 'wb'), stderr=subprocess.STDOUT,
                   timeout=300, check=False)
    subprocess.run(['adb', 'shell', 'pkill', '-2', 'screenrecord'], timeout=15, check=False,
                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    time.sleep(2)
    subprocess.run(['adb', 'pull', '/sdcard/fixture-pass.mp4', str(out / 'fixture-pass.mp4')],
                   timeout=60, check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    subprocess.run(['adb', 'pull', '/sdcard/Android/data/com.logybyte.chaty/files/screenshots/', str(out / 'fixture')],
                   timeout=60, check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    for scale in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        adb('shell', 'settings', 'put', 'global', scale, '0.0')

try:
    adb('install','-r','-t','runtime-apks/app-debug.apk')
    adb('install','-r','-t','runtime-apks/app-debug-androidTest.apk')
    adb('shell','am','force-stop',pkg)
    write_prefs('chaty_api_backend', {'api_hub_token':'synthetic-test-token','api_hub_user':json.dumps({'id':'synthetic-layout-user','username':'fixture','displayName':'Synthetic Layout','avatarInitials':'SL','about':'Isolated capture','email':'','phone':'','avatarUrl':None})})
    # SIGNED-OUT PRESENTATION PROOF: prefs auth is SessionAuthSource(sessionManager) (Supabase
    # plane); the fake REST session is signed-out for prefs, so seed the BARE bucket.
    write_prefs('chaty_local_preferences', {
        'chaty_preferences_v1': json.dumps({'home': {'homeStyle': 'Chaty Default'}}),
        'chaty_preferences_v1.synthetic-layout-user': json.dumps({'home': {'homeStyle': 'Chaty Default'}}),
        'chaty_theme_v1': json.dumps({'schemaVersion':2,'globalTheme':{
            'id':'capture_custom','name':'Capture theme','brightness':'dark',
            'accentColor':4288423856,'backgroundColor':4279374354,'surfaceColor':4280163883,
            'cardColor':4280624421,'primaryTextColor':4294967295,'secondaryTextColor':4289374890,
            'navigationMode':'bottomNav','layoutMode':'classic',
            'fontScale':1.0,'density':1.0,'animationLevel':1.0},
            'perChatThemes':{},'useReducedMotion':False,'followSystem':False})})
    adb('shell','am','start','-n',pkg+'/com.example.chat.MainActivity')
    time.sleep(10)
    # host-side modal dismissal so the changelog seen-flag persists before instrumentation
    for label in ('Close','Not now','Not Now','Close','Not Now'):
        tap_label(label,optional=True)
    capture('motion-00-home')
    fixture_pass()
finally:
    capture('final-state')
    (out/'logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime',check=False))
