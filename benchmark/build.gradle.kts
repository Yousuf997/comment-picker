// Macrobenchmarks and the Baseline Profile generator (plan H-06). They need a device or emulator:
//   ./gradlew :app:generateBaselineProfile            writes app/src/release/generated/baselineProfiles
//   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.giveaway.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        // Macrobenchmark measures startup and frames reliably from Android 9.
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
