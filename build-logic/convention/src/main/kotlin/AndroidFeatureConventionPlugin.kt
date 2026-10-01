import app.giveaway.buildlogic.libs
import app.giveaway.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A screen-group module: Android library + Compose + Hilt + the design system. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("giveaway.android.library")
        pluginManager.apply("giveaway.android.compose")
        pluginManager.apply("giveaway.hilt")
        // Typed navigation routes are @Serializable classes.
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            "implementation"(project(":core-designsystem"))
            "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
            "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
            "implementation"(libs.library("androidx-navigation-compose"))
            "implementation"(libs.library("hilt-navigation-compose"))
            "implementation"(libs.library("kotlinx-serialization-json"))
        }
    }
}
