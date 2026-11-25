plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "refixes"

includeBuild("build-logic")

include(
    "early",
    "plugin",
    "shared"
)
