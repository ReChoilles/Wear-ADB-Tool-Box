import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        google()
    }
}

rootProject.name = "WearADBToolBox"
