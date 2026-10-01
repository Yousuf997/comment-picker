import app.giveaway.buildlogic.libs
import app.giveaway.buildlogic.library
import com.android.build.api.dsl.CommonExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Compose screenshot tests with Roborazzi on Robolectric. Golden images live in `src/test/screenshots` and are
 * committed; `recordRoborazziDebug` updates them and `verifyRoborazziDebug` (run in CI) fails on differences.
 * Apply after the Android and Compose convention plugins.
 */
class AndroidScreenshotTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        extensions.getByType<CommonExtension>().testOptions.unitTests.isIncludeAndroidResources = true
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        dependencies {
            "testImplementation"(platform(libs.library("androidx-compose-bom")))
            "testImplementation"(libs.library("androidx-compose-ui-test-junit4"))
            "testImplementation"(libs.library("robolectric"))
            "testImplementation"(libs.library("roborazzi"))
            "testImplementation"(libs.library("roborazzi-compose"))
            "testImplementation"(libs.library("roborazzi-junit-rule"))
            "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
        }
    }
}
