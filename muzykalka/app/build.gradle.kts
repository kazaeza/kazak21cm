plugins {
    alias(libs.plugins.muzykalka.android.application)
    alias(libs.plugins.muzykalka.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** Monotonic versionCode = number of commits on HEAD (CI must check out with fetch-depth: 0). */
val commitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)

/** Bump by hand at each milestone from docs/ROADMAP.md (0.1, 0.2, 0.3). */
val versionBase = "0.0"

android {
    namespace = "com.kazaeza.muzykalka"

    defaultConfig {
        applicationId = "com.kazaeza.muzykalka"
        versionCode = commitCount
        versionName = "$versionBase.$commitCount"
    }

    signingConfigs {
        create("sideload") {
            storeFile = rootProject.file("signing/sideload.jks")
            storePassword = "muzykalka"
            keyAlias = "muzykalka"
            keyPassword = "muzykalka"
        }
    }

    buildTypes {
        debug {
            // Separate app id: a dev build never touches the real library on the phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            // R8 is off until ticket R-01 adds keep rules + a startup test for the minified build.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
}

dependencies {
    implementation(projects.core.music)
    implementation(projects.core.srs)
    implementation(projects.core.dsp)
    implementation(projects.core.game)
    implementation(projects.audio)
    implementation(projects.data)
    implementation(projects.ui)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
