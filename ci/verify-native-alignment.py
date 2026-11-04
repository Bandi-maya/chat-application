#!/usr/bin/env python3
"""Inspect final APK native ELF PT_LOAD and uncompressed ZIP data alignment.
Does not prove 16KB runtime compatibility. Never modifies or extracts the APK.
"""
import argparse, hashlib, json, struct, sys, zipfile
from pathlib import Path

PAGE = 16384

def inspect_elf(data):
    if data[:4] != b'\x7fELF' or len(data) < 64:
        raise ValueError('not a complete ELF file')
    cls, encoding = data[4], data[5]
    if encoding not in (1, 2) or cls not in (1, 2):
        raise ValueError('unsupported ELF class/encoding')
    endian = '<' if encoding == 1 else '>'
    if cls == 2:
        offset = struct.unpack_from(endian+'Q', data, 32)[0]
        entry_size, count = struct.unpack_from(endian+'HH', data, 54)
        fmt = endian+'IIQQQQQQ'
    else:
        offset = struct.unpack_from(endian+'I', data, 28)[0]
        entry_size, count = struct.unpack_from(endian+'HH', data, 42)
        fmt = endian+'IIIIIIII'
    expected = struct.calcsize(fmt)
    if entry_size < expected or count == 0 or count == 65535:
        raise ValueError('missing/extended unsupported program headers')
    loads = []
    for index in range(count):
        header = struct.unpack_from(fmt, data, offset + index*entry_size)
        if header[0] != 1:
            continue
        if cls == 2:
            _, _, file_offset, vaddr, _, file_size, memory_size, alignment = header
        else:
            _, file_offset, vaddr, _, file_size, memory_size, _, alignment = header
        passed = alignment >= PAGE and alignment & (alignment-1) == 0 and file_offset % PAGE == vaddr % PAGE
        loads.append(dict(offset=file_offset, vaddr=vaddr, filesz=file_size, memsz=memory_size, alignment=alignment, passed=passed))
    return {'elf_class': 64 if cls == 2 else 32, 'load_segments': loads, 'passed': bool(loads) and all(row['passed'] for row in loads)}

def inspect_apk(path):
    path = Path(path)
    entries = []
    with path.open('rb') as raw, zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if not (info.filename.startswith('lib/') and info.filename.endswith('.so')):
                continue
            raw.seek(info.header_offset)
            local = raw.read(30)
            if len(local) != 30 or local[:4] != b'PK\x03\x04':
                raise ValueError('invalid ZIP local header')
            name_length, extra_length = struct.unpack_from('<HH', local, 26)
            data_offset = info.header_offset + 30 + name_length + extra_length
            stored = info.compress_type == zipfile.ZIP_STORED
            row = {'path': info.filename, 'compressed': not stored, 'zip_data_offset': data_offset,
                   'zip_alignment_passed': not stored or data_offset % PAGE == 0}
            try:
                row['elf'] = inspect_elf(archive.read(info))
                row['passed'] = row['zip_alignment_passed'] and row['elf']['passed']
            except (ValueError, struct.error) as error:
                row.update(passed=False, error=str(error))
            entries.append(row)
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b''):
            digest.update(chunk)
    return {'artifact': path.name, 'sha256': digest.hexdigest(), 'page_size': PAGE, 'native_libraries': entries,
            'alignment_passed': all(row['passed'] for row in entries),
            'runtime_status': 'NOT RUN',
            'limits': ['PT_LOAD and stored ZIP alignment only; RELRO and SDK runtime assumptions require readelf review and 16KB device proof.',
                       'Compressed native entries skip ZIP data alignment, not ELF alignment. AAB-generated split APKs must each be checked.',
                       'Not signing/package/ABI completeness verification. No native libraries means this APK has none, not that all delivery splits are covered.']}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    result = inspect_apk(args.apk)
    Path(args.output).write_text(json.dumps(result, indent=2)+'\n')
    print('Native alignment:', 'PASS' if result['alignment_passed'] else 'FAIL', 'runtime NOT RUN')
    return 0 if result['alignment_passed'] else 1

if __name__ == '__main__':
    sys.exit(main())
