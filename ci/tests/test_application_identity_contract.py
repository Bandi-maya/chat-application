from pathlib import Path
import re
import unittest
ROOT = Path(__file__).resolve().parents[2]
class ApplicationIdentityContractTest(unittest.TestCase):
    def test_both_gradle_sources_use_owner_application_id(self):
        for rel in ['app/build.gradle.kts', 'ci/gb-parity-overrides/app/build.gradle.kts']:
            source = (ROOT / rel).read_text()
            self.assertRegex(source, r'applicationId\s*=\s*"com\.logybyte\.chaty"')
            self.assertRegex(source, r'namespace\s*=\s*"com\.example\.chat"')
    def test_providers_remain_application_scoped(self):
        source = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('${applicationId}.fileprovider', source)
        self.assertIn('${applicationId}.exportprovider', source)
if __name__ == '__main__': unittest.main()
