# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- kotlinx-serialization (Phase 5: BoostReport / BoostActionOutcome persistence) ---
# Standard keep rules from the kotlinx.serialization README. They preserve the
# generated serializers and the @SerialName discriminators used by the sealed
# BoostActionOutcome hierarchy when the JSON reports are decoded from DataStore.
# (minify is currently disabled for this app; the rules keep R8 builds safe if it is enabled.)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.nexus.gamebooster.**$$serializer { *; }
-keepclassmembers class com.nexus.gamebooster.** {
    *** Companion;
}
-keepclasseswithmembers class com.nexus.gamebooster.** {
    kotlinx.serialization.KSerializer serializer(...);
}
