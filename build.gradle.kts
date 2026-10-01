// Puts every plugin on the build classpath once so build-logic can apply them by id.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dependency.check)
}

// Vulnerability scan of all resolved dependencies. Needs an NVD API key (NVD_API_KEY) to download the database in reasonable time.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "SARIF")
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
    }
    analyzers {
        assemblyEnabled = false
        nodeEnabled = false
        nodeAuditEnabled = false
        ossIndexEnabled = false
    }
}
