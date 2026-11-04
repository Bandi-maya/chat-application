"""Source-contract checks. These do not substitute for a Compose/runtime test."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

class CallBundleSettingsContract(unittest.TestCase):
    def test_only_global_appearance_choice_remains(self):
        source = (ROOT / 'app/src/main/kotlin/com/example/chat/ui/settings/CallSettingsScreen.kt').read_text()
        self.assertEqual(source.count('title = "Selected Call Bundle"'), 1)
        self.assertIn('onTap = onOpenCallBundles', source)
        for obsolete in ['"Incoming Call Screen"', '"Ongoing Call Screen"', '"Call Screen Style"', 'dialog = 2', 'dialog = 3']:
            self.assertNotIn(obsolete, source)

    def test_unrelated_settings_and_compatibility_are_preserved(self):
        source = (ROOT / 'app/src/main/kotlin/com/example/chat/ui/settings/CallSettingsScreen.kt').read_text()
        for label in ['Who Can Call Me', 'Excluded Contacts', 'Dynamic Call Island', 'Picture-in-Picture Video', 'Dual-Speaker & Output Routing', 'Low Data Usage for Calls']:
            self.assertIn(label, source)
        for parameter in ['onIncomingTemplate: (String) -> Unit', 'onOngoingTemplate: (String) -> Unit', 'templates: List<CallTemplateOption>']:
            self.assertIn(parameter, source)
        store = (ROOT / 'app/src/main/kotlin/com/example/chat/ui/calls/CallPresentation.kt').read_text()
        for key in ['chaty.calls.incoming_template.v1', 'chaty.calls.ongoing_template.v1', 'chaty.calls.experience_bundle.v1']:
            self.assertIn(key, store)

class CallBundleRuntimeContract(unittest.TestCase):
    def test_hosts_use_global_resolver_not_independent_template(self):
        for rel in ['app/src/main/kotlin/com/example/chat/ui/calls/GlobalCallHost.kt',
                    'ci/gb-parity-overrides/app/src/main/kotlin/com/example/chat/ui/calls/GlobalCallHost.kt']:
            source = (ROOT / rel).read_text()
            self.assertIn('resolveCallBundlePresentation(prefs.callExperienceBundleId, session.state)', source)
            self.assertIn('template = bundlePresentation.template', source)
            self.assertIn('bundleId = activeBundle', source)
            self.assertNotIn('prefs.ongoingCallTemplate', source)

    def test_production_screen_keeps_media_actions_and_source_ownership(self):
        source = (ROOT / 'app/src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt').read_text()
        for symbol in ['RemoteVideoSurface(session, videoSurfaces', 'LocalVideoSurface(videoSurfaces',
                       'onToggleScreenShare', 'onToggleAudioRoute', 'onEnterNativePip', 'statusLabel(session, callDurationSeconds)']:
            self.assertIn(symbol, source)
        for name in ['testref-candidate-build', 'build-kotlin-apk', 'deploy-telegram']:
            workflow = (ROOT / ('.github/workflows/' + name + '.yml')).read_text()
            self.assertIn('            app/src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt', workflow)

class GoogleLatestIncomingContract(unittest.TestCase):
    def test_actions_are_distinct_semantic_and_token_colored(self):
        source = (ROOT / 'app/src/main/kotlin/com/example/chat/ui/calls/bundles/BundleIncomingCallView.kt').read_text()
        section = source.split('private fun GoogleLatestIncomingCall(', 1)[1].split('/** Material You bottom bar', 1)[0]
        self.assertNotIn('DeclineAnswerPuckBar(', section)
        for token in ['containerColor = tokens.declineColor', 'containerColor = tokens.acceptColor',
                      'Icons.Rounded.CallEnd', 'Icons.Rounded.Call', 'Text("Decline")', 'Text("Answer")',
                      'enabled = !actionPending']:
            self.assertIn(token, section)
        self.assertLess(section.index('Text("Decline")'), section.index('Text("Answer")'))

class CallPreviewRuntimeParityContract(unittest.TestCase):
    def test_live_preview_uses_production_surface_and_isolated_local_state(self):
        source = (ROOT / 'app/src/main/kotlin/com/example/chat/ui/calls/bundles/CallExperienceBundlePickerScreen.kt').read_text()
        section = source.split('1, 5, 6, 7, 8 -> {', 1)[1].split('2 -> BundleGroupMeetingView(', 1)[0]
        for symbol in ['OngoingCallScreen(', 'resolveCallBundlePresentation(', 'observeNativePip = false',
                       'autoHideControls = false', 'hasLocalMedia = false', 'videoSurfaces = null',
                       'previewMuted = !previewMuted', 'previewRoute = it']:
            self.assertIn(symbol, section)
        self.assertNotIn('BundleOngoingCallView(', section)
        self.assertIn('mockSession.copy(state = CallSessionState.incoming)', source)

if __name__ == '__main__':
    unittest.main()
