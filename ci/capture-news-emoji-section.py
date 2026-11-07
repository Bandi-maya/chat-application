import os,pathlib,subprocess,time,re,xml.etree.ElementTree as ET
out=pathlib.Path('evidence');out.mkdir(exist_ok=True);pkg=os.environ['TEST_PACKAGE']
def adb(*a):
 p=subprocess.run(['adb',*a],capture_output=True,timeout=45);p.check_returncode();return p.stdout
def nodes():
 for _ in range(3):
  try:
   adb('shell','uiautomator','dump','/sdcard/news-panel.xml');return list(ET.fromstring(adb('shell','cat','/sdcard/news-panel.xml')).iter('node'))
  except (ET.ParseError,subprocess.CalledProcessError):time.sleep(2)
 raise RuntimeError('Hierarchy unavailable')
def tap(text):
 for _ in range(20):
  ns=nodes()
  for n in ns:
   if n.get('text')==text or n.get('content-desc')==text:
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));return
  for n in ns:
   if n.get('text')=='Wait':
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
  time.sleep(2)
 raise RuntimeError('Expected control missing: '+text)
def snap(name):
 (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'));nodes();(out/(name+'.xml')).write_bytes(adb('shell','cat','/sdcard/news-panel.xml'))
adb('shell','settings','put','global','animator_duration_scale','1.0')
adb('shell','am','start','-n',pkg+'/com.example.chat.NewsEmojiPanelProofActivity');time.sleep(5)
tap('News');time.sleep(3)
assert any(n.get('text')=='News Emoji' for n in nodes())
snap('01-news-emoji-light-a');time.sleep(.7);snap('01-news-emoji-light-b')
seen=set()
size=adb('shell','wm','size').decode();w,h=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
for i in range(14):
 for n in nodes():
  desc=n.get('content-desc','');m=re.search(r'News emoji (\d{3}), preview only',desc)
  if m:seen.add(m.group(1))
 snap(f'02-news-scroll-{i:02}')
 if '100' in seen:break
 adb('shell','input','swipe',str(w//2),str(int(h*.84)),str(w//2),str(int(h*.60)),'450');time.sleep(1)
(out/'seen-ids.txt').write_text('\n'.join(sorted(seen)))
assert len(seen)==100,f'Only {len(seen)} custom emoji semantic IDs seen'
adb('shell','am','force-stop',pkg);adb('shell','am','start','-n',pkg+'/com.example.chat.NewsEmojiPanelProofActivity','--ez','dark','true');time.sleep(4);tap('News');time.sleep(2);snap('03-news-emoji-dark')
# Real blank login, no account and no credentials.
adb('shell','am','force-stop',pkg)
activity=adb('shell','cmd','package','resolve-activity','--brief',pkg).decode().strip().splitlines()[-1]
adb('shell','am','start','-n',activity);time.sleep(5);tap('Log In');time.sleep(2);snap('04-current-kotlin-blank-login')
(out/'capture-status.txt').write_text('100 News items in real emoji panel component. Offline fixture only, no send/receive proof. Normal animator scale 1.0. Blank real Login captured without credentials.')
adb('shell','rm','-f','/sdcard/news-panel.xml')
