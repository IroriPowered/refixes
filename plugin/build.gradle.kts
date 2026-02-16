dependencies {
    compileOnly(project(":early"))

    compileOnly(libs.hytale)

    implementation(libs.guava)
}

base {
    archivesName.set("refixes-plugin")
}

tasks {
    compileJava {
        dependsOn(":early:shadowJar")
    }

    shadowJar {
        relocate("com.google", "cc.irori.refixes.lib.com.google") {
            exclude("com.google.common.flogger.*")
        }
        relocate("org.jspecify", "cc.irori.refixes.lib.org.jspecify")
    }
}
