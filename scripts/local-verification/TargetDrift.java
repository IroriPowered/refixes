package local.refixes.drift;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Compares the server members that mixins depend on between a baseline and a current server JAR.
 * Writes one TSV row per mixin, target class and dependency with the status ok, changed, missing or added.
 */
public final class TargetDrift {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final Pattern QUANTIFIER = Pattern.compile("(?:\\*|\\+|\\{\\d*(?:,\\d*)?})$");
    private static final Pattern LAMBDA_NUMBER = Pattern.compile("(lambda\\$\\w+\\$)\\d+");

    private enum Kind {
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

    /** A member of the target class: a method selector for injectors, otherwise name and descriptor. */
    private record Dependency(Kind kind, String member, String name, String desc, boolean field, boolean lambdas) {}

    private TargetDrift() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 5) {
            throw new IllegalArgumentException(
                    "Expected: <refixes.jar> <baseline-server.jar> <server.jar> <result.tsv> <mixin class>...");
        }
        List<String> rows = new ArrayList<>();
        rows.add(String.join("\t", "mixin", "target", "kind", "member", "status"));
        try (JarFile refixes = new JarFile(args[0]);
                JarFile baseline = new JarFile(args[1]);
                JarFile current = new JarFile(args[2])) {
            Map<String, ClassNode> baselineClasses = new HashMap<>();
            Map<String, ClassNode> currentClasses = new HashMap<>();
            for (String mixinName : Arrays.asList(args).subList(4, args.length)) {
                ClassNode mixin = readClass(refixes, mixinName.replace('.', '/'), ClassReader.SKIP_CODE);
                if (mixin == null) throw new IllegalStateException("Mixin class not in Refixes JAR: " + mixinName);
                String shortName = mixinName.substring(mixinName.lastIndexOf('.') + 1);
                List<String> targets = targets(mixin);
                if (targets.isEmpty()) throw new IllegalStateException("No @Mixin targets in " + mixinName);
                Set<Dependency> dependencies = dependencies(mixin);
                for (String target : targets) {
                    ClassNode before = baselineClasses.computeIfAbsent(target, name -> serverClass(baseline, name));
                    ClassNode after = currentClasses.computeIfAbsent(target, name -> serverClass(current, name));
                    String targetName = target.replace('/', '.');
                    rows.add(row(shortName, targetName, "class", targetName, classStatus(before, after)));
                    if (after == null) continue;
                    for (Dependency dependency : dependencies) {
                        String status = status(resolve(before, dependency), resolve(after, dependency));
                        rows.add(row(shortName, targetName, dependency.kind.label, dependency.member, status));
                    }
                }
            }
        }
        Files.write(Path.of(args[3]), rows);
    }

    private static String row(String mixin, String target, String kind, String member, String status) {
        return String.join("\t", mixin, target, kind, member, status);
    }

    private static List<String> targets(ClassNode mixin) {
        List<String> targets = new ArrayList<>();
        for (AnnotationNode annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
            if (!annotation.desc.equals(MIXIN)) continue;
            if (value(annotation, "value") instanceof List<?> types) {
                for (Object type : types) targets.add(((Type) type).getInternalName());
            }
            if (value(annotation, "targets") instanceof List<?> names) {
                for (Object name : names) targets.add(name.toString().replace('.', '/'));
            }
        }
        return targets;
    }

    private static Set<Dependency> dependencies(ClassNode mixin) {
        Set<Dependency> dependencies = new LinkedHashSet<>();
        for (MethodNode method : mixin.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                switch (annotation.desc) {
                    case OVERWRITE -> dependencies.add(new Dependency(
                            Kind.OVERWRITE, method.name + method.desc, method.name, method.desc, false, false));
                    case SHADOW -> {
                        String name = stripPrefix(method.name, annotation);
                        dependencies.add(
                                new Dependency(Kind.SHADOW, name + method.desc, name, method.desc, false, false));
                    }
                    case ACCESSOR -> dependencies.add(accessor(method, annotation));
                    case INVOKER -> dependencies.add(invoker(method, annotation));
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
                String name = stripPrefix(field.name, annotation);
                dependencies.add(new Dependency(Kind.SHADOW, name + ":" + field.desc, name, field.desc, true, false));
            }
        }
        return dependencies;
    }

    /** Parses a Mixin target selector such as {@code Lowner;name+(desc) -> Lambda;sam()V}. */
    private static Dependency injector(String selector) {
        int arrow = selector.indexOf("->");
        String method = (arrow >= 0 ? selector.substring(0, arrow) : selector).trim();
        int paren = method.indexOf('(');
        String name = paren >= 0 ? method.substring(0, paren) : method;
        String desc = paren >= 0 ? method.substring(paren) : null;
        name = QUANTIFIER.matcher(name.substring(name.indexOf(';') + 1)).replaceFirst("");
        return new Dependency(Kind.INJECTOR, selector, name.isEmpty() ? null : name, desc, false, arrow >= 0);
    }

    private static Dependency accessor(MethodNode method, AnnotationNode annotation) {
        Type type = Type.getMethodType(method.desc);
        String desc = type.getReturnType() == Type.VOID_TYPE
                ? type.getArgumentTypes()[0].getDescriptor()
                : type.getReturnType().getDescriptor();
        String name = explicitName(annotation);
        if (name == null) name = inferredName(method.name, "get", "is", "set");
        return new Dependency(Kind.ACCESSOR, name + ":" + desc, name, desc, true, false);
    }

    private static Dependency invoker(MethodNode method, AnnotationNode annotation) {
        String name = explicitName(annotation);
        if (name == null) name = inferredName(method.name, "call", "invoke");
        String desc = name.equals("<init>")
                ? Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(method.desc))
                : method.desc;
        return new Dependency(Kind.INVOKER, name + desc, name, desc, false, false);
    }

    private static String explicitName(AnnotationNode annotation) {
        Object name = value(annotation, "value");
        return name == null || name.toString().isEmpty() ? null : name.toString();
    }

    private static String inferredName(String methodName, String... prefixes) {
        for (String prefix : prefixes) {
            if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
                String name = methodName.substring(prefix.length());
                return Character.toLowerCase(name.charAt(0)) + name.substring(1);
            }
        }
        throw new IllegalStateException("Cannot infer the target member of " + methodName);
    }

    private static String stripPrefix(String name, AnnotationNode shadow) {
        Object prefix = value(shadow, "prefix");
        String text = prefix == null ? "shadow$" : prefix.toString();
        return name.startsWith(text) ? name.substring(text.length()) : name;
    }

    /** Fingerprints of the members a dependency selects: normalised bytecode for methods patched in place. */
    private static List<String> resolve(ClassNode target, Dependency dependency) {
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
        List<MethodNode> selected = new ArrayList<>();
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
        for (MethodNode method : selected) {
            fingerprints.add(dependency.kind.method ? normalised(method) : method.name + method.desc);
        }
        fingerprints.sort(null);
        return fingerprints;
    }

    private static String normalised(MethodNode method) {
        Textifier text = new Textifier();
        TraceMethodVisitor visitor = new TraceMethodVisitor(text);
        method.instructions.accept(visitor);
        for (TryCatchBlockNode block : method.tryCatchBlocks) block.accept(visitor);
        StringWriter out = new StringWriter();
        text.print(new PrintWriter(out));
        String code = method.name + method.desc + "\n" + out;
        return LAMBDA_NUMBER.matcher(code).replaceAll("$1N");
    }

    private static String classStatus(ClassNode before, ClassNode after) {
        if (after == null) return "missing";
        return before == null ? "added" : "ok";
    }

    private static String status(List<String> before, List<String> after) {
        if (after.isEmpty()) return "missing";
        if (before.isEmpty()) return "added";
        return before.equals(after) ? "ok" : "changed";
    }

    private static ClassNode serverClass(JarFile jar, String internalName) {
        try {
            return readClass(jar, internalName, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read " + internalName + " from " + jar.getName(), failure);
        }
    }

    private static ClassNode readClass(JarFile jar, String internalName, int flags) throws IOException {
        JarEntry entry = jar.getJarEntry(internalName + ".class");
        if (entry == null) return null;
        try (InputStream input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, flags);
            return node;
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
            }
        }
        return null;
    }
}
