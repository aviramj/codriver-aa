# Release builds keep minification off (see build.gradle.kts), so no custom
# rules are required. If you enable minify later, keep OkHttp's optional deps:
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
