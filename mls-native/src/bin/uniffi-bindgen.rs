// Standalone uniffi-bindgen binary target. The Gradle generateUniffiBindings
// task runs `cargo run --release --features uniffi/cli --bin uniffi-bindgen
// generate ...`; uniffi 0.28 exposes the CLI entry point as
// uniffi::uniffi_bindgen_main behind the `cli` feature, but the package must
// still declare a bin target under that name. Lives in src/bin/ so Cargo
// auto-discovers it. It does not link the cdylib-only chaty_mls_native lib.
fn main() {
    uniffi::uniffi_bindgen_main();
}
