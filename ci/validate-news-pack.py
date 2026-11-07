from pathlib import Path
import json,hashlib,struct
root=Path('app/src/main/assets/stickers/news');m=json.loads((root/'manifest.json').read_text());assert len(m['items'])==100
for e in m['items']:
 b=(root/e['animation']).read_bytes();assert hashlib.sha256(b).hexdigest()==e['sha256'];assert 0<len(b)<2*1024*1024
 assert b[:4]==b'RIFF' and b[8:12]==b'WEBP'
 chunks=[];i=12;duration=0
 while i<len(b):
  tag=b[i:i+4];n=int.from_bytes(b[i+4:i+8],'little');v=b[i+8:i+8+n];chunks.append(tag)
  if tag==b'VP8X':assert int.from_bytes(v[4:7],'little')+1==128 and int.from_bytes(v[7:10],'little')+1==128
  if tag==b'ANMF':duration+=int.from_bytes(v[12:15],'little')
  i+=8+n+(n%2)
 assert b'ANIM' in chunks and chunks.count(b'ANMF')>1
 assert duration==e['duration_ms'],e['id']
 assert (root/e['poster']).is_file()
assert sum(e['quality']=='held_fidelity' for e in m['items'])==9
print('NEWS_PACK:100 identities/hash/animated-container/dimensions/durations/size/poster contracts passed')
