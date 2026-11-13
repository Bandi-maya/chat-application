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
