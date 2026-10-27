import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is read from keystore.properties (gitignored) when present, so
// no signing secrets live in the repo. Create it with ./make-upload-key.sh.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

// Optional default API keys, baked into the build from secrets.properties
// (gitignored) so a fresh install can come pre-filled without committing a
// secret. Absent file -> empty defaults, and the user just types the key once.
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) FileInputStream(secretsFile).use { load(it) }
}
fun secret(key: String): String = (secrets.getProperty(key) ?: "").trim().replace("\\", "").replace("\"", "")

android {
    namespace = "com.aviramj.codriver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aviramj.codriver"
        minSdk = 29          // Android 10; Android Auto dev mode lives under app settings here
        targetSdk = 36       // Google Play requires API 36 for new uploads (since Aug 31, 2026)
        // CI sets VERSION_CODE (auto-incrementing); local builds use the fallback.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 3
        versionName = System.getenv("VERSION_NAME") ?: "1.2"

        buildConfigField("String", "DEFAULT_OPENAI_API_KEY", "\"${secret("OPENAI_API_KEY")}\"")
        buildConfigField("String", "DEFAULT_ANTHROPIC_API_KEY", "\"${secret("ANTHROPIC_API_KEY")}\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Android for Cars App Library (Android Auto templates + car microphone)
    implementation("androidx.car.app:app:1.7.0")

    // Networking + async
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Encrypted storage for the API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
