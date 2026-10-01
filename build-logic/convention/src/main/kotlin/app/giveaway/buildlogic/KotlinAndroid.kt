package app.giveaway.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Emulator used for device tests: `./gradlew <module>:pixel6Api34DebugAndroidTest`. */
internal const val MANAGED_DEVICE = "pixel6Api34"
private const val MANAGED_DEVICE_API = 34

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.compileSdk = libs.versionInt("compileSdk")
    extension.defaultConfig.minSdk = libs.versionInt("minSdk")
    extension.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    extension.lint.warningsAsErrors = true
    extension.lint.abortOnError = true
    extension.lint.lintConfig = isolated.rootProject.projectDirectory.file("lint.xml").asFile
    // Only modules with device tests get the emulator; otherwise each module would boot its own and exhaust CI memory.
    if (layout.projectDirectory.dir("src/androidTest").asFile.exists()) {
        extension.testOptions.managedDevices.allDevices.register(MANAGED_DEVICE, ManagedVirtualDevice::class.java) {
            device = "Pixel 6"
            sdkVersion = MANAGED_DEVICE_API
            systemImageSource = "google"
            testedAbi = "x86_64"
        }
    }
    configureKotlin()
    configureDetekt()
    // Robolectric reaches into FileDescriptor internals, which JDK 17+ hides by default.
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
    }
    dependencies {
        "testImplementation"(libs.library("junit"))
        "androidTestImplementation"(libs.library("junit"))
        "androidTestImplementation"(libs.library("androidx-test-runner"))
        "androidTestImplementation"(libs.library("androidx-test-ext-junit"))
    }
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
