"""Isolated Direct phase1 UI proof, no live accounts or transfers."""
import pathlib,subprocess
out=pathlib.Path('call-proof');out.mkdir(exist_ok=True)
def adb(*args):
 return subprocess.run(['adb',*args],capture_output=True,timeout=600).stdout
for p in ['runtime-apks/app-debug.apk','runtime-apks/app-debug-androidTest.apk']:
 result=adb('install','-r','-t',p)
 if b'Success' not in result:raise RuntimeError(result)
result=adb('shell','am','instrument','-w','-r','-e','class','com.example.chat.ChatyDirectPhaseOneProofTest','com.logybyte.chaty.test/androidx.test.runner.AndroidJUnitRunner')
text=result.decode(errors='replace');print(text,flush=True);(out/'ChatyDirectPhaseOneProofTest.txt').write_text(text)
adb('pull','/sdcard/Android/data/com.logybyte.chaty/files/screenshots',str(out/'screenshots'))
(out/'logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime'))
if 'OK (' not in text or 'FAILURES!!!' in text or 'INSTRUMENTATION_FAILED' in text:raise SystemExit(1)
