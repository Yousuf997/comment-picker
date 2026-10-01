plugins {
    alias(libs.plugins.giveaway.android.application)
    alias(libs.plugins.giveaway.android.compose)
    alias(libs.plugins.giveaway.hilt)
}

android {
    namespace = "app.giveaway"
    defaultConfig {
        // Placeholder until the app name is decided (open decision 1). Must be final before the first Play upload.
        applicationId = "app.giveaway"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.coreDesignsystem)
    implementation(projects.featureOnboarding)
    implementation(projects.featureHome)
    implementation(projects.featureCreate)
    implementation(projects.featureDraw)
    implementation(projects.featureSettings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}
