plugins {
    alias(libs.plugins.giveaway.android.feature)
    alias(libs.plugins.giveaway.android.screenshot)
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
