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
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content { includeGroupByRegex("ai\\.pleos.*") }
        }
    }
}

rootProject.name = "hey-car"

include(
    ":app",
    ":core:designsystem",
    ":core:model",
    ":core:session",
    ":core:vehicle",
    ":feature:trip",
    ":feature:settings",
)
