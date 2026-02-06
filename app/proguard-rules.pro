# Add project specific ProGuard rules here.
# Tabula V3 ProGuard Rules

# Keep Coil classes
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep data models
-keep class com.tabula.v3.data.model.** { *; }

# Keep ML Kit Face Detection classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_vision_face.**

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
