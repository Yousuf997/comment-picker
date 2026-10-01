plugins {
    alias(libs.plugins.giveaway.android.feature)
}

dependencies {
    implementation(projects.coreInstagram)
    implementation(projects.coreSecurity)
    implementation(projects.coreData)
}
