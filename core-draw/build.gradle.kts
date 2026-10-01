plugins {
    alias(libs.plugins.giveaway.jvm.library)
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
