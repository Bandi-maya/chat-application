plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
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
        applicationId = "com.example.chat"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // CI currently produces a release-mode APK using the repository's
            // existing signing configuration. Store-distribution signing must
            // use a persistent private release key supplied through CI secrets.
            signingConfig = signingConfigs.getByName("debug")
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
