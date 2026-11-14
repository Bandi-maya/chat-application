# Keep Kotlin metadata and Compose-generated classes for reflection-based tooling.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn org.jetbrains.annotations.**

# WebRTC JNI_OnLoad resolves Java classes/members by their original names.
# The 150.7871.04 AAR has no consumer rules; R8 otherwise removes/renames JNI
# entry points (confirmed release crash: ClassNotFoundException JniZero).
# Keep only native entry methods, not the unused Java setter that references
# the AAR's unbundled generated JniZeroJni helper.
-keep class org.jni_zero.JniZero {
    private static java.lang.Object[] init();
    private static void crashIfMultiplexingMisaligned(long, long);
}
-keep class org.webrtc.** { *; }

# ---- R8 hardening (patch 26): keep rules for the rest of the stack ----
# Room: generated database implementations and entities resolve reflectively.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# SQLCipher native bridge.
-keep class net.zetetic.** { *; }

# kotlinx.serialization: generated serializers are looked up reflectively.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.chat.**$$serializer { *; }
-keepclassmembers class com.example.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep class <1> {
    <init>(...);
}

# OkHttp/OkIO ship consumer rules; silence optional-platform noise only.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# BouncyCastle: provider classes register and resolve by name.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# JNA + UniFFI MLS bindings: native methods and Structure fields are bound
# reflectively; these AARs ship no consumer rules.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    public <fields>;
    public <init>();
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-dontwarn com.sun.jna.**
-dontwarn org.slf4j.**

# ML Kit barcode: models load reflectively.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.odml.**

# CameraX, media3, Coil, Lottie, WorkManager, DataStore and Biometric ship
# consumer rules inside their AARs; only silence known-harmless notes here.
-dontwarn androidx.camera.**
-dontwarn androidx.media3.**
-dontwarn com.airbnb.lottie.**

# BuildConfig carries the tamper-guard inputs and routing keys.
-keep class com.example.chat.BuildConfig { *; }
