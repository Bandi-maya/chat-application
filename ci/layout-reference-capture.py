"""Isolated fake session, installed pair only. No live server or real user data.
Captures actual saved selection before/after startup, tab navigation and process death.
No code/fixture APK rebuild. UI failures preserve evidence and fail the job.
"""
import json, pathlib, subprocess, time, xml.etree.ElementTree as ET, re
out = pathlib.Path('screenshots'); out.mkdir(exist_ok=True)
pkg = 'com.logybyte.chat'
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

def capture(name):
    time.sleep(2)
    (out/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
    xml=adb('shell','cat','/sdcard/window.xml',check=False)
    (out/f'{name}.xml').write_bytes(xml)
    for prefs in ('chaty_local_preferences',):
        (out/f'{name}-{prefs}.xml').write_bytes(adb('shell','run-as',pkg,'cat',f'shared_prefs/{prefs}.xml',check=False))
    return xml

def tap_label(label, optional=False):
    adb('shell','uiautomator','dump','/sdcard/window.xml',check=False)
    xml=adb('shell','cat','/sdcard/window.xml',check=False)
    nodes=[]
    for node in ET.fromstring(xml).iter('node'):
        if node.get('text')==label or node.get('content-desc')==label:
            b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(b)==4: nodes.append(b)
    if not nodes:
        if optional: return False
        raise AssertionError(f'UI label not found: {label}')
    l,t,r,b=max(nodes,key=lambda b:b[1]); adb('shell','input','tap',str((l+r)//2),str((t+b)//2));time.sleep(2)
    return True

try:
    adb('install','-r','-t','runtime-apks/app-debug.apk')
    for style in ('Chaty Default','WhatsApp OLD UI','IOS STYLE','Curved Radial Menu'):
        adb('shell','am','force-stop',pkg)
        write_prefs('chaty_api_backend', {'api_hub_token':'synthetic-test-token','api_hub_user':json.dumps({'id':'synthetic-layout-user','username':'fixture','displayName':'Synthetic Layout','avatarInitials':'SL','about':'Isolated capture','email':'','phone':'','avatarUrl':None})})
        write_prefs('chaty_local_preferences', {'chaty_preferences_v1.synthetic-layout-user':json.dumps({'home':{'homeStyle':style}})})
        slug=style.lower().replace(' ','-')
        # Preserve input before a potentially destructive bootstrap write.
        (out/f'{slug}-seed.txt').write_text(style)
        adb('shell','am','start','-n',pkg+'/com.example.chat.MainActivity')
        time.sleep(10)
        for label in ('Not now','Not Now','Not Now','Not Now'):
            tap_label(label,optional=True)
        capture(f'{slug}-01-chats')
        for i,label in enumerate(('Calls','Chats','Tasks','Settings','Chats'),2):
            tap_label(label)
            capture(f'{slug}-{i:02}-{label.lower()}')
        adb('shell','am','force-stop',pkg)
        adb('shell','am','start','-n',pkg+'/com.example.chat.MainActivity')
        time.sleep(8)
        capture(f'{slug}-07-reopened')
finally:
    capture('final-state')
    (out/'logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime',check=False))
