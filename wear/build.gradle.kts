import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// H1: release signing — CI env vars, or the gitignored root keystore.properties
// for local builds. Absent both, assembleRelease fails loudly.
val releaseKeystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.example.alakey.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.alakey.wear"
        minSdk = 30
        targetSdk = 36
        // L10: versionCode 6 diverged from app's 8 at the same versionName.
        versionCode = 8
        versionName = "2.6.3"
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                System.getenv("KEYSTORE_FILE")
                    ?: releaseKeystore.getProperty("storeFile")
                    ?: "release-signing-not-configured.jks"
            )
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: releaseKeystore.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: releaseKeystore.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: releaseKeystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":tile-contract"))
    implementation(libs.androidx.wear.tiles)
    implementation(libs.play.services.wearable)
    implementation(libs.guava)
    implementation(libs.kotlinx.coroutines.android) // L10: was hardcoded outside the catalog
    testImplementation(libs.junit)
}
