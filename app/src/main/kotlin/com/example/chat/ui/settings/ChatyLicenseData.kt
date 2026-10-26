package com.example.chat.ui.settings

import com.example.chat.ui.kit.ChatyLicenseEntry

/**
 * Direct app RUNTIME dependencies of app/build.gradle.kts (implementation), with the license name/URL declared in each
 * artifact's Maven POM (fetched from Google Maven / Maven Central; versions from the Gradle file and the compose-bom
 * 2024.12.01 / firebase-bom 33.7.0 POMs). The Apache-2.0 text is the canonical file from
 * https://www.apache.org/licenses/LICENSE-2.0.txt (sha256 cfc7749b...523d30).
 *
 * NOT covered (unresolved): transitive dependencies, per-artifact NOTICE files, and the full text of non-Apache licenses
 * (JNA dual LGPL-2.1/Apache-2.0, webrtc BSD-3, ML Kit Terms of Service). Build/test-only deps (junit, mockwebserver,
 * coroutines-test, org.json, uiautomator, androidx.test, ui-tooling) are excluded because they are not in the APK.
 * Flutter's LicenseRegistry equivalent (transitive + bundled texts) needs a build-time Gradle report from the owner.
 */
object ChatyLicenseData {
    private const val APACHE_2_0 = CHATY_APACHE_2_0

    val entries: List<ChatyLicenseEntry> = listOf(
        ChatyLicenseEntry("androidx.compose.ui:ui", listOf("Maven coordinate androidx.compose.ui:ui:1.7.6\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.compose.ui:ui-tooling-preview", listOf("Maven coordinate androidx.compose.ui:ui-tooling-preview:1.7.6\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.compose.material3:material3", listOf("Maven coordinate androidx.compose.material3:material3:1.3.1\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.compose.material:material-icons-extended", listOf("Maven coordinate androidx.compose.material:material-icons-extended:1.7.6\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.core:core-ktx", listOf("Maven coordinate androidx.core:core-ktx:1.15.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.activity:activity-compose", listOf("Maven coordinate androidx.activity:activity-compose:1.9.3\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.biometric:biometric", listOf("Maven coordinate androidx.biometric:biometric:1.1.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.fragment:fragment", listOf("Maven coordinate androidx.fragment:fragment:1.8.9\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.lifecycle:lifecycle-runtime-ktx", listOf("Maven coordinate androidx.lifecycle:lifecycle-runtime-ktx:2.8.7\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.lifecycle:lifecycle-process", listOf("Maven coordinate androidx.lifecycle:lifecycle-process:2.8.7\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.lifecycle:lifecycle-viewmodel-compose", listOf("Maven coordinate androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.navigation:navigation-compose", listOf("Maven coordinate androidx.navigation:navigation-compose:2.8.5\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("io.coil-kt:coil-compose", listOf("Maven coordinate io.coil-kt:coil-compose:2.7.0\nLicense declared in the artifact POM:\nThe Apache License, Version 2.0 <https://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("com.google.zxing:core", listOf("Maven coordinate com.google.zxing:core:3.5.3\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <https://www.apache.org/licenses/LICENSE-2.0.txt (declared in parent POM com.google.zxing:zxing-parent:3.5.3)>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.camera:camera-camera2", listOf("Maven coordinate androidx.camera:camera-camera2:1.4.1\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.camera:camera-lifecycle", listOf("Maven coordinate androidx.camera:camera-lifecycle:1.4.1\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.camera:camera-view", listOf("Maven coordinate androidx.camera:camera-view:1.4.1\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("com.google.mlkit:barcode-scanning", listOf("Maven coordinate com.google.mlkit:barcode-scanning:17.3.0\nLicense declared in the artifact POM:\nML Kit Terms of Service <https://developers.google.com/ml-kit/terms>", "License text NOT bundled (unverified here, notice obligations unresolved): ML Kit Terms of Service. See the URL(s) above.")),
        ChatyLicenseEntry("androidx.datastore:datastore-preferences", listOf("Maven coordinate androidx.datastore:datastore-preferences:1.1.1\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.security:security-crypto", listOf("Maven coordinate androidx.security:security-crypto:1.1.0-alpha07\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("com.squareup.okhttp3:okhttp", listOf("Maven coordinate com.squareup.okhttp3:okhttp:4.12.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("com.google.firebase:firebase-messaging", listOf("Maven coordinate com.google.firebase:firebase-messaging:24.1.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("net.java.dev.jna:jna", listOf("Maven coordinate net.java.dev.jna:jna:5.14.0\nLicense declared in the artifact POM:\nLGPL-2.1-or-later <https://www.gnu.org/licenses/old-licenses/lgpl-2.1>\nApache-2.0 <https://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0, "License text NOT bundled (unverified here, notice obligations unresolved): LGPL-2.1-or-later. See the URL(s) above.")),
        ChatyLicenseEntry("com.squareup.retrofit2:retrofit", listOf("Maven coordinate com.squareup.retrofit2:retrofit:2.11.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <https://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("com.squareup.retrofit2:converter-kotlinx-serialization", listOf("Maven coordinate com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <https://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("org.jetbrains.kotlinx:kotlinx-serialization-json", listOf("Maven coordinate org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <https://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("org.jetbrains.kotlinx:kotlinx-coroutines-android", listOf("Maven coordinate org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <https://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("io.github.webrtc-sdk:android", listOf("Maven coordinate io.github.webrtc-sdk:android:150.7871.04\nLicense declared in the artifact POM:\nThe 3-Clause BSD License <https://opensource.org/license/bsd-3-clause/>", "License text NOT bundled (unverified here, notice obligations unresolved): The 3-Clause BSD License. See the URL(s) above.")),
        ChatyLicenseEntry("androidx.media3:media3-exoplayer", listOf("Maven coordinate androidx.media3:media3-exoplayer:1.8.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("androidx.media3:media3-ui", listOf("Maven coordinate androidx.media3:media3-ui:1.8.0\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
        ChatyLicenseEntry("com.google.mediapipe:tasks-vision", listOf("Maven coordinate com.google.mediapipe:tasks-vision:1.1.1\nLicense declared in the artifact POM:\nThe Apache Software License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0.txt>", APACHE_2_0)),
    )
}
