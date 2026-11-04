import importlib.util
import struct
import unittest
from pathlib import Path
spec = importlib.util.spec_from_file_location('alignment', Path(__file__).resolve().parents[1] / 'verify-native-alignment.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
class NativeAlignmentTest(unittest.TestCase):
    def elf(self, alignment):
        data = bytearray(120)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, 0, 0, 0, 120, 120, alignment)
        return bytes(data)
    def test_4k_fails(self):
        self.assertFalse(module.inspect_elf(self.elf(4096))['passed'])
    def test_16k_passes(self):
        self.assertTrue(module.inspect_elf(self.elf(16384))['passed'])
    def test_truncated_is_not_pass(self):
        with self.assertRaises(ValueError): module.inspect_elf(b'not ELF')
if __name__ == '__main__': unittest.main()
