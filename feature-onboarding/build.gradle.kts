plugins {
    alias(libs.plugins.giveaway.android.feature)
    alias(libs.plugins.giveaway.android.screenshot)
}

dependencies {
    implementation(projects.coreInstagram)
    implementation(projects.coreSecurity)
    implementation(projects.coreData)
    implementation(libs.androidx.browser)

    testImplementation(libs.kotlinx.coroutines.test)
}
