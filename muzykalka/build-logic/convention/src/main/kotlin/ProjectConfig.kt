import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Platform constants shared by every module. Change here only. */
object ProjectConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    /** Android 12. The only real device is a Pixel 10 Pro; 31 keeps modern audio APIs without compat shims. */
    const val MIN_SDK = 31
    val JAVA = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
    const val NAMESPACE_ROOT = "com.kazaeza.muzykalka"
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureKotlinCompile(warningsAsErrors: Boolean) {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(ProjectConfig.JVM_TARGET)
            allWarningsAsErrors.set(warningsAsErrors)
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }
}

/**
 * Robolectric downloads android-all jars itself (not through Gradle). Default = Maven Central;
 * override per machine with `muzykalka.robolectricRepoUrl=...` in ~/.gradle/gradle.properties
 * (the cloud sandbox uses Google's Maven Central mirror because repo1 rate-limits it).
 */
internal fun Project.configureRobolectric() {
    val repo = providers.gradleProperty("muzykalka.robolectricRepoUrl").orElse("https://repo1.maven.org/maven2")
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", repo.get())
        systemProperty("robolectric.dependency.repo.id", "muzykalka-robolectric")
        maxHeapSize = "2g"
        // Robolectric (SDK 36 images) reflects into FileDescriptor internals on JDK 17+.
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
    }
}
