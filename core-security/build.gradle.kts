plugins {
    alias(libs.plugins.giveaway.android.library)
    alias(libs.plugins.giveaway.hilt)
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // The Google Cloud project linked in Play Console (F-01); 0 until then, which turns the Standard API off.
        val cloudProject = providers.gradleProperty("giveaway.cloudProjectNumber").get()
        buildConfigField("long", "CLOUD_PROJECT_NUMBER", "${cloudProject}L")
    }
}

dependencies {
    implementation(libs.tink.android)
    implementation(libs.argon2kt)
    implementation(libs.play.integrity)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)
}
