"""Only allowlisted state evidence may leave the ephemeral test runner."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from PIL import Image
src=Path('prod-auth-evidence');dst=Path('redacted-auth-evidence');dst.mkdir(exist_ok=True)
allowed=re.compile(r'(?:RUN=[123]|REACHED_HOME=[01]|BOUNCE_REPRODUCED=[01]|PROCESS_DIED=[01]|INCONCLUSIVE_LOGIN=[123]|(?:observe|post-close|relaunch) t=\d+s pid=(?:\d+|none) state=(?:WELCOME|LOGIN|HOME|HOME_CHANGELOG|HOME_BANNER|OTHER))')
for path in src.rglob('*.txt'):
 lines=[line for line in path.read_text(errors='replace').splitlines() if allowed.fullmatch(line)]
 if lines:
  out=dst/path.relative_to(src);out.parent.mkdir(exist_ok=True,parents=True);out.write_text('\n'.join(lines)+'\n')
for path in src.rglob('timeline.log'):
 lines=[line for line in path.read_text(errors='replace').splitlines() if allowed.fullmatch(line)]
 out=dst/path.relative_to(src);out.parent.mkdir(exist_ok=True,parents=True);out.write_text('\n'.join(lines)+'\n')
labels={'Welcome to Chaty','Welcome back','Chaty changelog','Confirm your email','Tasks','Settings'}
for path in src.rglob('*.png'):
 xml=path.with_suffix('.xml')
 if not xml.exists():continue
 try:
  image=Image.open(path).convert('RGB');redacted=Image.new('RGB',image.size,'black');count=0
  for node in ET.parse(xml).iter('node'):
   if node.get('text') not in labels or node.get('password')=='true':continue
   match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds',''))
   if match:
    x,y,u,v=map(int,match.groups())
    if 0<=x<u<=image.width and 0<=y<v<=image.height:
     redacted.paste(image.crop((x,y,u,v)),(x,y));count+=1
  if count:
   out=dst/path.relative_to(src);out.parent.mkdir(exist_ok=True,parents=True);redacted.save(out)
 except Exception:pass # Fail closed: uncertain screenshots never leave the runner.
# Raw logcat, videos, XML, terminal transcript and unredacted screenshots are NOT uploaded.
(dst/'privacy.txt').write_text('Only exact UI label pixels and state/PID/verdict lines retained. Black regions are redacted. Raw account screens, XML, recordings and full logcat withheld.\n')
