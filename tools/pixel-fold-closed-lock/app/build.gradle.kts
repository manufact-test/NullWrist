plugins { id("com.android.application") }
android {
    namespace = "com.manufact.pixelfoldclosed"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.manufact.pixelfoldclosed"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0-beta1"
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.annotation:annotation:1.3.0")
}
