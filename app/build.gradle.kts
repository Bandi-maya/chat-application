plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.chat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.chat"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(layout.buildDirectory.dir("generated/uniffi"))
            jniLibs.srcDir(layout.buildDirectory.dir("jniLibs"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // QR render + scanner (linked devices): zxing core + CameraX + ML Kit
    // barcode (same decode engine mobile_scanner uses on Android). All free,
    // on-device. Availability verified 200 on Maven Central / Google Maven.
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha07")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Required by the uniffi-generated Kotlin bindings (JNA native calls).
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.github.webrtc-sdk:android:150.7871.04")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("com.google.mediapipe:tasks-vision:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // org.json so unit tests exercise the same JSON helpers as the device.
    testImplementation("org.json:json:20241224")
}

// ── MLS native binding (mls-native UniFFI shim over the repo-owned OpenMLS
// Rust crate in third_party/openmls_android/rust). Requires cargo-ndk and the
// Android NDK; the CI workflow installs both. Local builds without Rust can
// skip with -PskipMlsNative (the app then fails closed at runtime, by design).
val skipMlsNative = providers.gradleProperty("skipMlsNative").orElse("false").map { it == "true" }
val mlsJniLibsDir = layout.buildDirectory.dir("jniLibs")
val uniffiOutDir = layout.buildDirectory.dir("generated/uniffi")

val buildMlsNative = tasks.register<Exec>("buildMlsNative") {
    onlyIf { !skipMlsNative.get() }
    // cargo-ndk resolves the crate from the working directory, so run inside
    // mls-native rather than passing --manifest-path from the repo root.
    workingDir = rootProject.file("mls-native")
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64",
        "-o", mlsJniLibsDir.get().asFile.absolutePath,
        "build", "--release",
    )
}

val generateUniffiBindings = tasks.register<Exec>("generateUniffiBindings") {
    onlyIf { !skipMlsNative.get() }
    dependsOn(buildMlsNative)
    workingDir = rootProject.file("mls-native")
    commandLine(
        "cargo", "run", "--release",
        "--features", "uniffi/cli", "--bin", "uniffi-bindgen", "generate",
        "--library", mlsJniLibsDir.get().file("arm64-v8a/libchaty_mls_native.so").asFile.absolutePath,
        "--language", "kotlin",
        "--out-dir", uniffiOutDir.get().asFile.absolutePath,
    )
}

tasks.named("preBuild") { dependsOn(generateUniffiBindings) }
