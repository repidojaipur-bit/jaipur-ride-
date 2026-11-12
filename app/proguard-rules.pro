# R8 / ProGuard rules for Drive Partner release builds

-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Kotlinx Serialization (preserve DTO serializers & Companion objects)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.drivepartner.app.data.remote.dto.** { *; }
-keep,includedescriptorclasses class com.drivepartner.app.core.network.** { *; }
-keep,includedescriptorclasses class com.drivepartner.app.data.local.** { *; }

# Retrofit 2 & OkHttp
-keepattributes Signature, Exceptions, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
