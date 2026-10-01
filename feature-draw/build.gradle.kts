plugins {
    alias(libs.plugins.giveaway.android.feature)
    alias(libs.plugins.giveaway.android.screenshot)
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "VERIFIER_URL", "\"${providers.gradleProperty("giveaway.verifierUrl").get()}\"")
        // Instagram's Story share needs the Meta (Facebook) app ID, not the Instagram app ID (plan R18).
        buildConfigField("String", "META_APP_ID", "\"${providers.gradleProperty("giveaway.metaAppId").get()}\"")
    }
}

dependencies {
    implementation(projects.coreDraw)
    implementation(projects.coreData)
    implementation(projects.coreMedia)
    implementation(projects.coreSecurity)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.room.runtime)
}
