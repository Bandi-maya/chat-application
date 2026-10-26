# Baseline

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

Reference Next4: run40389044496, commit a64c356f28e95e79c6a73c398bb5541023b5ce6c; overlay132 SHA256 9968d13fb9fd25147c181e60d3a1b90997a65fced59ecd2e2d9d3962c123178e. APK SHA256 d6935aeff41c8a6025b6a6e8e479ff738200249da64ffc18d7feb00929d8014f. Build result:1006 unit tests/112 suites pass. Runtime at01:42 was still native compilation, no instrumentation yet. No installed-and-run APK claim from this document.

Current source reviewed at01:43 IST: local Next5 660-file archive plus SHA manifest, manifest digest46d68fe6cc98326b439c50a988e1d3984874c125d81bdd56d6b1536c53294be9. Resources/assets excluded from source snapshot, not claimed missing from app. Local changes must be tracked by merged source hash and tested build. Public branch alone is not the cumulative candidate. Updates through01:47 are labeled below.

## Project
Repository https://github.com/Bandi-maya/chat-application ; reported branch kotlin. Snapshot is not live git status: inspect uncommitted user work and checkpoint before changes, no destructive reset.

Read build files: Kotlin/Compose plugin2.0.21; AGP8.7.3; Java17 target; compileSdk/targetSdk36; minSdk24; ID/namespace com.example.chat; versionCode1/versionName1.0.0; debug and minified/resource-shrunk release. Compose BOM2024.12.01. Existing Rust/UniFFI crypto bridge retained. Exact Gradle/Rust toolchain needs complete checkout validation, not guesswork. MainActivity/ChatyApplication/AppRoot/ServiceLocator remain existing wiring; preserve custom REST/WebSocket backend.

## Project commands, read from CI, not executed by this research
```sh
gradle :app:generateUniffiBindings --no-daemon
gradle :app:assembleDebug --no-daemon
gradle :app:testDebugUnitTest --no-daemon
bash ci/run-smoke.sh
```
CI runtime configuration: Java17; API34 default x86_64 emulator, Pixel6 profile. Complete source/native prerequisites required. Synthetic tests must not access real accounts/backend. Inspect test XML and actual install/runtime evidence before reporting pass. Signing differs between historical installs; no uninstall/data-clear workaround.


## Next5 publication checkpoint,01:49 IST
Build launched: https://github.com/Bandi-maya/chat-application/actions/runs/40394644500
Commit400782ad76a951ff43d38e2d92a2f2997292c9bf. Frozen156-file overlay SHA2565cb7dbc2e3f63eac1cd947d8d6591e82713faa39d1e58a8fbc4f5139304ffb8e. At01:49 the run had launched; no completed native build/test/runtime result was recorded in this review. This supersedes the earlier local-unbuilt status only for launch, not validation.01:42 test declarations are historical snapshot, not frozen-candidate count.


## Runtime outcome update,01:54 IST
Next4 runtime job failed during Android test compilation, BEFORE app installation/instrumentation: AttachmentFileCacheTest.kt:295 unresolved EditSession/outputFile reference. No app started, no runtime XML or app visual passes. This is a test-source compile failure, not an observed app crash. Native and main application build and1006 JVM unit passes remain valid. Source: https://github.com/Bandi-maya/chat-application/actions/runs/40389044496/job/121812020284 . A missing EditSession import is the test compile cause; outputFile is a cascade error. No production defect is shown by this compile failure. Next5 launch was recorded at01:49; no completed outcome is recorded in this review.
