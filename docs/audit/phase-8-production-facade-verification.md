# Phase 8 production facade verification

The production compatibility facade has been renamed from `MockDataStore` / `mock_data_store.dart` to `ChatyDataStore` / `chaty_data_store.dart` across the Flutter `lib/` dependency graph.

Release acceptance requires the Phase 8 workflow to independently verify:

- `flutter analyze --no-fatal-infos`
- full Flutter test suite
- canonical Supabase migration checksum/preflight
- `tools/check_no_production_mocks.sh`
- Android release APK build and non-empty APK verification

This marker intentionally triggers those gates after the bot-authored bulk rename commit, because GitHub does not recursively start workflows from a `GITHUB_TOKEN` push.
