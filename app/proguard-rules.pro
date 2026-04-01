# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Strip debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Preserve Firestore data models from being renamed or stripped.
# Firestore uses reflection to map document fields to class properties.
-keepclassmembers class ca.uwaterloo.cs446.bighero6.data.** {
    <fields>;
    <init>(...);
}
-keep class ca.uwaterloo.cs446.bighero6.data.** { *; }
