import app.giveaway.buildlogic.configureAndroidCommon
import app.giveaway.buildlogic.defaultNamespace
import app.giveaway.buildlogic.enforceModuleRules
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            if (namespace == null) namespace = defaultNamespace()
        }
        pluginManager.apply("giveaway.dependency.check")
        enforceModuleRules()
    }
}
