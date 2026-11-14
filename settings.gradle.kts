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

// ai.hey:tokens-compose (HeyTokens.kt, generated in hey-shared) is substituted by this included build.
includeBuild("shared/android")

include(
    ":app",
    ":core:designsystem",
    ":core:model",
    ":core:session",
    ":core:vehicle",
    ":feature:settings",
    ":feature:onboarding",
)
