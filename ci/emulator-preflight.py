"""CI-only boot/launcher guard. Never dismiss app ANRs or change test outcomes."""
import pathlib, subprocess, time, xml.etree.ElementTree as ET, re, uuid
out=pathlib.Path('screenshots/emulator-preflight');out.mkdir(parents=True,exist_ok=True)
def adb(*args):
 r=subprocess.run(['adb',*args],capture_output=True,timeout=25)
 if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
 return r.stdout
def capture(name):
 # uiautomator sometimes exits zero without creating XML. Use a fresh path on
 # each bounded attempt so a stale hierarchy can never pass the launcher guard.
 failures=[]
 for attempt in range(1,4):
  prefix=out/(name+'-attempt-'+str(attempt))
  remote='/sdcard/preflight-'+uuid.uuid4().hex+'.xml'
  (prefix.with_suffix('.png')).write_bytes(adb('exec-out','screencap','-p'))
  try:
   r=subprocess.run(['adb','shell','uiautomator','dump',remote],capture_output=True,timeout=25)
   prefix.with_suffix('.dump.stdout.txt').write_bytes(r.stdout)
   prefix.with_suffix('.dump.stderr.txt').write_bytes(r.stderr)
   prefix.with_suffix('.dump.exit.txt').write_text(str(r.returncode)+'\n')
   if r.returncode:raise RuntimeError('uiautomator dump exit '+str(r.returncode))
   data=adb('shell','cat',remote)
   prefix.with_suffix('.xml').write_bytes(data)
   root=ET.fromstring(data)
   if root.tag!='hierarchy' or next(root.iter('node'),None) is None:
    raise RuntimeError('Missing hierarchy nodes')
   (out/(name+'.xml')).write_bytes(data)
   (out/(name+'.png')).write_bytes(prefix.with_suffix('.png').read_bytes())
   return root
  except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as exc:
   if isinstance(exc, subprocess.TimeoutExpired):
    prefix.with_suffix('.dump.stdout.txt').write_bytes(exc.stdout or b'')
    prefix.with_suffix('.dump.stderr.txt').write_bytes(exc.stderr or b'')
   reason=type(exc).__name__+': '+str(exc)
   failures.append(reason)
   prefix.with_suffix('.error.txt').write_text(reason+'\n')
   print('EMULATOR-PREFLIGHT XML attempt',attempt,'failed:',reason,flush=True)
  finally:
   # Cleanup is best effort; UUID paths prevent reuse even if cleanup fails.
   try:adb('shell','rm','-f',remote)
   except (RuntimeError, subprocess.TimeoutExpired):pass
  if attempt<3:time.sleep(2)
 raise RuntimeError('No valid UI hierarchy after 3 attempts: '+'; '.join(failures))
def main():
 subprocess.run(['adb','wait-for-device'],check=True,timeout=120)
 for _ in range(60):
  if adb('shell','getprop','sys.boot_completed').strip()==b'1':break
  time.sleep(2)
 else:raise RuntimeError('Boot not complete')
 adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard')
 for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
  adb('shell','settings','put','global',key,'0')
 adb('shell','input','keyevent','KEYCODE_HOME')
 time.sleep(3)
 root=capture('launcher-before')
 # Dismiss exactly a pre-test Pixel Launcher system ANR, once. No other dialog.
 text=' '.join(n.get('text','') for n in root.iter('node'))
 if "Pixel Launcher isn't responding" in text or 'Pixel Launcher isn\u2019t responding' in text:
  close=[n for n in root.iter('node') if n.get('text','')=='Close app' and n.get('package','')=='android']
  if len(close)!=1:raise RuntimeError('Known launcher ANR cannot be safely dismissed')
  b=list(map(int,re.findall(r'\d+',close[0].get('bounds',''))))
  if len(b)!=4:raise RuntimeError('Missing Close app bounds')
  adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
  (out/'launcher-anr-dismissed.txt').write_text('One pre-test Pixel Launcher system ANR dismissed; original evidence preserved.\n')
  adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(4)
 for i in range(2):
  root=capture('launcher-idle-'+str(i))
  text=' '.join(n.get('text','') for n in root.iter('node'))
  if "isn't responding" in text or 'isn\u2019t responding' in text:raise RuntimeError('Pre-test ANR remains; tests not started')
  state=adb('shell','dumpsys','activity','activities').decode(errors='replace')
  (out/('activities-'+str(i)+'.txt')).write_text(state)
  resumed='\n'.join(l for l in state.splitlines() if 'mResumedActivity' in l or 'topResumedActivity' in l)
  if not any(p in resumed for p in ['com.google.android.apps.nexuslauncher','com.android.launcher3']):
   raise RuntimeError('Launcher not resumed: '+resumed)
  time.sleep(2)
 (out/'ready.txt').write_text('Boot complete; keyguard dismissed; animations disabled; launcher resumed and no ANR in two idle captures.\n')
 print('EMULATOR-PREFLIGHT READY',flush=True)

if __name__ == "__main__":
 main()
