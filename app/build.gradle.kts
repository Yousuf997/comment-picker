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
    implementation(projects.coreData)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}

// A Play upload (bundleRelease) must not ship placeholder sign-in settings or an unpinned login helper (spec: Release
// checklist). Development builds, including CI's assembleRelease, keep working before the F-01 setup exists.
val verifyReleaseConfig by tasks.registering {
    val igAppId = providers.gradleProperty("giveaway.igAppId")
    val authHost = providers.gradleProperty("giveaway.authHost")
    val pins = providers.gradleProperty("giveaway.authHostPins")
    doLast {
        val problems = buildList {
            if (igAppId.get().startsWith("REPLACE")) add("giveaway.igAppId is a placeholder")
            if (authHost.get().endsWith(".invalid")) add("giveaway.authHost is a placeholder")
            val pinCount = pins.get().split(",").count { it.isNotBlank() }
            if (pinCount < 2) add("giveaway.authHostPins needs a pin and a backup pin")
        }
        check(problems.isEmpty()) { "Release configuration incomplete:\n" + problems.joinToString("\n") { "  - $it" } }
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(verifyReleaseConfig) }
