plugins {
    alias(libs.plugins.giveaway.android.feature)
    alias(libs.plugins.giveaway.android.screenshot)
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${providers.gradleProperty("giveaway.privacyPolicyUrl").get()}\"")
    }
}

dependencies {
    implementation(projects.coreData)
    implementation(projects.coreSecurity)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.kotlinx.coroutines.test)
}
