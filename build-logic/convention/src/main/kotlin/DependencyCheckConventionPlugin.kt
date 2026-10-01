import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension

/**
 * OWASP dependency-check, applied per module. Gradle 9 forbids the aggregate task resolving other
 * projects' configurations, so each module scans only what it ships; the root project downloads the
 * vulnerability database once (`dependencyCheckUpdate`) and modules reuse it without updating.
 */
class DependencyCheckConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.owasp.dependencycheck")
        val isRoot = this == rootProject
        extensions.configure<DependencyCheckExtension> {
            failBuildOnCVSS.set(7.0f)
            formats.set(listOf("HTML", "SARIF"))
            autoUpdate.set(isRoot)
            nvd.apiKey.set(providers.environmentVariable("NVD_API_KEY"))
            analyzers.assemblyEnabled.set(false)
            analyzers.nodePackage.enabled.set(false)
            analyzers.nodeAudit.enabled.set(false)
            analyzers.ossIndex.enabled.set(false)
            if (!isRoot) {
                val shipped = if (plugins.hasPlugin("com.android.base")) "releaseRuntimeClasspath" else "runtimeClasspath"
                scanConfigurations.set(listOf(shipped))
            }
        }
    }
}
