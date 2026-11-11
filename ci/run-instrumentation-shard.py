#!/usr/bin/env python3
"""Run installed identical synthetic APKs and preserve raw status plus derived JUnit XML."""
import pathlib, subprocess, sys, time, re, xml.etree.ElementTree as ET

def parse(text, classes):
    pending = {}; cases = []; expected_count = None; final_code = None; fatal = False; started = []
    for line in text.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, sep, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if sep: pending[key] = value
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = int(line.split(': ',1)[1])
            if 'numtests' in pending: expected_count = int(pending['numtests'])
            if code == 1 and pending.get('class') and pending.get('test'): started.append((pending['class'], pending['test']))
            if code <= 0 and pending.get('class') and pending.get('test'):
                cases.append((pending['class'], pending['test'], code, pending.get('stack','') or pending.get('stream','')))
            pending = {}
        elif line.startswith('INSTRUMENTATION_CODE: '): final_code = int(line.split(': ',1)[1])
        elif line.startswith(('INSTRUMENTATION_FAILED:', 'INSTRUMENTATION_ABORTED:')): fatal = True
        elif pending and not line.startswith('INSTRUMENTATION_'):
            key = 'stack' if 'stack' in pending else 'stream'
            pending[key] = pending.get(key,'') + '\n' + line
    seen = [(c,t) for c,t,_,_ in cases]
    problems=[]
    # Diagnostic only: a method that reported "started" (code 1) but never completed. Evidence of an abort, not a cause.
    unfinished = [f'{c}#{t}' for c,t in started if (c,t) not in set(seen)]
    if unfinished: problems.append('INCOMPLETE: started but no completion result: ' + ', '.join(unfinished))
    if fatal or final_code != -1: problems.append(f'Runner failed/aborted or missing success termination: {final_code}')
    if not cases or expected_count is None or expected_count != len(cases): problems.append(f'Missing or count-mismatched test results: declared={expected_count}, actual={len(cases)}')
    if len(seen) != len(set(seen)): problems.append('Duplicate completed test result')
    if set(c for c,_,_,_ in cases) != set(x.split('#',1)[0] for x in classes): problems.append('Completed class coverage differs from requested partition')
    # A requested class#method entry must not disappear unnoticed: identity-level check.
    missing_methods = [m for m in classes if '#' in m and tuple(m.split('#',1)) not in set(seen)]
    if missing_methods: problems.append('Requested method missing from results: ' + ', '.join(missing_methods))
    if any(code not in (0,-1,-2,-3,-4) for _,_,code,_ in cases): problems.append('Unknown test outcome code')
    return cases, problems

def main():
    classes = sys.argv[1].split(',')
    out=pathlib.Path('screenshots');out.mkdir(exist_ok=True)
    began=time.time()
    # Stream/flush raw evidence: an emulator/runner crash must not erase already emitted statuses.
    proc=subprocess.Popen(['adb','shell','am','instrument','-w','-r','-e','class',','.join(classes),'com.logybyte.chaty.test/androidx.test.runner.AndroidJUnitRunner'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    lines=[]; previous_class=None
    with (out/'instrumentation-raw-status.txt').open('w') as raw, (out/'class-boundaries.txt').open('w') as boundaries:
        for line in proc.stdout:
            raw.write(line); raw.flush(); lines.append(line); print(line,end='',flush=True)
            if line.startswith('INSTRUMENTATION_STATUS: class='):
                klass=line.strip().split('=',1)[1]
                if klass != previous_class:
                    previous_class=klass
                    boundaries.write(f'{time.time():.3f} {klass}\n'); boundaries.flush()
    proc.wait(); text=''.join(lines)
    result=proc
    cases,problems=parse(text,classes)
    if result.returncode:problems.append(f'adb exit={result.returncode}')
    suite=ET.Element('testsuite',name='AndroidJUnitRunner-derived',tests=str(len(cases)+bool(problems)),time=str(time.time()-began))
    for klass,test,code,details in cases:
        case=ET.SubElement(suite,'testcase',classname=klass,name=test)
        if code in (-3,-4):ET.SubElement(case,'skipped',message='Ignored or assumption failed')
        elif code:ET.SubElement(case,'failure' if code==-2 else 'error',message=details.splitlines()[0]if details else f'runner status {code}').text=details
    if problems:ET.SubElement(ET.SubElement(suite,'testcase',classname='shard.integrity',name='complete_runner_evidence'),'error').text='\n'.join(problems)
    for key in ('failures','errors','skipped'):suite.set(key,str(sum(len(c.findall({'failures':'failure','errors':'error','skipped':'skipped'}[key]))for c in suite.findall('testcase'))))
    dest=pathlib.Path('app/build/outputs/androidTest-results/connected');dest.mkdir(parents=True,exist_ok=True)
    ET.ElementTree(suite).write(dest/'TEST-shard-runner-derived.xml',encoding='utf-8',xml_declaration=True)
    print('SHARD-EVIDENCE',suite.attrib,'integrity',problems)
    return 1 if problems or any(code not in (0,-3,-4)for _,_,code,_ in cases)else 0
if __name__=='__main__':sys.exit(main())
