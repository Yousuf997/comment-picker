plugins {
    alias(libs.plugins.giveaway.android.library)
    alias(libs.plugins.giveaway.hilt)
    alias(libs.plugins.room)
}

// Schemas are committed so every version change is reviewed and migrations can be tested (plan section 4).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.coreDraw)
    implementation(projects.coreSecurity)
    // api: AccountRepository and GiveawayRepository expose AuthToken, IgAccount and IgMedia.
    api(projects.coreInstagram)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
}
