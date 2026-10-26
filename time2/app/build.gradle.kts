plugins { id("com.android.application") }

android {
    namespace = "com.manufacttest.nullwrist.time2"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.manufacttest.nullwrist.time2"
        minSdk = 35
        targetSdk = 36
        versionCode = 10
        versionName = "0.8.1-spi-guardian-lab"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
