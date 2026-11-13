plugins {
    id("cc.irori.refixes.build.java")
}

dependencies {
    compileOnly(project(":early"))
}

base {
    archivesName.set("refixes-plugin")
}

tasks {
    compileJava {
        dependsOn(":early:shadowJar")
    }
}
