plugins {
    alias(libs.plugins.giveaway.jvm.library)
}

dependencies {
    // Tests only: reading and writing the published test vectors. The engine itself has no dependencies.
    testImplementation(libs.kotlinx.serialization.json)
}

// The draw engine must stay fully covered (spec: Testing).
kover {
    reports {
        verify {
            rule {
                minBound(100)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("regenerateVectors", providers.gradleProperty("regenerateVectors").getOrElse("false"))
}
