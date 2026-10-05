# ---- TDLib -------------------------------------------------------------
# libtdjni.so looks up classes, fields and constructors of org.drinkless.tdlib.*
# by name through JNI (TdApi objects are built/read natively), and calls
# Client$ResultHandler / ExceptionHandler back. Renaming or removing anything
# in this package breaks the native <-> Java bridge at runtime.
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- Logging -----------------------------------------------------------
# Strip verbose/debug/info logging from release builds (chat/user ids etc.).
# Only effective because proguard-android-optimize.txt enables optimization.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep useful stack traces for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- security-crypto / Tink (OpenAI key store) -------------------------
# Tink references compile-only Error Prone annotations; without this R8 fails the release build.
-dontwarn com.google.errorprone.annotations.Immutable
