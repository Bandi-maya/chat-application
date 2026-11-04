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
    print('CAPTURE '+name,flush=True)
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

def find_label(label):
    """Presence-only probe (no tap): True/False, None on dump flake (never
    evidence of absence)."""
    root = None
    for _ in range(5):
        adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
        xml = adb('shell','cat','/sdcard/window.xml',check=False)
        if xml.strip().startswith(b'<'):
            try:
                root = ET.fromstring(xml); break
            except ET.ParseError:
                pass
        time.sleep(1)
    if root is None:
        return None
    for node in root.iter('node'):
        if node.get('text') == label or node.get('content-desc') == label:
            return True
    return False

def dismiss_known_modals():
    """Only close the diagnosed emulator launcher ANR, never an app ANR."""
    adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
    xml=adb('shell','cat','/sdcard/window.xml',check=False)
    try: root=ET.fromstring(xml)
    except ET.ParseError: return False
    texts=[n.get('text','') for n in root.iter('node')]
    anr=[t for t in texts if "isn't responding" in t or 'is not responding' in t]
    if anr:
        if anr != ["Pixel Launcher isn't responding"]:
            raise RuntimeError('APP_ANR_NOT_DISMISSED:'+repr(anr))
        print('RECOVER verified Pixel Launcher ANR',flush=True)
        if not tap_label('Close app',optional=True,settle=2):
            raise RuntimeError('LAUNCHER_ANR_CLOSE_UNAVAILABLE')
        return True
    for label in ('Close','Not now','Not Now'):
        if label in texts and tap_label(label,optional=True,settle=1):
            print('DISMISS known modal '+label,flush=True)
            return True
    return False

def dismiss_until_ready(identity='Chats', attempts=8):
    for _ in range(attempts):
        if dismiss_known_modals(): continue
        if find_label(identity) is True:
            print('READY '+identity,flush=True)
            return True
        # Drawer shells have no visible Chats label until navigation opens.
        # Verify the actual drawer affordance, open it, require Chats, then
        # close through that real destination. Never accept a blind fallback.
        if identity == 'Chats':
            for drawer_label in ('Open navigation drawer', 'Open navigation menu'):
                if find_label(drawer_label) is True and tap_label(drawer_label,optional=True,settle=1):
                    if find_label('Chats') is True and tap_label('Chats',optional=True,settle=1):
                        if not dismiss_known_modals():
                            print('READY Chats via '+drawer_label,flush=True)
                            return True
        time.sleep(2)
    return False

def require_ready(slug, identity='Chats'):
    print('READINESS '+slug,flush=True)
    if not dismiss_until_ready(identity):
        capture(slug+'-READINESS-FAILED',settle=0)
        raise RuntimeError('READINESS_NOT_VERIFIED:'+slug+':'+identity)


def record_layout_walkthrough(slug):
    """Actual emulator walkthrough, isolated signed-out presentation only.
    Record enabled root destinations plus supported home popup. No sends.
    This is not an all-app or authenticated-persistence proof."""
    remote = '/sdcard/' + slug + '-walkthrough.mp4'
    notes=[]
    rec=subprocess.Popen(['adb','shell','screenrecord','--bit-rate','1800000','--time-limit','75',remote],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    try:
        time.sleep(2)
        for label in ('Calls','Tasks','Settings','Chats'):
            reached=tap_label(label,optional=True,settle=3)
            notes.append(label+':'+str(reached))
            capture(slug+'-walkthrough-'+label.lower(),settle=2)
        if tap_label('More options',optional=True,settle=2):
            capture(slug+'-walkthrough-menu',settle=2)
            adb('shell','input','keyevent','KEYCODE_BACK')
        time.sleep(2)
    finally:
        adb('shell','pkill','-2','screenrecord',check=False)
        try: rec.wait(timeout=10)
        except subprocess.TimeoutExpired: rec.terminate()
        time.sleep(1)
        adb('pull',remote,str(out/(slug+'-walkthrough.mp4')),check=False)
        (out/(slug+'-walkthrough-notes.txt')).write_text('SCOPE:isolated-signed-out-presentation-not-all-app\n'+'\n'.join(notes))

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
    passes = [('One UI', 'dark'), ('Chaty Default', 'dark'), ('WhatsApp OLD UI', 'dark'), ('IOS STYLE', 'dark'),
              ('Curved Radial Menu', 'dark'), ('Chaty Default', 'light')]
    for style, brightness in passes:
        adb('shell','am','force-stop',pkg)
        write_prefs('chaty_api_backend', {'api_hub_token':'synthetic-test-token','api_hub_user':json.dumps({'id':'synthetic-layout-user','username':'fixture','displayName':'Synthetic Layout','avatarInitials':'SL','about':'Isolated capture','email':'','phone':'','avatarUrl':None})})
        # ONE write for the shared file: layout prefs + capture theme seed (writer-grounded
        # chaty_theme_v1 key). A second write_prefs call would replace the file and erase the first.
        theme = {'schemaVersion': 2, 'globalTheme': {
            'id': 'capture_custom', 'name': 'Capture theme', 'brightness': brightness,
            'accentColor': 4288423856, 'backgroundColor': 4279374354, 'surfaceColor': 4280163883,
            'cardColor': 4280624421, 'primaryTextColor': 4294967295, 'secondaryTextColor': 4289374890,
            'navigationMode': 'bottomNav', 'layoutMode': 'classic',
            'fontScale': 1.0, 'density': 1.0, 'animationLevel': 1.0},
            'perChatThemes': {}, 'useReducedMotion': False, 'followSystem': False}
        # SIGNED-OUT PRESENTATION PROOF: the fixture's fake session is REST-plane only
        # (api_hub_user); ChatyPreferencesController auth is SessionAuthSource(sessionManager)
        # (Supabase plane), so the fixture is correctly signed-out for prefs and the shell
        # reads the BARE bucket. Seed bare for isolated presentation proof - this evidences
        # signed-out presentation persistence, NOT authenticated account persistence.
        write_prefs('chaty_local_preferences', {
            'chaty_preferences_v1': json.dumps({'home': {'homeStyle': style}}),
            'chaty_preferences_v1.synthetic-layout-user': json.dumps({'home': {'homeStyle': style}}),
            'chaty_theme_v1': json.dumps(theme)})
        slug=style.lower().replace(' ','-') + ('' if brightness == 'dark' else '-light')
        # Preserve input before a potentially destructive bootstrap write.
        (out/f'{slug}-seed.txt').write_text(style)
        adb('shell','am','start','-n',pkg+'/com.example.chat.MainActivity')
        time.sleep(10)
        # First-launch dialogs: version changelog (Close) sits above the
        # notification-permission prompt (Not Now); dismiss both, changelog
        # first since it masks the home window entirely (baseline 41626081675
        # died here: AssertionError UI label not found: Calls).
        # Readiness before proof: bounded modal dismissal (ANR 'Wait', never
        # 'Close app'; changelog 'Close'; notification 'Not now') AND verified
        # screen identity. Failure is recorded, never relabeled a pass.
        require_ready(slug)
        ready = True
        capture(f'{slug}-01-chats')
        if style == 'One UI':
            # Dock scroll evidence only: geometry follows the actual viewport.
            size=re.search(r'(\d+)x(\d+)',adb('shell','wm','size').decode())
            w,h=map(int,size.groups())
            for side,x1,x2 in [('left',.08,.40),('right',.60,.92)]:
                for direction,a,b in [('forward',x2,x1),('back',x1,x2)]:
                    adb('shell','input','swipe',str(int(w*a)),str(int(h*.94)),str(int(w*b)),str(int(h*.94)),'500')
                    capture(f'{slug}-dock-{side}-{direction}',settle=1)

        if ready and style in ('Chaty Default', 'WhatsApp OLD UI') and brightness == 'dark':
            record_layout_walkthrough(slug)
        # Probe the style's actually-present destinations; a label absent in this
        # style's dock/drawer is recorded, never a hard failure (no fixed inventory).
        # Drawer/menu styles keep destinations behind an affordance: open the actual
        # menu once before probing, so a closed drawer cannot mask a real destination.
        menu_note = 'MENU_NONE'
        for mlabel in ('Open navigation drawer', 'Open navigation menu'):
            if tap_label(mlabel, optional=True, settle=1):
                menu_note = 'MENU_OPENED:' + mlabel
                break
        present, hidden = [], []
        dump_dead = False
        for i,label in enumerate(('Calls','Chats','Tasks','Settings','Communities','Updates','Groups'),2):
            r = tap_label(label, optional=True, settle=0)
            if r is None:
                # UI_DUMP_UNAVAILABLE: dump flake, NOT a missing destination.
                hidden.append('UI_DUMP_UNAVAILABLE:' + label)
                dump_dead = True
                continue
            if not r:
                # Drawer closes on every select: reopen the actual menu and retry once
                # before recording anything, so a closed drawer is never misread.
                reopened = False
                for mlabel in ('Open navigation drawer', 'Open navigation menu'):
                    if tap_label(mlabel, optional=True, settle=1):
                        reopened = True
                        break
                r2 = tap_label(label, optional=True, settle=0) if reopened else False
                if r2 is None:
                    hidden.append('UI_DUMP_UNAVAILABLE:' + label)
                    continue
                if reopened and r2:
                    pass  # fell through to a real destination below
                else:
                    # UNREACHABLE: no menu affordance found, label may sit behind it.
                    # HIDDEN: menu opened (or dock visible) and the label still was not found.
                    hidden.append(('UNREACHABLE:' if not reopened else 'HIDDEN:') + label)
                    continue
            present.append(label)
            capture(f'{slug}-{i:02}-{label.lower()}-transition', settle=0.4)
            capture(f'{slug}-{i:02}-{label.lower()}')
            # Job bound: the light-variant pass probes collapse on the first present
            # destination only; dark passes probe every present destination.
            if style != 'One UI' and (brightness == 'dark' or not present[:-1]):
                swipe_probe(slug, f'{i:02}-{label.lower()}')
        (out/f'{slug}-destinations.txt').write_text('SCOPE:signed-out-bare-bucket-presentation-proof-not-authenticated-account-persistence\n' + menu_note + '\nPRESENT:' + ','.join(present) + '\n' + '\n'.join(hidden))
        adb('shell','input','keyevent','KEYCODE_BACK')
        tap_label('Chats', optional=True)
        adb('shell','am','force-stop',pkg)
        adb('shell','am','start','-n',pkg+'/com.example.chat.MainActivity')
        time.sleep(8)
        require_ready(slug+'-reopen')
        capture(f'{slug}-07-reopened')

    # BOUNDED MENU PIXELS - fixture presentation proof only (signed-out bare-bucket
    # fake session, fixture data; NOT production account state or authenticated
    # persistence). Every step optional and recorded, never fatal to the style passes.
    menu_notes = []
    def long_press_label(label, ms=900):
        root = None
        for _ in range(5):
            adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
            xml = adb('shell','cat','/sdcard/window.xml',check=False)
            if xml.strip().startswith(b'<'):
                try:
                    root = ET.fromstring(xml); break
                except ET.ParseError:
                    pass
            time.sleep(1)
        if root is None:
            return None
        for node in root.iter('node'):
            if node.get('text') == label or node.get('content-desc') == label:
                b = list(map(int, re.findall(r'\d+', node.get('bounds',''))))
                if len(b) == 4:
                    x, y = (b[0]+b[2])//2, (b[1]+b[3])//2
                    adb('shell','input','swipe',str(x),str(y),str(x),str(y),str(ms))
                    return True
        return False
    try:
        if tap_label('More options', optional=True, settle=1):
            capture('menu-01-home-overflow')
            adb('shell','input','keyevent','KEYCODE_BACK')
        else:
            menu_notes.append('HOME_OVERFLOW_NOT_FOUND_OR_DUMP_FLAKE')
    except Exception as e:
        menu_notes.append('HOME_OVERFLOW_ERROR:' + type(e).__name__)
    try:
        adb('shell','am','start','-n',pkg+'/com.example.chat.MenuAlbumProofActivity','--es','screen','menu',check=False)
        time.sleep(3)
        # This fixture intentionally opens its overflow at launch; modal semantics
        # hide the underlying chat title from UIAutomator until dismissed.
        require_ready('debug-menu','View contact info')
        capture('menu-02-chat-header-open',settle=2)
        adb('shell','input','keyevent','KEYCODE_BACK')
        require_ready('debug-menu-closed','Alice Rao')
        time.sleep(3)
        reach = tap_label('See you at 6!', optional=True, settle=0)
        if reach is not True:
            menu_notes.append('FIXTURE_DETAIL_NOT_REACHED')
        else:
            lp = long_press_label('See you at 6!')
            if lp is not True:
                menu_notes.append('BUBBLE_LONGPRESS_FAILED')
            else:
                time.sleep(1)
                capture('menu-02-bubble-selection')
                if tap_label('More options', optional=True, settle=1):
                    capture('menu-03-selection-sheet')
                    adb('shell','input','keyevent','KEYCODE_BACK')
                else:
                    menu_notes.append('SELECTION_OVERFLOW_ABSENT')
    except Exception as e:
        menu_notes.append('BUBBLE_MENU_ERROR:' + type(e).__name__)
        raise
    # Separate >4 album fixture proof; no sends or backend.
    adb('shell','am','start','-n',pkg+'/com.example.chat.MenuAlbumProofActivity','--es','screen','album',check=False)
    time.sleep(3)
    require_ready('debug-album','Alice Rao')
    capture('menu-04-six-image-album',settle=2)
    for proof_screen in ('contactinfo','allmedia','profile'):
        adb('shell','am','start','-n',pkg+'/com.example.chat.MenuAlbumProofActivity','--es','screen',proof_screen,check=False)
        time.sleep(3)
        require_ready('debug-'+proof_screen, 'All media' if proof_screen == 'allmedia' else 'Alice Rao')
        capture('proof-'+proof_screen+'-top',settle=2)
        x,y1,y2=viewport()
        adb('shell','input','swipe',str(x),str(y1),str(x),str(y2),'350')
        capture('proof-'+proof_screen+'-scrolled',settle=2)
    (out/'menu-notes.txt').write_text('SCOPE:fixture-presentation-proof-not-production-account-state\n' + ('\n'.join(menu_notes) if menu_notes else 'MENU_CAPTURE_OK'))

finally:
    capture('final-state')
    (out/'logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime',check=False))
