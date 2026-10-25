plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.orangrepiano.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orangrepiano.launcher"
        minSdk = 28
        targetSdk = 36
        versionCode = 105
        versionName = "1.1.3"
    }

    // Never commit the private release key. GitHub Actions passes it through
    // protected repository secrets; without all four values only debug is built.
    val productionKeyFile = System.getenv("PIANO_KEYSTORE_PATH")
    val productionStorePassword = System.getenv("PIANO_STORE_PASSWORD")
    val productionAlias = System.getenv("PIANO_KEY_ALIAS")
    val productionKeyPassword = System.getenv("PIANO_KEY_PASSWORD")
    val releaseKeyAvailable = listOf(
        productionKeyFile, productionStorePassword,
        productionAlias, productionKeyPassword
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (releaseKeyAvailable) {
            create("production") {
                storeFile = file(productionKeyFile!!)
                storePassword = productionStorePassword!!
                keyAlias = productionAlias!!
                keyPassword = productionKeyPassword!!
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-rc1"
        }
        getByName("release") {
            isMinifyEnabled = false // Preserve the tested implementation for first release.
            isDebuggable = false
            if (releaseKeyAvailable) {
                signingConfig = signingConfigs.getByName("production")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
}
