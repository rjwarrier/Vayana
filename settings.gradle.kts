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

rootProject.name = "vayana"

include(":app")

include(":core:designsystem")
include(":core:resources")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:filesystem")

include(":feature:library")
include(":feature:reader")
include(":feature:notes")
include(":feature:statistics")
include(":feature:search")
include(":feature:settings")
include(":feature:onboarding")

include(":reader:engine-api")
include(":reader:engine-web")

include(":format:epub")
include(":format:convert")

include(":dictionary:api")
include(":dictionary:stardict")
