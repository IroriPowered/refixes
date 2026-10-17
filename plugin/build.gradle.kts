plugins {
    id("cc.irori.refixes.build.java")
}

dependencies {
    compileOnly(project(":early"))

    compileOnly(libs.guava)
    compileOnly(libs.jspecify)
}

base {
    archivesName.set("refixes-plugin")
}

tasks {
    compileJava {
        dependsOn(":early:shadowJar")
    }
}
