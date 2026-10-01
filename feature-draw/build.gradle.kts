plugins {
    alias(libs.plugins.giveaway.android.feature)
}

dependencies {
    implementation(projects.coreDraw)
    implementation(projects.coreData)
    implementation(projects.coreMedia)
    implementation(projects.coreSecurity)
}
