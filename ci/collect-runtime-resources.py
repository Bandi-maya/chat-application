#!/usr/bin/env python3
"""Bounded diagnostic sampling only. No app data or account payloads are read."""
import datetime,json,pathlib,subprocess,time,sys,os,signal
out=pathlib.Path('screenshots'); out.mkdir(exist_ok=True)
running=True
def stop(*_):
    global running
    running=False
signal.signal(signal.SIGTERM,stop); signal.signal(signal.SIGINT,stop)
def cmd(argv,timeout=3):
    try:
        r=subprocess.run(argv,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=timeout)
        return {'rc':r.returncode,'text':r.stdout[-40000:]}
    except Exception as e:return {'error':str(e)}
gone_logged=False
def host_kernel_evidence():
    # Best effort, read-only; each command is bounded and may be denied on the runner (recorded as such).
    return {'dmesg':cmd(['sudo','-n','dmesg','--ctime'],timeout=8),'journal_kernel':cmd(['sudo','-n','journalctl','-k','-n','200','--no-pager'],timeout=8),'oom_grep':cmd(['bash','-c',"(sudo -n dmesg 2>/dev/null || dmesg 2>/dev/null) | grep -iE 'killed process|out of memory|oom-kill|qemu' | tail -50"],timeout=8)}
with (out/'host-emulator-resources.jsonl').open('w') as log:
    seen_qemu=False
    while running:
        row={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'host_meminfo':pathlib.Path('/proc/meminfo').read_text(),'emulator_processes':cmd(['ps','-eo','pid,ppid,stat,%cpu,%mem,rss,args'])}
        row['emulator_processes']['text']='\n'.join(line for line in row['emulator_processes'].get('text','').splitlines() if any(x in line.lower()for x in ('qemu','emulator','adb')))
        row['adb_devices']=cmd(['adb','devices','-l'])
        row['host_loadavg']=pathlib.Path('/proc/loadavg').read_text().strip()
        row['device_meminfo']=cmd(['adb','shell','cat','/proc/meminfo'])
        row['app_meminfo']=cmd(['adb','shell','dumpsys','meminfo','com.logybyte.chaty'])
        has_qemu='qemu-system' in row['emulator_processes']['text']
        seen_qemu=seen_qemu or has_qemu
        if seen_qemu and not has_qemu and not gone_logged:
            gone_logged=True; row['emulator_process_gone']=True; row['host_kernel_evidence']=host_kernel_evidence()
        log.write(json.dumps(row)+'\n');log.flush()
        for _ in range(100):
            if not running:break
            time.sleep(.1)
