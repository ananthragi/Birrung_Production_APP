# ============================================================================
#  Birrung TV app — R8 / ProGuard keep rules for release builds
# ============================================================================

# Keep line numbers for readable Crashlytics stack traces, hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- App code -------------------------------------------------------------
# Keep all app classes. This app parses JSON by field/key names and uses several
# reflection-based libraries, so obfuscating app code risks subtle runtime breakage.
# (Library code is still shrunk/optimized by R8; app classes are kept intact.)
-keep class au.com.birrung.tv.** { *; }

# Custom views referenced from XML layouts.
-keep public class * extends android.view.View { *; }

# --- Gson (model classes are serialized/deserialized by field name) --------
-keepattributes AnnotationDefault
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# --- Volley ----------------------------------------------------------------
-keep class com.android.volley.** { *; }
-dontwarn com.android.volley.**

# --- OkHttp / Okio ---------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Eclipse Paho MQTT -----------------------------------------------------
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# --- ExoPlayer -------------------------------------------------------------
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# --- VLC (native) ----------------------------------------------------------
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# --- Glide -----------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }
-dontwarn com.bumptech.glide.**

# --- Firebase / Crashlytics / Play services --------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Apache Commons / HttpComponents / Mail (used by some flows) -----------
-dontwarn org.apache.**
-dontwarn android.net.http.**
-keep class org.apache.http.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn java.awt.**

# Suppress warnings for optional/unused transitive classes.
-dontwarn javax.annotation.**
