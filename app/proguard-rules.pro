# ProGuard / R8 configuration for PromptLens (Release Readiness)

-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# Keep all @Keep annotated Firestore data models and no-arg constructors
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class com.example.model.** {
    public <init>();
    public <fields>;
    public <methods>;
}

# Firebase Firestore & Auth reflection safety
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}

# Google Mobile Ads (AdMob)
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-dontwarn com.google.android.gms.ads.**

# OneSignal Push Notifications
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**
-keepclassmembers class * implements com.onesignal.notifications.INotificationClickListener { *; }

# Coil Image Loader & Coroutines
-dontwarn coil.**
-dontwarn kotlinx.coroutines.**
-dontwarn okhttp3.**
-dontwarn okio.**
