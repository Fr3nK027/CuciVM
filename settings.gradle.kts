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
        maven("https://www.jitpack.io") // FreeReflection, used by the app-space engine
    }
}
rootProject.name = "CuciVM"
include(":app")

// App-space engine: NewBlackbox (Apache-2.0), vendored in blackbox/, log upload removed.
include(":Bcore", ":black-reflection", ":compiler")
project(":Bcore").projectDir = file("blackbox/Bcore")
project(":black-reflection").projectDir = file("blackbox/black-reflection")
project(":compiler").projectDir = file("blackbox/compiler")
