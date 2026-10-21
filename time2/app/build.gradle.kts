plugins { id("com.android.application") }

android {
    namespace = "com.manufacttest.nullwrist.time2"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.manufacttest.nullwrist.time2"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
}
