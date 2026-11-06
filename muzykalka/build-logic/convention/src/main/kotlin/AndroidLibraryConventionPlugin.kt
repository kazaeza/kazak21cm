import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android library module (audio, data, ui). Kotlin is built into AGP 9: no kotlin-android plugin. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = ProjectConfig.COMPILE_SDK
            defaultConfig {
                minSdk = ProjectConfig.MIN_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = ProjectConfig.JAVA
                targetCompatibility = ProjectConfig.JAVA
            }
            testOptions { unitTests.isIncludeAndroidResources = true }
            lint {
                abortOnError = true
                checkDependencies = false
                warningsAsErrors = false
            }
        }
        configureKotlinCompile(warningsAsErrors = false)
        configureRobolectric()
        dependencies {
            add("testImplementation", libs.findLibrary("junit4").get())
        }
    }
}
