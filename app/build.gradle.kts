plugins {
    alias(libs.plugins.giveaway.android.application)
    alias(libs.plugins.giveaway.android.compose)
    alias(libs.plugins.giveaway.hilt)
    alias(libs.plugins.giveaway.android.screenshot)
}

android {
    namespace = "app.giveaway"
    defaultConfig {
        // Placeholder until the app name is decided (open decision 1). Must be final before the first Play upload.
        applicationId = "app.giveaway"
        versionCode = 1
        versionName = "0.1.0"
        // Host of the login helper; the App Link below is verified against its assetlinks.json.
        manifestPlaceholders["authHost"] = providers.gradleProperty("giveaway.authHost").get()
    }
}

dependencies {
    implementation(projects.coreDesignsystem)
    implementation(projects.featureOnboarding)
    implementation(projects.featureHome)
    implementation(projects.featureCreate)
    implementation(projects.featureDraw)
    implementation(projects.featureSettings)
    implementation(projects.coreInstagram)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
