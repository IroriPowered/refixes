plugins {
    id("cc.irori.refixes.build.java")
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras)
}

base {
    archivesName.set("refixes-early")
}
