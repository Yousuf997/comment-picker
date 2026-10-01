plugins {
    alias(libs.plugins.giveaway.android.library)
    alias(libs.plugins.giveaway.hilt)
}

dependencies {
    implementation(libs.tink.android)
    implementation(libs.argon2kt)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)
}
