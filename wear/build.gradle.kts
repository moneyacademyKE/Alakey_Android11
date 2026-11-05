plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    buildTypes {
        release {
            isMinifyEnabled = false
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
