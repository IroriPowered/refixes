package cc.irori.refixes.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

abstract class HytaleLineCheck : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @get:Input
    abstract val range: Property<String>

    @TaskAction
    fun check() {
        val expected = range.get()
        val missing = mutableListOf<String>()
        classes.asFileTree.matching { include("**/*.class") }.visit {
            if (isDirectory) return@visit
            var found: String? = null
            ClassReader(file.readBytes()).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    if (descriptor != "Lcc/irori/hyinit/api/IfHytale;") return null
                    return object : AnnotationVisitor(Opcodes.ASM9) {
                        override fun visit(name: String?, value: Any?) {
                            if (name == "value") found = value as String
                        }
                    }
                }
            }, ClassReader.SKIP_CODE)
            if (found != expected) missing += "$relativePath has ${found?.let { "@IfHytale(\"$it\")" } ?: "no @IfHytale"}"
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Every class of this Hytale line needs @IfHytale(\"$expected\"):\n" + missing.joinToString("\n"))
        }
    }
}
