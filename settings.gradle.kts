// https://developer.android.com/build#settings-file
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral() {
            content {
                includeGroupByRegex("(?!com\\.github\\.).*")
            }
        }
        maven(url = "https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
                includeGroupByRegex("io\\.github\\..*")
            }
        }
    }
}

rootProject.name = "CloudStream"
include(":app", ":library", ":docs")
