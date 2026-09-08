package cc.irori.refixes.build

plugins {
    java
    id("com.diffplug.spotless")
    id("com.gradleup.shadow")
}

val libs: VersionCatalog = versionCatalogs.named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

spotless {
    java {
        palantirJavaFormat()
    }
}

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release")
    maven("https://maven.hytale.com/pre-release")
}

dependencies {
    val localHytaleJar = (findProperty("hytaleServerJar") as String?)?.trim().orEmpty()
    if (localHytaleJar.isNotEmpty()) {
        compileOnly(files(localHytaleJar))
    } else {
        compileOnly(libs.findLibrary("hytale").get())
    }
}

tasks {
    processResources {
        val hytaleVersion = rootProject.property("hytale_server_version") as String

        inputs.property("version", version)
        inputs.property("hytaleVersion", hytaleVersion)
        filteringCharset = "UTF-8"

        filesMatching("manifest.json") {
            expand(
                "version" to version,
                "hytaleVersion" to hytaleVersion
            )
        }
    }

    compileJava {
        options.release.set(25)
        options.encoding = "UTF-8"
    }

    build {
        dependsOn(spotlessApply, shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
    }
}
