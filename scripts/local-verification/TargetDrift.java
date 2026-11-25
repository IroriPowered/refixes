package local.refixes.drift;

import static cc.irori.refixes.early.MixinAnnotations.ACCESSOR;
import static cc.irori.refixes.early.MixinAnnotations.INVOKER;
import static cc.irori.refixes.early.MixinAnnotations.OVERWRITE;
import static cc.irori.refixes.early.MixinAnnotations.SHADOW;
import static cc.irori.refixes.early.MixinAnnotations.annotations;
import static cc.irori.refixes.early.MixinAnnotations.shadowName;
import static cc.irori.refixes.early.MixinAnnotations.value;

import cc.irori.refixes.early.MixinAnnotations;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public final class TargetDrift {
    private static final Pattern LAMBDA_NUMBER = Pattern.compile("(lambda\\$\\w+\\$)\\d+");

    enum Kind {
        INJECTOR("injector", true),
        OVERWRITE("overwrite", true),
        SHADOW("shadow", false),
        ACCESSOR("accessor", false),
        INVOKER("invoker", false);

        final String label;
        final boolean method;

        Kind(String label, boolean method) {
            this.label = label;
            this.method = method;
        }
    }

    record Dependency(Kind kind, String member, String name, String desc, boolean field, boolean lambdas) {}

    private TargetDrift() {}

    static Set<Dependency> dependencies(ClassNode mixin) {
        Set<Dependency> dependencies = new LinkedHashSet<>();
        for (MethodNode method : mixin.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                switch (annotation.desc) {
                    case OVERWRITE -> dependencies.add(new Dependency(
                            Kind.OVERWRITE, method.name + method.desc, method.name, method.desc, false, false));
                    case SHADOW -> {
                        String name = shadowName(method.name, annotation);
                        dependencies.add(
                                new Dependency(Kind.SHADOW, name + method.desc, name, method.desc, false, false));
                    }
                    case ACCESSOR -> {
                        MixinAnnotations.Target field = MixinAnnotations.accessor(method, annotation);
                        dependencies.add(new Dependency(
                                Kind.ACCESSOR,
                                field.name() + ":" + field.desc(),
                                field.name(),
                                field.desc(),
                                true,
                                false));
                    }
                    case INVOKER -> {
                        MixinAnnotations.Target invoked = MixinAnnotations.invoker(method, annotation);
                        dependencies.add(new Dependency(
                                Kind.INVOKER,
                                invoked.name() + invoked.desc(),
                                invoked.name(),
                                invoked.desc(),
                                false,
                                false));
                    }
                    default -> {
                        if (value(annotation, "method") instanceof List<?> selectors) {
                            for (Object selector : selectors) dependencies.add(injector(selector.toString()));
                        }
                    }
                }
            }
        }
        for (FieldNode field : mixin.fields) {
            for (AnnotationNode annotation : annotations(field.visibleAnnotations, field.invisibleAnnotations)) {
                if (!annotation.desc.equals(SHADOW)) continue;
                String name = shadowName(field.name, annotation);
                dependencies.add(new Dependency(Kind.SHADOW, name + ":" + field.desc, name, field.desc, true, false));
            }
        }
        return dependencies;
    }

    static Dependency injector(String selector) {
        MixinAnnotations.Selector parsed = MixinAnnotations.injector(selector);
        return new Dependency(Kind.INJECTOR, selector, parsed.name(), parsed.desc(), false, parsed.lambdas());
    }

    static List<String> resolve(ClassNode target, Dependency dependency) {
        List<String> fingerprints = new ArrayList<>();
        if (target == null) return fingerprints;
        if (dependency.field) {
            for (FieldNode field : target.fields) {
                if (field.name.equals(dependency.name) && field.desc.equals(dependency.desc)) {
                    fingerprints.add(field.name + ":" + field.desc);
                }
            }
            return fingerprints;
        }
        for (MethodNode method : selected(target, dependency)) {
            fingerprints.add(dependency.kind.method ? normalised(method) : method.name + method.desc);
        }
        fingerprints.sort(null);
        return fingerprints;
    }

    static List<MethodNode> selected(ClassNode target, Dependency dependency) {
        List<MethodNode> selected = new ArrayList<>();
        if (target == null || dependency.field) return selected;
        for (MethodNode method : target.methods) {
            if ((dependency.name == null || method.name.equals(dependency.name))
                    && (dependency.desc == null || method.desc.equals(dependency.desc))) {
                selected.add(method);
            }
        }
        if (dependency.lambdas && !selected.isEmpty()) {
            Pattern lambda = Pattern.compile("lambda\\$" + Pattern.quote(dependency.name) + "\\$\\d+");
            for (MethodNode method : target.methods) {
                if (lambda.matcher(method.name).matches()) selected.add(method);
            }
        }
        return selected;
    }

    static String normalised(MethodNode method) {
        Textifier text = new Textifier();
        TraceMethodVisitor visitor = new TraceMethodVisitor(text);
        method.instructions.accept(visitor);
        for (TryCatchBlockNode block : method.tryCatchBlocks) block.accept(visitor);
        StringWriter out = new StringWriter();
        text.print(new PrintWriter(out));
        String code = method.name + method.desc + "\n" + out;
        return LAMBDA_NUMBER.matcher(code).replaceAll("$1N");
    }

    static String classStatus(ClassNode before, ClassNode after) {
        if (after == null) return "missing";
        return before == null ? "added" : "ok";
    }

    static String status(List<String> before, List<String> after) {
        if (after.isEmpty()) return "missing";
        if (before.isEmpty()) return "added";
        return before.equals(after) ? "ok" : "changed";
    }

    static ClassNode serverClass(JarFile jar, String internalName) {
        try {
            return readClass(jar, internalName, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read " + internalName + " from " + jar.getName(), failure);
        }
    }

    static ClassNode readClass(JarFile jar, String internalName, int flags) throws IOException {
        JarEntry entry = jar.getJarEntry(internalName + ".class");
        if (entry == null) return null;
        try (InputStream input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, flags);
            return node;
        }
    }

    static String version(JarFile server) throws IOException {
        var manifest = server.getManifest();
        String version = manifest == null
                ? null
                : manifest.getMainAttributes().getValue(java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION);
        return version == null ? null : version.trim();
    }

}
