plugins {
    id("cc.irori.refixes.build.java") apply false
}

/* Project Properties */
val modGroup    = project.property("mod_group")     as String
val modVersion  = project.property("mod_version")   as String

allprojects {
    group = modGroup
    version = modVersion
}

tasks {
    register<Delete>("cleanBundle") {
        delete(layout.buildDirectory.dir("bundle"))
    }

    register<Copy>("copyBundleManifest") {
        dependsOn("cleanBundle")
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
            "hytaleVersion" to libs.versions.hytale.get()
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
}
