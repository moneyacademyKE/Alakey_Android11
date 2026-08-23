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
        versionCode = 1
        versionName = "2.4.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation(libs.junit)
}
