# =======================================================================
# HotaruNet Production R8 & ProGuard Hardening Configuration
# =======================================================================

# 1. Debug Stripping & Line Number Obfuscation
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!MethodParameters
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# 2. Native JNI Bindings (Go Mobile & Hev Socks5 Tunnel)
-keep class go.** { *; }
-keep interface go.** { *; }
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-keep class com.v2ray.ang.service.TProxyService {
    *;
}
-keep class com.v2ray.ang.service.TProxyService$* {
    *;
}
-keepclasseswithmembers class * {
    native <methods>;
}

# 3. Android Components & Core Engine (Services, Activities, Receivers)
-keep class com.v2ray.ang.AngApplication { *; }
-keep class com.v2ray.ang.service.** { *; }
-keep class com.v2ray.ang.ui.** { *; }
-keep class com.v2ray.ang.receiver.** { *; }
-keep class com.v2ray.ang.contracts.** { *; }
-keep class com.v2ray.ang.core.** { *; }
-keep class com.v2ray.ang.handler.** { *; }

# 4. Data Transfer Objects (DTO) and MMKV / Gson Serialization
-keep class com.v2ray.ang.dto.** { *; }
-keepclassmembers class com.v2ray.ang.dto.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# 5. Core Cryptographic Native Bridge (allow R8 to obfuscate AppConfig & helpers)
-keep class com.v2ray.ang.util.HotaruCrypto {
    private static byte[] n0(int);
}

# 6. MMKV Storage
-keep class com.tencent.mmkv.** { *; }

# 7. OkHttp & Okio Network Stack
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 8. Kotlin Coroutines & Standard Library
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# 9. Android WorkManager
-keep class androidx.work.** { *; }

# 10. Jetpack Compose
-keep class androidx.compose.** { *; }

# 11. ZXing & CameraX
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }
