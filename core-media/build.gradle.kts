plugins {
    alias(libs.plugins.giveaway.android.library)
    alias(libs.plugins.giveaway.hilt)
}

dependencies {
    api(projects.coreDraw)

    testImplementation(libs.junit)
}
