# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keep class com.alicom.fusion.auth.** { *; }
-keep class com.alicom.** { *; }
-keep class com.ali.auth.** { *; }
-keep class com.mobile.auth.gatewayauth.** { *; }
-dontwarn com.alicom.**
-dontwarn com.ali.auth.**
-dontwarn com.mobile.auth.gatewayauth.**
