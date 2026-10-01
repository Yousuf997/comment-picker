package app.giveaway.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = libs.versionInt("compileSdk")
    extension.defaultConfig.minSdk = libs.versionInt("minSdk")
    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    extension.lint.warningsAsErrors = true
    extension.lint.abortOnError = true
    extension.lint.lintConfig = isolated.rootProject.projectDirectory.file("lint.xml").asFile
    configureKotlin()
    configureDetekt()
    dependencies {
        "testImplementation"(libs.library("junit"))
    }
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
