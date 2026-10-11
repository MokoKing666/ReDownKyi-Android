# Re：哔哩下载姬 —— R8 规则
#
# 当前 release 未开启混淆（isMinifyEnabled = false），此文件为后续启用预留。

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.moko.downkyi.re.**$$serializer { *; }
-keepclassmembers class com.moko.downkyi.re.** {
    *** Companion;
}
-keepclasseswithmembers class com.moko.downkyi.re.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
