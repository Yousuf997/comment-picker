import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.giveaway.android.application)
    alias(libs.plugins.giveaway.android.compose)
    alias(libs.plugins.giveaway.hilt)
    alias(libs.plugins.giveaway.android.screenshot)
    alias(libs.plugins.baselineprofile)
}

// Crash reporting (spec: Tech stack) needs the Firebase project from the F-01 setup. Until its google-services.json
// is in this folder the plugins stay off; the SDK is present but never starts, so nothing is sent.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
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
    buildTypes {
        // Crash reports from release builds only (spec: Crashlytics disabled in debug).
        getByName("debug") { manifestPlaceholders["crashReports"] = "false" }
        getByName("release") { manifestPlaceholders["crashReports"] = "true" }
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.process)
    // Installs the Baseline Profile on devices without Play's cloud profiles (H-06: cold start under 2 s).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.benchmark)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    constraints {
        // Firebase pulls in DataStore 1.0, which bundles protobuf-javalite 3.10 (CVE-2024-7254, CVE-2022-3171).
        implementation(libs.androidx.datastore.preferences.core)
    }

    testImplementation(projects.coreSecurity)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.room.runtime)
    kspTest(libs.hilt.compiler)
}

// A Play upload (bundleRelease) must not ship placeholder sign-in settings or an unpinned login helper (spec: Release
// checklist). Development builds, including CI's assembleRelease, keep working before the F-01 setup exists.
val verifyReleaseConfig by tasks.registering {
    val igAppId = providers.gradleProperty("giveaway.igAppId")
    val metaAppId = providers.gradleProperty("giveaway.metaAppId")
    val authHost = providers.gradleProperty("giveaway.authHost")
    val pins = providers.gradleProperty("giveaway.authHostPins")
    val privacyPolicy = providers.gradleProperty("giveaway.privacyPolicyUrl")
    val verifier = providers.gradleProperty("giveaway.verifierUrl")
    val cloudProject = providers.gradleProperty("giveaway.cloudProjectNumber")
    doLast {
        val problems = buildList {
            if (igAppId.get().startsWith("REPLACE")) add("giveaway.igAppId is a placeholder")
            if (metaAppId.get().startsWith("REPLACE")) add("giveaway.metaAppId is a placeholder")
            if (authHost.get().endsWith(".invalid")) add("giveaway.authHost is a placeholder")
            if (privacyPolicy.get().contains(".invalid")) add("giveaway.privacyPolicyUrl is a placeholder")
            if (verifier.get().contains(".invalid")) add("giveaway.verifierUrl is a placeholder")
            if (cloudProject.get() == "0") add("giveaway.cloudProjectNumber is not set (Play Integrity)")
            val pinCount = pins.get().split(",").count { it.isNotBlank() }
            if (pinCount < 2) add("giveaway.authHostPins needs a pin and a backup pin")
        }
        check(problems.isEmpty()) { "Release configuration incomplete:\n" + problems.joinToString("\n") { "  - $it" } }
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(verifyReleaseConfig) }

// H-02: the release APK carries no secrets, is obfuscated by R8, and stays well under the 25 MB download limit (the
// universal APK holds every ABI; Play delivers one). CI runs this after assembleRelease.
val checkReleaseApk by tasks.registering {
    dependsOn("assembleRelease")
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
    val maxBytes = 25L * 1024 * 1024
    // Things that must never ship: the Meta app secret and its OAuth parameter, private keys, service account keys.
    val forbidden = listOf("client_secret", "IG_APP_SECRET", "BEGIN PRIVATE KEY", "BEGIN RSA PRIVATE KEY", "private_key_id")
    doLast {
        val apk = apkDir.get().asFile.listFiles { file -> file.extension == "apk" }.orEmpty().single()
        check(apk.length() <= maxBytes) { "${apk.name} is ${apk.length() / 1024 / 1024} MB, over 25 MB" }
        check(mapping.get().asFile.isFile) { "No R8 mapping: release builds must be minified and obfuscated" }
        val found = ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.endsWith(".dex") || it.name == "resources.arsc" || it.name.startsWith("assets/") }
                .flatMap { entry ->
                    val text = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                    forbidden.filter { text.contains(it) }.map { "${entry.name}: $it" }
                }
                .toList()
        }
        check(found.isEmpty()) { "Secrets in the release APK:\n" + found.joinToString("\n") }
    }
}
