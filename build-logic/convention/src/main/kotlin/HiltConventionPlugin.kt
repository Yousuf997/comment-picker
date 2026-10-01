import app.giveaway.buildlogic.libs
import app.giveaway.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Apply after an Android application or library plugin. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            "implementation"(libs.library("hilt-android"))
            "ksp"(libs.library("hilt-compiler"))
        }
    }
}
