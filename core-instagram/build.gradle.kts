plugins {
    alias(libs.plugins.giveaway.android.library)
    alias(libs.plugins.giveaway.hilt)
    alias(libs.plugins.kotlin.serialization)
}

fun quoted(property: String) = "\"${providers.gradleProperty(property).get()}\""

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "IG_APP_ID", quoted("giveaway.igAppId"))
        buildConfigField("String", "AUTH_HOST", quoted("giveaway.authHost"))
        buildConfigField("String", "AUTH_HOST_PINS", quoted("giveaway.authHostPins"))
    }
}

dependencies {
    implementation(projects.coreSecurity)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.androidx.browser)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
