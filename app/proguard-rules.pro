# =============================================================================
# MoneyTalk ProGuard Rules
# =============================================================================

# ---------- 디버그 & 스택 트레이스 ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*

# ---------- Hilt / Dagger ----------
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keepclasseswithmembers class * {
    @dagger.* <fields>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---------- Gson ----------
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
# Keep model classes used with Gson
-keep class com.sanha.moneytalk.core.firebase.PremiumConfig { *; }

# ---------- Firebase ----------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ---------- Google Generative AI (Gemini) ----------
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# ---------- Google AdMob ----------
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ---------- Google Drive API ----------
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.services.drive.**
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---------- Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---------- Kotlin Serialization (used by some libs) ----------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ---------- Compose ----------
-dontwarn androidx.compose.**

# ---------- DataStore ----------
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ---------- Lottie ----------
-dontwarn com.airbnb.lottie.**

# ---------- App Models (prevent stripping of data classes) ----------
-keep class com.sanha.moneytalk.core.db.entity.** { *; }
-keep class com.sanha.moneytalk.core.model.** { *; }
