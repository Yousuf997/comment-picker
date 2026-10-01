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
    implementation(projects.coreInstagram)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
}
