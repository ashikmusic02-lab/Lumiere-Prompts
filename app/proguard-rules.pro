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

# Firebase Firestore, Auth & Messaging reflection safety
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}

# Google Mobile Ads (AdMob) & UMP
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.ads.**

# Firebase Messaging Service & Google Credential Manager
-keep class com.example.notifications.PromptLensMessagingService { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Coil Image Loader & Coroutines
-dontwarn coil.**
-dontwarn kotlinx.coroutines.**
-dontwarn okhttp3.**
-dontwarn okio.**
