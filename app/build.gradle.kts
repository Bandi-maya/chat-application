import java.io.File
import java.net.URI
import java.security.MessageDigest

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
            assets.srcDir(layout.buildDirectory.dir("generated/faceModel/assets"))
            res.srcDir(layout.buildDirectory.dir("generated/launcherIcons/res"))
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
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
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
    // FCM transport (firebase_notification_service.dart port). The
    // google-services plugin is NOT applied: google-services.json exists in
    // neither repo branch, and the plugin fails the build without it. The
    // messaging client compiles and registers now; runtime activation
    // happens once the Firebase config lands.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
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
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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


// ── MediaPipe face landmarker model (FaceDetectionBridge.MODEL_ASSET).
// Downloaded at build time from the official MediaPipe model bucket and
// verified against the pinned SHA-256 (fail closed on mismatch), so no
// binary lives in the repo and the app carries no runtime network
// dependency for the model.
val faceLandmarkerUrl =
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
val faceLandmarkerSha256 =
    "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
val faceLandmarkerSize = 3758596L
val faceLandmarkerAssetsDir = layout.buildDirectory.dir("generated/faceModel/assets")

val downloadFaceLandmarker = tasks.register("downloadFaceLandmarker") {
    val outFile = faceLandmarkerAssetsDir.get().file("face_landmarker.task").asFile
    outputs.file(outFile)
    doLast {
        fun sha256(f: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (outFile.exists() && outFile.length() == faceLandmarkerSize &&
            sha256(outFile) == faceLandmarkerSha256
        ) {
            return@doLast
        }
        outFile.parentFile.mkdirs()
        val tmp = File(outFile.parentFile, outFile.name + ".download")
        tmp.delete()
        URI(faceLandmarkerUrl).toURL().openStream().use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        check(tmp.length() == faceLandmarkerSize) {
            tmp.delete()
            "face_landmarker.task size mismatch: got ${tmp.length()}, want $faceLandmarkerSize"
        }
        val hash = sha256(tmp)
        check(hash == faceLandmarkerSha256) {
            tmp.delete()
            "face_landmarker.task SHA-256 mismatch: got $hash, want $faceLandmarkerSha256"
        }
        check(tmp.renameTo(outFile)) { "failed to move $tmp to $outFile" }
    }
}

tasks.named("preBuild") { dependsOn(downloadFaceLandmarker) }

// Launcher alias icons (warm/outline/obsidian/glass/signal/fold x 5 densities). The PNGs are stored
// as a base64 tar.gz (split into part-NN.b64 files) of drawable-<density>/ic_launcher_<variant>.png because binary files cannot be
// committed through the integration route; this task unpacks them into a generated res dir.
val launcherIconsParts = layout.projectDirectory.dir("launcher-icons")
val launcherIconsOutDir = layout.buildDirectory.dir("generated/launcherIcons/res")

val unpackLauncherIcons = tasks.register("unpackLauncherIcons") {
    inputs.dir(launcherIconsParts)
    outputs.dir(launcherIconsOutDir)
    doLast {
        val out = launcherIconsOutDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val parts = launcherIconsParts.asFile.listFiles { f -> f.name.startsWith("part-") && f.name.endsWith(".b64") }!!.sortedBy { it.name }
        check(parts.isNotEmpty()) { "no launcher icon archive parts" }
        val raw = java.util.Base64.getMimeDecoder().decode(parts.joinToString("") { it.readText().trim() })
        java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(raw)).use { gz ->
            val header = ByteArray(512)
            fun readFully(buf: ByteArray, len: Int): Boolean {
                var n = 0
                while (n < len) {
                    val r = gz.read(buf, n, len - n)
                    if (r < 0) return false
                    n += r
                }
                return true
            }
            while (readFully(header, 512)) {
                if (header.all { it == 0.toByte() }) break
                val name = String(header, 0, 100, Charsets.US_ASCII).trimEnd(0.toChar())
                val size = String(header, 124, 12, Charsets.US_ASCII).trim(0.toChar(), ' ').toLong(8)
                val type = header[156].toInt().toChar()
                val padded = ((size + 511) / 512) * 512
                val data = ByteArray(padded.toInt())
                if (padded > 0) check(readFully(data, padded.toInt())) { "truncated tar entry $name" }
                if ((type == '0' || type == 0.toChar()) && size > 0) {
                    val clean = name.removePrefix("./")
                    check(!clean.contains("..")) { "unsafe tar entry $clean" }
                    val target = File(out, clean)
                    target.parentFile.mkdirs()
                    target.writeBytes(data.copyOf(size.toInt()))
                }
            }
        }
        val count = out.walkTopDown().count { it.isFile && it.name.startsWith("ic_launcher_") }
        check(count == 30) { "expected 30 launcher icons, unpacked $count" }
    }
}

tasks.named("preBuild") { dependsOn(unpackLauncherIcons) }
