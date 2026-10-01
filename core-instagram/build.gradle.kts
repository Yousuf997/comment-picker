plugins {
    alias(libs.plugins.giveaway.android.library)
    alias(libs.plugins.giveaway.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.coreSecurity)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
