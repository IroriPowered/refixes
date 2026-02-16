dependencies {
    compileOnly(libs.hytale)

    implementation(libs.guava)
}

base {
    archivesName.set("refixes-plugin")
}

tasks {
    shadowJar {
        relocate("com.google", "cc.irori.refixes.lib.com.google")
        relocate("org.jspecify", "cc.irori.refixes.lib.org.jspecify")
    }
}
