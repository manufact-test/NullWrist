plugins {
    id("com.android.application")
}
android {
    namespace = "com.manufact.pixelouteronly"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.manufact.pixelouteronly"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-beta1"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
}
