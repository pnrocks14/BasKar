pluginManagement {
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

rootProject.name = "pratyahara"

include(":core")
include(":app")
// Test-only stand-in app for the emulator tests; never shipped.
include(":testapps:fakeinsta")
