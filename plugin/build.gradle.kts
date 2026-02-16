dependencies {
    compileOnly(libs.hytale)

    implementation(libs.guava)
}

base {
    archivesName.set("refixes-plugin")
}

tasks {
    shadowJar {
        relocate("com.google.common", "cc.irori.refixes.lib.com.google.common")
    }
}
