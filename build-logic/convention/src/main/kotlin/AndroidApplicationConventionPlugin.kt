import app.giveaway.buildlogic.configureAndroidCommon
import app.giveaway.buildlogic.enforceModuleRules
import app.giveaway.buildlogic.libs
import app.giveaway.buildlogic.versionInt
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = libs.versionInt("targetSdk")
            buildTypes.getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }
        pluginManager.apply("giveaway.dependency.check")
        enforceModuleRules()
    }
}
