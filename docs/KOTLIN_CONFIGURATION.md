# Kotlin client configuration

The Android module builds with Gradle, not Flutter. Copy the repository's `.env.example` to an ignored root `.env`, then put your public client routing overrides there, if needed. The user-supplied defaults are centralized in `app/src/main/java/com/example/chat/data/api/BackendRoutingPolicy.kt`:

```
REST_API_BASE_URL=https://api.hub.logybyte.in
TENANT_ID=chat-application
WEBSOCKET_URL=wss://api.hub.logybyte.in/api/chat/ws{userId}
```

These exact public defaults were supplied and confirmed by the user. The WebSocket template deliberately has no slash before {userId}: user u-42 produces wss://api.hub.logybyte.in/api/chat/wsu-42. No live connectivity or route availability is implied. Nonempty .env values override the defaults. Without a WebSocket override the supplied default template is used. Legacy /ws/school values are ignored; when no valid template remains, the resolver derives ws/wss from REST_API_BASE_URL, removes a trailing /api/vN and appends `/chat/ws/<userId>`. Legacy /ws/school overrides are ignored, matching Flutter. Both {userId} and $userId placeholders are supported. A WebSocket base without a placeholder gets `/chat/ws/<userId>` appended.

Root `.env` nonempty values take priority over build overrides, matching Flutter's dotenv custom values. Build overrides can be supplied with `-PREST_API_BASE_URL=...`, `-PTENANT_ID=...`, `-PWEBSOCKET_URL=...`, or environment variables with those names. Gradle properties beat process environment values. Rebuild after configuration changes; a compiled APK does not read a phone-side .env.

Tenant selection restores the user's saved tenant after the .env initial tenant, exactly as Flutter init() does. Selecting the default tenant uses the build TENANT_ID or `chat-application`. With no REST override, the centralized supplied HTTPS endpoint is used. Synthetic emulator CI explicitly supplies loopback REST/WebSocket overrides so fake sessions never contact that server.

Only these three keys are imported from .env. Quoted values and an optional `export ` prefix are supported. Use one KEY=value per line; do not use variable interpolation or inline comments. Do not commit a real .env. The values are compiled into the client and are readable from an APK: they are not secret storage. Never place passwords, service-role keys, signing material, API secrets, access tokens, or private backend credentials in these settings. User credentials remain entered through the app and stored by its existing secure-session path.

Build prerequisites: Java 17, Gradle (CI currently uses 9.8.1), Android SDK platform 36/build tools and NDK, Rust stable with `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android` targets, and cargo-ndk. Point ANDROID_NDK_HOME and ANDROID_NDK_ROOT at the installed NDK. For example, after Rust is installed:

```
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
gradle :app:testDebugUnitTest :app:assembleDebug
```

Do not skip native MLS for a real test build: it is required for encrypted messaging. The Gradle tasks generate the UniFFI bindings and native libraries. Build-time downloads require network access. The existing CI workflow uses the same Gradle module. Its unconfigured synthetic tests intentionally use emulator loopback and fake sessions; they do not verify your live login server.

Firebase configuration remains separately deferred at the user's request. Adding routing values does not activate FCM, change the application ID/signing certificate, migrate local chats, or make the Kotlin APK an update to a differently signed installed app. Package and signing are unchanged by this wiring.
