plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.shortsgesturecontrol"
    compileSdk = 35
    buildToolsVersion = "36.0.0"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.shortsgesturecontrol"
        minSdk = 26
        targetSdk = 35
        // Keep the v5 whole-body renderer, but use a higher code so Android
        // accepts this rollback over the newer installed builds.
        versionCode = 19
        versionName = "19.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
