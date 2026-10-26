# Chaty native Android (Kotlin branch)

This branch contains the native Android/Kotlin port of `api@3bee780d`, using Jetpack Compose. The `api` branch retains the original Flutter project. Unused Flutter app sources, tests, platform runners, Dart package metadata and Flutter-specific build workflows have been removed from this branch by the user's request. Git history retains the source baseline and cleanup commits.

## Start here

- [Configuration and native build requirements](docs/KOTLIN_CONFIGURATION.md)
- [Checked evidence and remaining verification limits](docs/kotlin-parity.md)
- Native Android module: `app/`
- Native build and smoke workflow: `.github/workflows/build-kotlin-apk.yml`

Copy root `.env.example` to ignored root `.env`, set your existing public backend routing values, then rebuild. Never put credentials or private server keys there: these values are compiled into the APK. Firebase setup remains separately deferred.

After installing the documented Java/Gradle/Android SDK/NDK/Rust/cargo-ndk prerequisites:

```
gradle :app:testDebugUnitTest :app:assembleDebug
```

Do not skip native MLS in a real test build. `mls-native/` exposes the shared Rust encryption engine from `third_party/openmls_android/rust`. The shared crate still requires its FRB annotations/generated Rust bridge dependency; that is intentionally retained to preserve working encryption, not a Flutter application entrypoint. Its licenses and notices remain.

`assets/`, native launcher assets, backend/schema/security tooling and historical audit/design documents are retained. Legacy patch/QA tools and historical documents may mention Dart paths from the original source; they are not the native build route. Kotlin preference identifiers carrying a `flutter.` prefix are compatibility data contracts, not a Flutter runtime, and have not been renamed.

The tested source is ready for configured testing, not a claim that all server/phone/call/camera/audio/design behavior has been independently verified. Read the evidence record before treating a build as full parity. Application ID/signing identity was not changed; do not assume a debug APK updates an older differently signed APK or clear installed data to work around it.
