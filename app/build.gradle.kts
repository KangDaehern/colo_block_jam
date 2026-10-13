plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kang.colorblockjam"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kang.colorblockjam"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    testOptions {
        unitTests.all { it.maxHeapSize = "2g" }
    }
}

dependencies {
    // The game is a single custom View drawn on Canvas; no AndroidX runtime dependency is needed.
    testImplementation("junit:junit:4.13.2")
}
