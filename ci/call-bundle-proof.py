"""Strict isolated test execution. No live backend or real contacts."""
import pathlib,subprocess,sys
out=pathlib.Path('call-proof');out.mkdir(exist_ok=True)
pkg='com.logybyte.chaty'
def adb(*args):
 return subprocess.run(['adb',*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=900).stdout
failure=False
try:
 for p in ['runtime-apks/app-debug.apk','runtime-apks/app-debug-androidTest.apk']:
  result=adb('install','-r','-t',p);print(result.decode(errors='replace'),flush=True)
  if b'Success' not in result:raise RuntimeError('Install failed')
 for cls in ['CallContactPickerProofTest','CallBundleMatrixProofTest','IncomingCallTypeBadgeProofTest','PrdAuthAccessibilityProofTest','VaultWindowPrivacyProofTest']:
  result=adb('shell','am','instrument','-w','-r','-e','class','com.example.chat.'+cls,pkg+'.test/androidx.test.runner.AndroidJUnitRunner')
  text=result.decode(errors='replace');(out/(cls+'.txt')).write_text(text);print(text,flush=True)
  if 'FAILURES!!!' in text or 'INSTRUMENTATION_FAILED' in text or 'OK (' not in text:failure=True
finally:
 (out/'logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime'))
 for name in ['screenshots','call-matrix']:
  adb('pull','/sdcard/Android/data/'+pkg+'/files/'+name,str(out/name))
if failure:sys.exit(1)
