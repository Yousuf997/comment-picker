package app.giveaway.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionInt(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/** `:core-data` -> `app.giveaway.core.data` */
internal fun Project.defaultNamespace(): String =
    "app.giveaway." + path.removePrefix(":").replace(':', '.').replace('-', '.')
