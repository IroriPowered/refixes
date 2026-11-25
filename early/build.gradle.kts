plugins {
    id("cc.irori.refixes.build.java")
}

val hyinitApi = rootProject.files("libs/hyinit-api-0.5.0.jar")

dependencies {
    implementation(project(":shared"))
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras)
    compileOnly(hyinitApi)
}

base {
    archivesName.set("refixes-early")
}

val hytale07 = extra["hytale07"]!!
val hytale08 = extra["hytale08"]!!

val v07: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
}

val v08: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
}

val shared08: SourceSet by sourceSets.creating {
    java.setSrcDirs(sourceSets.main.get().java.srcDirs)
}

dependencies {
    for (set in listOf(v07, v08, shared08)) {
        set.compileOnlyConfigurationName(libs.mixin)
        set.compileOnlyConfigurationName(libs.mixinextras)
        set.compileOnlyConfigurationName(hyinitApi)
        set.compileOnlyConfigurationName(project(":shared"))
    }
    v07.compileOnlyConfigurationName(hytale07)
    v08.compileOnlyConfigurationName(hytale08)
    shared08.compileOnlyConfigurationName(hytale08)
}

tasks.named("classes") { dependsOn(shared08.classesTaskName) }

val hytaleLineChecks = listOf(v07, v08).map { it to (rootProject.property("hytale_line_${it.name}") as String) }.map { (set, range) ->
    tasks.register<cc.irori.refixes.build.HytaleLineCheck>("check${set.name.replaceFirstChar { it.uppercase() }}HytaleLine") {
        classes.from(set.output.classesDirs)
        this.range.set(range)
    }
}

tasks.shadowJar {
    dependsOn(hytaleLineChecks)
    from(v07.output, v08.output)
}

val fingerprintServer07: Configuration by configurations.creating
val fingerprintServer08: Configuration by configurations.creating

dependencies {
    fingerprintServer07(libs.hytale) { isTransitive = false }
    fingerprintServer08(libs.hytale.pre) { isTransitive = false }
}
