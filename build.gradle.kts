plugins {
    id("cc.irori.refixes.build.java") apply false
}

/* Project Properties */
val modGroup    = project.property("mod_group")     as String
val modVersion  = project.property("mod_version")   as String
val hytaleServerVersion = project.property("hytale_server_version") as String

allprojects {
    group = modGroup
    version = modVersion
}

tasks {
    register<Delete>("cleanBundle") {
        delete(layout.buildDirectory.dir("bundle"))
        delete(layout.buildDirectory.dir("singlejar"))
    }

    register<Copy>("copyBundleManifest") {
        dependsOn("cleanBundle")
        inputs.property("version", modVersion)
        inputs.property("hytaleVersion", hytaleServerVersion)
        from("bundle") {
            include(
                "manifest.json",
                "LICENSE",
                "README.md"
            )
        }
        into(layout.buildDirectory.dir("bundle"))
        expand(
            "version" to modVersion,
            "hytaleVersion" to hytaleServerVersion
        )
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    register<Copy>("collectEarlyJar") {
        val shadowJarTask = project(":early").tasks.named<Jar>("shadowJar")
        dependsOn("copyBundleManifest", shadowJarTask, project(":early").tasks.named("jar"))
        from(shadowJarTask.flatMap { it.archiveFile })
        into(layout.buildDirectory.dir("bundle/earlyplugins"))
    }

    register<Copy>("collectMainJar") {
        val shadowJarTask = project(":plugin").tasks.named<Jar>("shadowJar")
        dependsOn("copyBundleManifest", shadowJarTask, project(":plugin").tasks.named("jar"))
        from(shadowJarTask.flatMap { it.archiveFile })
        into(layout.buildDirectory.dir("bundle/mods"))
    }

    register<Zip>("bundle") {
        dependsOn("collectEarlyJar", "collectMainJar")
        archiveFileName.set("${project.name}-bundle-${version}.zip")
        destinationDirectory.set(file("bundle"))
        from(layout.buildDirectory.dir("bundle"))
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
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
