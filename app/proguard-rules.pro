# Proguard Rules for Su Yolu Android Application

# 1. Kotlin & Coroutines Protection
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# 2. AndroidX Startup & Lifecycle
-keep class androidx.startup.** { *; }
-keep interface androidx.startup.** { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.startup.**

# 3. Osmdroid (Harita Motoru ve Döşeme Önbelleği)
-keep class org.osmdroid.** { *; }
-keep interface org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# 4. Google Play Services Location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# 5. ZXing QR Code Engine
-keep class com.google.zxing.** { *; }
-keep interface com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**

# 6. Su Yolu Uygulama ve Veri Modelleri
-keep class com.turga.suyolu.SuYoluApplication { *; }
-keep class com.turga.suyolu.MainActivity { *; }
-keep class com.turga.suyolu.data.** { *; }
-keep class com.turga.suyolu.service.** { *; }

# 7. Hata Ayıklama ve Satır Numarası Koruma (Google Play Console Analizi İçin)
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
