plugins {
    id("com.android.application")
}

android {
    namespace = "com.manufacttest.pebblereardisplay"
    compileSdk = 36
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "com.manufacttest.pebblereardisplay"
        minSdk = 28
        targetSdk = 36
        versionCode = 10
        versionName = "0.4.2"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
}
