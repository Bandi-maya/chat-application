# Keep Kotlin metadata and Compose-generated classes for reflection-based tooling.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn org.jetbrains.annotations.**

# WebRTC JNI_OnLoad resolves Java classes/members by their original names.
# The 150.7871.04 AAR has no consumer rules; R8 otherwise removes/renames JNI
# entry points (confirmed release crash: ClassNotFoundException JniZero).
-keep class org.jni_zero.** { *; }
-keep class org.webrtc.** { *; }
