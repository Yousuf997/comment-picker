plugins {
    alias(libs.plugins.giveaway.android.library)
    alias(libs.plugins.giveaway.hilt)
}

dependencies {
    api(projects.coreDraw)
    implementation(projects.coreSecurity)
    implementation(libs.kotlinx.coroutines.core)
}
