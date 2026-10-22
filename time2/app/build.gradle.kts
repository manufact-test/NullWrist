plugins { id("com.android.application") }

android {
    namespace = "com.manufacttest.nullwrist.time2"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.manufacttest.nullwrist.time2"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0-lab"
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
