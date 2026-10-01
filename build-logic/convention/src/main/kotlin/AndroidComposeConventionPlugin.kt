import app.giveaway.buildlogic.libs
import app.giveaway.buildlogic.library
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Apply after an Android application or library plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.getByType<CommonExtension>().buildFeatures.compose = true
        dependencies {
            "implementation"(platform(libs.library("androidx-compose-bom")))
            "implementation"(libs.library("androidx-compose-ui"))
            "implementation"(libs.library("androidx-compose-material3"))
            "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
            "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
        }
    }
}
