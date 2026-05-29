plugins {
    id("cc.irori.refixes.build.java")
}

dependencies {
    compileOnly(project(":early"))

    compileOnly(libs.hytale)

    compileOnly(libs.guava)
}

base {
    archivesName.set("refixes-plugin")
}

tasks {
    compileJava {
        dependsOn(":early:shadowJar")
    }
}
