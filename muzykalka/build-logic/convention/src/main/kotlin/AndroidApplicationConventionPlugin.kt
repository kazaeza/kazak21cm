import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = ProjectConfig.COMPILE_SDK
            defaultConfig {
                minSdk = ProjectConfig.MIN_SDK
                targetSdk = ProjectConfig.TARGET_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = ProjectConfig.JAVA
                targetCompatibility = ProjectConfig.JAVA
            }
            testOptions { unitTests.isIncludeAndroidResources = true }
            lint {
                abortOnError = true
                checkDependencies = true
                warningsAsErrors = false
            }
        }
        configureKotlinCompile(warningsAsErrors = false)
        configureRobolectric()
    }
}
