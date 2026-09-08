plugins {
    id("cc.irori.refixes.build.java")
}

repositories {
    maven("https://cursemaven.com")
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras)
}

base {
    archivesName.set("refixes-early")
}
