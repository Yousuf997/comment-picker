pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "comment-picker"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core-designsystem")
include(":core-draw")
include(":core-data")
include(":core-instagram")
include(":core-media")
include(":core-security")
include(":feature-onboarding")
include(":feature-home")
include(":feature-create")
include(":feature-draw")
include(":feature-settings")
include(":benchmark")
