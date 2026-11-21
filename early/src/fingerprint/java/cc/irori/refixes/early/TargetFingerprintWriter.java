package cc.irori.refixes.early;

import cc.irori.refixes.early.TargetFingerprints.Fingerprint;
import cc.irori.refixes.early.TargetFingerprints.Kind;
import cc.irori.refixes.early.TargetFingerprints.Member;
import cc.irori.refixes.early.TargetFingerprints.Role;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class TargetFingerprintWriter {

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String EARLY = "cc/irori/refixes/early/";
    private static final Pattern QUANTIFIER = Pattern.compile("(?:\\*|\\+|\\{\\d*(?:,\\d*)?})$");
    private static final Map<String, List<String>> RELIED_ON = Map.of(
            "MixinCodecMetadata",
            List.of(
                    "com/hypixel/hytale/codec/ExtraInfo.getMetadata",
                    "com/hypixel/hytale/codec/VersionedExtraInfo.getMetadata"));

    private final ClassLoader loader = TargetFingerprintWriter.class.getClassLoader();
    private final URL serverJar;
    private final Set<String> mixinClasses;
    private final Map<String, Optional<ClassNode>> classes = new HashMap<>();

    private TargetFingerprintWriter(URL serverJar, Set<String> mixinClasses) {
        this.serverJar = serverJar;
        this.mixinClasses = mixinClasses;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3)
            throw new IllegalArgumentException("Expected: <mixins.json> <runtime plugin classes> <output file>");
        JsonObject config;
        try (var reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String mixinPackage = config.get("package").getAsString().replace('.', '/') + "/";
        List<String> mixins = new ArrayList<>();
        for (JsonElement mixin : config.getAsJsonArray("mixins")) mixins.add(mixin.getAsString());
        URL main = TargetFingerprintWriter.class.getClassLoader().getResource("com/hypixel/hytale/Main.class");
        if (main == null || !(main.openConnection() instanceof JarURLConnection connection))
            throw new IllegalStateException("The Hytale server JAR is not on the classpath");
        String version = connection.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        if (version == null) throw new IllegalStateException("No Implementation-Version in " + main);
        Set<String> mixinClasses = new HashSet<>();
        for (String mixin : mixins) mixinClasses.add(mixinPackage + mixin);
        TargetFingerprintWriter writer = new TargetFingerprintWriter(connection.getJarFileURL(), mixinClasses);
        Set<Member> members = new LinkedHashSet<>();
        for (String mixin : mixins) writer.collect(mixin, mixinPackage + mixin, members);
        writer.plugin(Path.of(args[1]), Set.copyOf(mixins), members);
        List<String> lines = new ArrayList<>();
        lines.add(TargetFingerprints.HEADER + version.trim());
        for (Member member : members.stream()
                .sorted(Comparator.comparing(Member::mixin)
                        .thenComparing(Member::role)
                        .thenComparing(Member::owner)
                        .thenComparing(Member::kind)
                        .thenComparing(Member::name)
                        .thenComparing(Member::desc)
                        .thenComparing(Member::lambdas))
                .toList()) {
            String hash = member.role() == Role.PLUGIN
                    ? "plugin"
                    : TargetFingerprints.hash(writer.server(member.owner()), member);
            if (hash.equals(TargetFingerprints.ABSENT))
                throw new IllegalStateException("Not in the Hytale server: " + member.describe() + " (" + member + ")");
            lines.add(TargetFingerprints.line(new Fingerprint(member, hash)));
        }
        Path output = Path.of(args[2]);
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    private void collect(String mixin, String internalName, Set<Member> members) throws IOException {
        ClassNode node = early(internalName);
        if (node == null) throw new IllegalStateException("Mixin class not compiled: " + internalName);
        List<String> targets = targets(node);
        if (targets.isEmpty()) throw new IllegalStateException("No @Mixin targets in " + internalName);
        for (String target : targets) {
            if (server(target) == null) throw new IllegalStateException(mixin + " targets missing class " + target);
            members.add(new Member(mixin, Role.TARGET, target, Kind.CLASS, "", "", false));
            for (MethodNode method : node.methods) {
                for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                    targetMembers(mixin, target, method, annotation, members);
                }
            }
            for (FieldNode field : node.fields) {
                for (AnnotationNode annotation : annotations(field.visibleAnnotations, field.invisibleAnnotations)) {
                    if (!annotation.desc.equals(SHADOW)) continue;
                    String name = stripPrefix(field.name, annotation);
                    members.add(new Member(mixin, Role.SHADOW, target, Kind.FIELD, name, field.desc, false));
                }
            }
        }
        for (MethodNode method : node.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                injectionPoints(mixin, annotation, members);
            }
        }
        supertypes(mixin, Role.SUPERTYPE, node, members);
        calls(mixin, node, members);
        for (String relied : RELIED_ON.getOrDefault(mixin, List.of())) {
            int dot = relied.lastIndexOf('.');
            members.add(new Member(
                    mixin,
                    Role.RELIES,
                    relied.substring(0, dot),
                    Kind.METHOD,
                    relied.substring(dot + 1),
                    TargetFingerprints.ANY,
                    false));
        }
    }

    private void targetMembers(
            String mixin, String target, MethodNode method, AnnotationNode annotation, Set<Member> members) {
        switch (annotation.desc) {
            case OVERWRITE ->
                members.add(new Member(mixin, Role.OVERWRITE, target, Kind.METHOD, method.name, method.desc, true));
            case SHADOW -> {
                String name = stripPrefix(method.name, annotation);
                members.add(new Member(mixin, Role.SHADOW, target, Kind.METHOD, name, method.desc, true));
            }
            case ACCESSOR -> {
                Type type = Type.getMethodType(method.desc);
                String desc = type.getReturnType() == Type.VOID_TYPE
                        ? type.getArgumentTypes()[0].getDescriptor()
                        : type.getReturnType().getDescriptor();
                String name = explicitName(annotation);
                if (name == null) name = inferredName(method.name, "get", "is", "set");
                members.add(new Member(mixin, Role.ACCESSOR, target, Kind.FIELD, name, desc, false));
            }
            case INVOKER -> {
                String name = explicitName(annotation);
                if (name == null) name = inferredName(method.name, "call", "invoke");
                String desc = name.equals("<init>")
                        ? Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(method.desc))
                        : method.desc;
                members.add(new Member(mixin, Role.INVOKER, target, Kind.METHOD, name, desc, true));
            }
            default -> {
                if (value(annotation, "method") instanceof List<?> selectors) {
                    for (Object selector : selectors) members.add(injector(mixin, target, selector.toString()));
                }
            }
        }
    }

    private static Member injector(String mixin, String target, String selector) {
        int arrow = selector.indexOf("->");
        String method = (arrow >= 0 ? selector.substring(0, arrow) : selector).trim();
        int paren = method.indexOf('(');
        String name = paren >= 0 ? method.substring(0, paren) : method;
        String desc = paren >= 0 ? method.substring(paren) : TargetFingerprints.ANY;
        name = QUANTIFIER.matcher(name.substring(name.indexOf(';') + 1)).replaceFirst("");
        if (name.isEmpty()) name = TargetFingerprints.ANY;
        return new Member(mixin, Role.INJECT, target, Kind.METHOD, name, desc, true);
    }

    private void injectionPoints(String mixin, Object value, Set<Member> members) throws IOException {
        if (value instanceof List<?> list) {
            for (Object element : list) injectionPoints(mixin, element, members);
            return;
        }
        if (!(value instanceof AnnotationNode annotation) || annotation.values == null) return;
        if (annotation.desc.equals(AT) && value(annotation, "target") instanceof String target && !target.isEmpty())
            injectionPoint(mixin, String.valueOf(value(annotation, "value")), target, members);
        for (int i = 1; i < annotation.values.size(); i += 2) injectionPoints(mixin, annotation.values.get(i), members);
    }

    private void injectionPoint(String mixin, String point, String target, Set<Member> members) throws IOException {
        if (point.equals("NEW")) {
            String owner = target.startsWith("(") ? Type.getReturnType(target).getInternalName() : target;
            if (owner.startsWith("L") && owner.endsWith(";")) owner = owner.substring(1, owner.length() - 1);
            if (isServer(owner)) members.add(new Member(mixin, Role.AT, owner, Kind.CLASS, "", "", false));
            return;
        }
        if (!target.startsWith("L") || target.indexOf(';') < 0) return;
        String owner = target.substring(1, target.indexOf(';'));
        String member = target.substring(target.indexOf(';') + 1);
        int colon = member.indexOf(':');
        int paren = member.indexOf('(');
        if (colon >= 0) {
            field(mixin, Role.AT, owner, member.substring(0, colon), member.substring(colon + 1), members);
        } else if (paren >= 0) {
            method(mixin, Role.AT, Kind.METHOD, owner, member.substring(0, paren), member.substring(paren), members);
        }
    }

    private void calls(String mixin, ClassNode mixinClass, Set<Member> members) throws IOException {
        Deque<MethodNode> pending = new ArrayDeque<>(mixinClass.methods);
        Set<String> visited = new HashSet<>();
        for (MethodNode method : mixinClass.methods) visited.add(mixinClass.name + "." + method.name + method.desc);
        while (!pending.isEmpty()) {
            references(mixin, Role.CALL, Kind.METHOD, pending.poll(), members, pending, visited);
        }
    }

    private void plugin(Path classesDirectory, Set<String> mixins, Set<Member> members) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(classesDirectory)) {
            files = walk.filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) throw new IllegalStateException("No runtime plugin classes in " + classesDirectory);
        Map<String, ClassNode> plugin = new TreeMap<>();
        for (Path file : files) {
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(file)).accept(node, TargetFingerprints.READER_FLAGS);
            plugin.put(node.name, node);
        }
        Map<String, Set<Member>> links = new HashMap<>();
        for (ClassNode node : plugin.values()) {
            Set<Member> link = new LinkedHashSet<>();
            supertypes(node.name, Role.LINK, node, link);
            for (MethodNode method : node.methods)
                references(node.name, Role.LINK, Kind.SIGNATURE, method, link, null, null);
            links.put(node.name, link);
        }
        for (ClassNode node : plugin.values()) {
            Set<String> named = new LinkedHashSet<>();
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode constant
                            && constant.cst instanceof String text
                            && mixins.contains(text)) named.add(text);
                }
            }
            if (named.isEmpty()) continue;
            String group = TargetFingerprints.PLUGIN_GROUP + node.name.replace('/', '.');
            for (String reached : reachable(node.name, plugin)) {
                for (Member link : links.get(reached))
                    members.add(new Member(
                            group, link.role(), link.owner(), link.kind(), link.name(), link.desc(), link.lambdas()));
            }
            for (String mixin : named) members.add(new Member(mixin, Role.PLUGIN, group, Kind.CLASS, "", "", false));
        }
    }

    private static Set<String> reachable(String start, Map<String, ClassNode> plugin) {
        Set<String> reached = new LinkedHashSet<>(List.of(start));
        Deque<String> pending = new ArrayDeque<>(reached);
        while (!pending.isEmpty()) {
            ClassNode node = plugin.get(pending.poll());
            Set<String> used = new HashSet<>(node.interfaces);
            if (node.superName != null) used.add(node.superName);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    switch (instruction) {
                        case MethodInsnNode call -> used.add(call.owner);
                        case FieldInsnNode field -> used.add(field.owner);
                        case TypeInsnNode type ->
                            used.add(Type.getObjectType(type.desc).getInternalName());
                        case MultiANewArrayInsnNode array ->
                            used.add(Type.getType(array.desc).getElementType().getInternalName());
                        case LdcInsnNode constant -> {
                            if (constant.cst instanceof Type type && type.getSort() == Type.OBJECT)
                                used.add(type.getInternalName());
                        }
                        case InvokeDynamicInsnNode dynamic -> {
                            for (Object argument : dynamic.bsmArgs) {
                                if (argument instanceof Handle handle) used.add(handle.getOwner());
                            }
                        }
                        default -> {}
                    }
                }
            }
            for (String type : used) {
                if (plugin.containsKey(type) && reached.add(type)) pending.add(type);
            }
        }
        return reached;
    }

    private void references(
            String mixin,
            Role role,
            Kind methods,
            MethodNode method,
            Set<Member> members,
            Deque<MethodNode> pending,
            Set<String> visited)
            throws IOException {
        for (AbstractInsnNode instruction : method.instructions) {
            switch (instruction) {
                case MethodInsnNode call ->
                    invoke(mixin, role, methods, call.owner, call.name, call.desc, members, pending, visited);
                case FieldInsnNode field -> {
                    if (isServer(field.owner)) field(mixin, role, field.owner, field.name, field.desc, members);
                    else if (pending != null) enterEarly(mixin, field.owner, members, pending, visited);
                }
                case TypeInsnNode type -> type(mixin, role, Type.getObjectType(type.desc), members);
                case MultiANewArrayInsnNode array -> type(mixin, role, Type.getType(array.desc), members);
                case LdcInsnNode constant -> {
                    if (constant.cst instanceof Type type && type.getSort() != Type.METHOD)
                        type(mixin, role, type, members);
                }
                case InvokeDynamicInsnNode dynamic -> {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle)
                            invoke(
                                    mixin,
                                    role,
                                    methods,
                                    handle.getOwner(),
                                    handle.getName(),
                                    handle.getDesc(),
                                    members,
                                    pending,
                                    visited);
                    }
                }
                default -> {}
            }
        }
    }

    private void invoke(
            String mixin,
            Role role,
            Kind methods,
            String owner,
            String name,
            String desc,
            Set<Member> members,
            Deque<MethodNode> pending,
            Set<String> visited)
            throws IOException {
        if (owner.startsWith("[")) return;
        if (isServer(owner)) {
            method(mixin, role, methods, owner, name, desc, members);
            return;
        }
        if (pending == null || !isEarlyHelper(owner)) return;
        enterEarly(mixin, owner, members, pending, visited);
        for (String type = owner; type != null; ) {
            ClassNode node = early(type);
            if (node == null) return;
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) {
                    if (visited.add(type + "." + name + desc)) pending.add(method);
                    return;
                }
            }
            type = isEarlyHelper(node.superName) ? node.superName : null;
        }
    }

    private void enterEarly(
            String mixin, String owner, Set<Member> members, Deque<MethodNode> pending, Set<String> visited)
            throws IOException {
        if (!isEarlyHelper(owner) || !visited.add(owner)) return;
        ClassNode node = early(owner);
        if (node == null) return;
        supertypes(mixin, Role.SUPERTYPE, node, members);
        for (MethodNode method : node.methods) {
            boolean instance = (method.access & Opcodes.ACC_STATIC) == 0;
            if ((instance || method.name.equals("<clinit>")) && visited.add(owner + "." + method.name + method.desc))
                pending.add(method);
        }
    }

    private void supertypes(String mixin, Role role, ClassNode node, Set<Member> members) throws IOException {
        Deque<String> pending = new ArrayDeque<>();
        if (node.superName != null) pending.add(node.superName);
        pending.addAll(node.interfaces);
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            String type = pending.poll();
            if (!seen.add(type) || !isServer(type)) continue;
            members.add(new Member(mixin, role, type, Kind.SHAPE, "", "", false));
            ClassNode supertype = server(type);
            for (MethodNode method : node.methods) {
                if (method.name.startsWith("<") || (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0)
                    continue;
                for (MethodNode inherited : supertype.methods) {
                    if (inherited.name.equals(method.name) && inherited.desc.equals(method.desc))
                        members.add(new Member(mixin, role, type, Kind.SIGNATURE, method.name, method.desc, false));
                }
            }
            if (supertype.superName != null) pending.add(supertype.superName);
            pending.addAll(supertype.interfaces);
        }
    }

    private void type(String mixin, Role role, Type type, Set<Member> members) throws IOException {
        while (type.getSort() == Type.ARRAY) type = type.getElementType();
        if (type.getSort() == Type.OBJECT && isServer(type.getInternalName()))
            members.add(new Member(mixin, role, type.getInternalName(), Kind.CLASS, "", "", false));
    }

    private void method(String mixin, Role role, Kind kind, String owner, String name, String desc, Set<Member> members)
            throws IOException {
        String declaring = declaring(owner, node -> {
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) return true;
            }
            return false;
        });
        if (declaring == null) {
            if (isServer(owner)) members.add(new Member(mixin, role, owner, Kind.CLASS, "", "", false));
            return;
        }
        members.add(new Member(mixin, role, declaring, kind, name, desc, false));
        if (!declaring.equals(owner) && role != Role.LINK)
            members.add(new Member(mixin, role, owner, Kind.OVERRIDE, name, desc, false));
    }

    private void field(String mixin, Role role, String owner, String name, String desc, Set<Member> members)
            throws IOException {
        String declaring = declaring(owner, node -> {
            for (FieldNode field : node.fields) {
                if (field.name.equals(name) && field.desc.equals(desc)) return true;
            }
            return false;
        });
        if (declaring == null) {
            if (isServer(owner)) members.add(new Member(mixin, role, owner, Kind.CLASS, "", "", false));
            return;
        }
        members.add(new Member(mixin, role, declaring, Kind.FIELD, name, desc, false));
        if (!declaring.equals(owner) && role != Role.LINK)
            members.add(new Member(mixin, role, owner, Kind.OVERRIDE, name, desc, false));
    }

    private interface Declares {
        boolean test(ClassNode node);
    }

    private String declaring(String owner, Declares declares) throws IOException {
        Deque<String> pending = new ArrayDeque<>(List.of(owner));
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            String type = pending.poll();
            if (!seen.add(type) || !isServer(type)) continue;
            ClassNode node = server(type);
            if (declares.test(node)) return type;
            if (node.superName != null) pending.add(node.superName);
            pending.addAll(node.interfaces);
        }
        return null;
    }

    private boolean isServer(String internalName) throws IOException {
        URL url = loader.getResource(internalName + ".class");
        return url != null
                && url.openConnection() instanceof JarURLConnection connection
                && connection.getJarFileURL().equals(serverJar);
    }

    private boolean isEarlyHelper(String internalName) {
        return internalName != null && internalName.startsWith(EARLY) && !mixinClasses.contains(internalName);
    }

    ClassNode server(String internalName) throws IOException {
        return isServer(internalName) ? read(internalName) : null;
    }

    private ClassNode early(String internalName) throws IOException {
        return internalName.startsWith(EARLY) ? read(internalName) : null;
    }

    private ClassNode read(String internalName) throws IOException {
        Optional<ClassNode> cached = classes.get(internalName);
        if (cached != null) return cached.orElse(null);
        ClassNode node = null;
        try (InputStream input = loader.getResourceAsStream(internalName + ".class")) {
            if (input != null) {
                node = new ClassNode();
                new ClassReader(input.readAllBytes()).accept(node, TargetFingerprints.READER_FLAGS);
            }
        }
        classes.put(internalName, Optional.ofNullable(node));
        return node;
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
