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

val fingerprint: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}

tasks.register<JavaExec>("targetFingerprints") {
    val pluginClasses = project(":plugin").layout.buildDirectory.dir("classes/java/main")
    val mixins = file("src/main/resources/refixes.mixins.json")
    val output = layout.buildDirectory.file("fingerprints/target-fingerprints.tsv")
    dependsOn(":plugin:classes")
    inputs.file(mixins)
    inputs.dir(pluginClasses)
    inputs.files(fingerprint.runtimeClasspath)
    outputs.file(output)
    classpath = fingerprint.runtimeClasspath
    mainClass.set("cc.irori.refixes.early.TargetFingerprintWriter")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            mixins.absolutePath,
            pluginClasses.get().asFile.absolutePath,
            output.get().asFile.absolutePath)
    })
}
