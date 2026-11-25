plugins {
    id("cc.irori.refixes.build.java") apply false
}

/* Project Properties */
val modGroup    = project.property("mod_group")     as String
val modVersion  = project.property("mod_version")   as String
val hytaleServerVersion = project.property("hytale_server_range") as String

allprojects {
    group = modGroup
    version = modVersion
}

tasks {
    register<Delete>("cleanBundle") {
        delete(layout.buildDirectory.dir("bundle"))
        delete(layout.buildDirectory.dir("singlejar"))
    }

    // Hyinit single-jar: one archive containing both Main plugin classes and Mixin
    // configs, declared via `manifest-singlejar.json` (Main + Mixins). Drops in
    // `earlyplugins/` and Hyinit auto-discovers both the runtime plugin and Mixins.
    register<Copy>("copySingleJarManifest") {
        dependsOn("cleanBundle")
        inputs.property("modVersion", modVersion)
        inputs.property("hytaleVersion", hytaleServerVersion)
        from("bundle") {
            include("manifest-singlejar.json")
            rename("manifest-singlejar.json", "manifest.json")
        }
        into(layout.buildDirectory.dir("singlejar"))
        expand(
            "modVersion" to modVersion,
            "hytaleVersion" to hytaleServerVersion
        )
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    register<Jar>("singleJar") {
        val earlyShadow = project(":early").tasks.named<Jar>("shadowJar")
        val pluginShadow = project(":plugin").tasks.named<Jar>("shadowJar")
        dependsOn(
                "copySingleJarManifest",
                earlyShadow,
                pluginShadow,
                project(":early").tasks.named("jar"),
                project(":plugin").tasks.named("jar"))
        archiveFileName.set("${project.name}-${version}.jar")
        destinationDirectory.set(layout.buildDirectory.dir("bundle"))
        // Per-module manifest.json is dropped; the merged manifest from copySingleJarManifest
        // is added last and wins via DuplicatesStrategy.EXCLUDE (first-source-wins).
        from(layout.buildDirectory.dir("singlejar")) {
            include("manifest.json")
        }
        from(earlyShadow.flatMap { it.archiveFile }.map { zipTree(it) }) {
            exclude("manifest.json", "META-INF/MANIFEST.MF")
        }
        from(pluginShadow.flatMap { it.archiveFile }.map { zipTree(it) }) {
            exclude("manifest.json", "META-INF/MANIFEST.MF")
        }
        from(rootProject.file("LICENSE")) {
            into("META-INF")
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val hyinitJar = providers.gradleProperty("hyinitJar")
        val javaLauncher = project(":early").extensions.getByType<JavaToolchainService>()
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
        val versions = versionCatalogs.named("libs")
        val servers = listOf("hytale" to "fingerprintServer07", "hytale-pre" to "fingerprintServer08").map { (alias, name) ->
            versions.findVersion(alias).get().requiredVersion to project(":early").configurations.named(name)
        }
        inputs.files(hyinitJar.map { listOf(it) }.orElse(listOf()))
        servers.forEach { (_, server) -> inputs.files(server) }
        doLast {
            if (!hyinitJar.isPresent) {
                throw GradleException("Set -PhyinitJar=<path to Hyinit-X.X.X.jar>. The fingerprint writer needs Hyinit.")
            }
            val command = mutableListOf(
                javaLauncher.get().executablePath.asFile.path,
                "-jar",
                hyinitJar.get(),
                "--write-fingerprints",
                archiveFile.get().asFile.path)
            servers.forEach { (version, server) -> command += "--fingerprint-server=$version=${server.get().singleFile}" }
            providers.exec { commandLine(command) }.result.get()
        }
    }
}
