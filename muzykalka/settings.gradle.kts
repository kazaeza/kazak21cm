pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "muzykalka"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Module graph is fixed by docs/ARCHITECTURE.md. Do not add modules without updating it.
include(":app")
include(":ui")
include(":audio")
include(":data")
include(":core:music")
include(":core:srs")
include(":core:dsp")
include(":core:game")
