import subprocess,time,pathlib,os
out=pathlib.Path("evidence")
def adb(*args):return subprocess.check_output(["adb",*args],timeout=60)
for page in range(4):
 adb("shell","am","force-stop",os.environ["TEST_PACKAGE"])
 adb("shell","am","start","-n",os.environ["TEST_PACKAGE"]+"/com.example.chat.NewsPackProofActivity","--ei","page",str(page))
 time.sleep(8)
 (out/f"news-page-{page+1}-a.png").write_bytes(adb("exec-out","screencap","-p"))
 time.sleep(0.7)
 (out/f"news-page-{page+1}-b.png").write_bytes(adb("exec-out","screencap","-p"))
 adb("shell","input","swipe","540","1700","540","600","500")
 time.sleep(2)
 (out/f"news-page-{page+1}-scrolled.png").write_bytes(adb("exec-out","screencap","-p"))
log=adb("logcat","-d","-s","NewsPackProof:I","*:S").decode()
assert "DECODE_100_OK" in log, "All-100 Android decoder proof missing"
(out/"news-decode.txt").write_text(log)
print("All 100 Android animated WebP decoders passed; synthetic picker captures saved.")
