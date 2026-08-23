plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

val releaseKeystorePath = System.getenv("CHATY_ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("CHATY_ANDROID_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("CHATY_ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("CHATY_ANDROID_KEY_PASSWORD")
val applicationIdOverride = System.getenv("CHATY_APPLICATION_ID")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val allowCiDebugSigning =
    System.getenv("CHATY_ALLOW_CI_DEBUG_SIGNING")?.equals("true", ignoreCase = true) == true
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

if (releaseTaskRequested && !allowCiDebugSigning) {
    if (!releaseSigningConfigured) {
        throw GradleException(
            "Production release signing is not configured. Set " +
                "CHATY_ANDROID_KEYSTORE_PATH, CHATY_ANDROID_STORE_PASSWORD, " +
                "CHATY_ANDROID_KEY_ALIAS, and CHATY_ANDROID_KEY_PASSWORD."
        )
    }
    if (applicationIdOverride.isNullOrBlank() || applicationIdOverride.startsWith("com.example.")) {
        throw GradleException(
            "Production CHATY_APPLICATION_ID is required and must not use the com.example namespace."
        )
    }
}

android {
    // Keep the source namespace aligned with the existing Kotlin package and
    // manifest-relative component names. Store/package identity is controlled
    // independently by applicationId below.
    namespace = "com.example.chat"
    // flutter_secure_storage 11 requires Android API 37 metadata. Raising
    // compileSdk only exposes newer compile-time APIs; minSdk/targetSdk keep
    // their existing Flutter-managed behavior and device compatibility.
    compileSdk = 37
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = applicationIdOverride ?: "com.example.chat"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            when {
                releaseSigningConfigured -> signingConfig = signingConfigs.getByName("release")
                allowCiDebugSigning -> signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    // Native Photo Picker / Activity Result contracts used by the custom icon flow.
    implementation("androidx.activity:activity-ktx:1.13.0")
    // Normalizes gallery/camera orientation before Flutter's crop editor sees the image.
    implementation("androidx.exifinterface:exifinterface:1.4.2")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
