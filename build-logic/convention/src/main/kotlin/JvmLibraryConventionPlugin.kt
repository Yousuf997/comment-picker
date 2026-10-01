import app.giveaway.buildlogic.configureDetekt
import app.giveaway.buildlogic.configureKotlin
import app.giveaway.buildlogic.enforceModuleRules
import app.giveaway.buildlogic.libs
import app.giveaway.buildlogic.library
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin/JVM module with no Android dependencies. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        configureDetekt()
        pluginManager.apply("org.jetbrains.kotlinx.kover")
        dependencies {
            "testImplementation"(libs.library("junit"))
        }
        pluginManager.apply("giveaway.dependency.check")
        enforceModuleRules()
    }
}
