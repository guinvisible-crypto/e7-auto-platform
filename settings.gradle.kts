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

rootProject.name = "e7-auto-platform-native"
include(":app")
include(":core")
include(":automation-accessibility")
include(":screenshot-manager")
include(":imagerecognition")
