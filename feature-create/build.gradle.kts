plugins {
    alias(libs.plugins.giveaway.android.feature)
    alias(libs.plugins.giveaway.android.screenshot)
}

dependencies {
    implementation(projects.coreDraw)
    implementation(projects.coreData)
    implementation(projects.coreInstagram)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.room.runtime)
}
