package app.giveaway.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.withType

/**
 * Enforces the module dependency rules from the build plan:
 * - feature modules never depend on each other
 * - core modules never depend on feature modules
 * - nothing depends on :app
 * - :core-draw stays pure Kotlin with no project dependencies
 */
internal fun Project.enforceModuleRules() {
    afterEvaluate {
        val self = path
        val targets = configurations
            .flatMap { it.dependencies.withType<ProjectDependency>() }
            .map { it.path }
            .filter { it != self }
            .toSortedSet()

        val violations = targets.mapNotNull { target ->
            when {
                target == ":app" -> "$self must not depend on :app"
                self == ":core-draw" -> ":core-draw must have no project dependencies (found $target)"
                self.isFeature() && target.isFeature() -> "feature modules must not depend on each other ($self -> $target)"
                self.isCore() && target.isFeature() -> "core modules must not depend on feature modules ($self -> $target)"
                else -> null
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Module dependency rules broken:\n" + violations.joinToString("\n") { "  - $it" })
        }
    }
}

private fun String.isFeature() = startsWith(":feature-")
private fun String.isCore() = startsWith(":core-")
