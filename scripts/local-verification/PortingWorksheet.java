package local.refixes.drift;

import static cc.irori.refixes.early.MixinAnnotations.ACCESSOR;
import static cc.irori.refixes.early.MixinAnnotations.INVOKER;
import static cc.irori.refixes.early.MixinAnnotations.OVERWRITE;
import static cc.irori.refixes.early.MixinAnnotations.SHADOW;
import static cc.irori.refixes.early.MixinAnnotations.annotations;
import static cc.irori.refixes.early.MixinAnnotations.value;

import cc.irori.refixes.early.MixinAnnotations;
import cc.irori.refixes.early.RefixesMixinPlugin;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

public final class PortingWorksheet {

    private PortingWorksheet() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            throw new IllegalArgumentException(
                    "Expected: <refixes.jar> <baseline-server.jar> <server.jar> <result.json> <mixin class>...");
        }
        Map<String, String> switches = switches();
        List<Object> entries = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        try (JarFile refixes = new JarFile(args[0]);
                JarFile baseline = new JarFile(args[1]);
                JarFile current = new JarFile(args[2])) {
            String before = TargetDrift.version(baseline);
            Server old = new Server(baseline);
            Server now = new Server(current);
            int checked = 0;
            for (String mixinName : Arrays.asList(args).subList(4, args.length)) {
                ClassNode mixin = TargetDrift.readClass(refixes, mixinName.replace('.', '/'), ClassReader.SKIP_CODE);
                if (mixin == null) throw new IllegalStateException("Mixin class not in Refixes JAR: " + mixinName);
                if (!MixinAnnotations.applies(mixin, before)) continue;
                mixin.methods.removeIf(method -> !MixinAnnotations.applies(method, before));
                mixin.fields.removeIf(field -> !MixinAnnotations.applies(field, before));
                checked++;
                List<Object> members = new ArrayList<>();
                for (String target : MixinAnnotations.targets(mixin)) members.addAll(members(mixin, target, old, now));
                members.addAll(atMembers(mixin, old, now));
                if (members.isEmpty()) continue;
                String shortName = mixinName.substring(mixinName.lastIndexOf('.') + 1);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("mixin", shortName);
                entry.put("switch", switches.get(shortName));
                entry.put("targets", MixinAnnotations.targets(mixin).stream()
                        .map(name -> name.replace('/', '.'))
                        .toList());
                entry.put("injectors", injectors(mixin));
                entry.put("members", members);
                entries.add(entry);
            }
            result.put("baseline", before);
            result.put("current", TargetDrift.version(current));
            result.put("checked", checked);
            result.put("mixins", entries);
        }
        Files.writeString(Path.of(args[3]), json(result) + "\n");
    }

    private static List<Object> members(ClassNode mixin, String target, Server old, Server now) {
        List<Object> members = new ArrayList<>();
        ClassNode before = old.plain(target);
        ClassNode after = now.plain(target);
        String moved = null;
        if (after == null) {
            List<String> candidates = now.sameSimpleName(target);
            Map<String, Object> entry = member(target, null, "class", target.replace('/', '.'));
            entry.put("status", TargetDrift.classStatus(before, after));
            entry.put("classCandidates", candidates);
            members.add(entry);
            if (candidates.size() == 1) {
                moved = candidates.getFirst().replace('.', '/');
                after = now.plain(moved);
            }
        }
        for (TargetDrift.Dependency dependency : TargetDrift.dependencies(mixin)) {
            Map<String, Object> entry = compare(target, moved, before, after, dependency, null, old, now);
            if (entry != null) members.add(entry);
        }
        return members;
    }

    private static List<Object> atMembers(ClassNode mixin, Server old, Server now) {
        Map<String, TargetDrift.Dependency> points = new LinkedHashMap<>();
        for (MethodNode method : mixin.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                Object at = value(annotation, "at");
                for (Object point : at instanceof List<?> list ? list : at == null ? List.of() : List.of(at)) {
                    if (value((AnnotationNode) point, "target") instanceof String target) {
                        String owner = atOwner(target);
                        if (owner != null && old.plain(owner) != null) points.putIfAbsent(target, atDependency(target));
                    }
                }
            }
        }
        List<Object> members = new ArrayList<>();
        points.forEach((target, dependency) -> {
            String owner = atOwner(target);
            Map<String, Object> entry = compare(owner, null, old.plain(owner), now.plain(owner), dependency, "at", old, now);
            if (entry != null) members.add(entry);
        });
        return members;
    }

    private static String atOwner(String target) {
        if (target.startsWith("L") && target.indexOf(';') > 0) return target.substring(1, target.indexOf(';'));
        return target.startsWith("(") || target.contains(";") || target.isEmpty() ? null : target;
    }

    private static TargetDrift.Dependency atDependency(String target) {
        if (!target.startsWith("L")) {
            return new TargetDrift.Dependency(TargetDrift.Kind.INJECTOR, target, "<init>", null, false, false);
        }
        String member = target.substring(target.indexOf(';') + 1);
        int colon = member.indexOf(':');
        if (colon > 0) {
            return new TargetDrift.Dependency(
                    TargetDrift.Kind.SHADOW, target, member.substring(0, colon), member.substring(colon + 1), true, false);
        }
        int paren = member.indexOf('(');
        return new TargetDrift.Dependency(
                TargetDrift.Kind.INJECTOR, target, member.substring(0, paren), member.substring(paren), false, false);
    }

    private static Map<String, Object> compare(
            String target,
            String moved,
            ClassNode before,
            ClassNode after,
            TargetDrift.Dependency dependency,
            String kind,
            Server old,
            Server now) {
        List<String> declared = TargetDrift.resolve(before, dependency);
        if (kind != null && declared.isEmpty()) return null;
        String status = TargetDrift.status(declared, TargetDrift.resolve(after, dependency));
        if (status.equals("ok") || status.equals("added")) return null;
        String current = moved == null ? target : moved;
        Map<String, Object> entry =
                member(target, moved, kind == null ? dependency.kind().label : kind, dependency.member());
        entry.put("status", status);
        entry.put("old", code(old, target, before, dependency));
        entry.put("new", code(now, current, after, dependency));
        if (status.equals("missing")) {
            if (!dependency.field() && dependency.name() != null) {
                TargetDrift.Dependency sameName = new TargetDrift.Dependency(
                        dependency.kind(), dependency.member(), dependency.name(), null, false, false);
                entry.put("sameName", code(now, current, after, sameName));
            }
            List<String> candidates = new ArrayList<>();
            if (after != null) {
                for (MethodNode method : after.methods) candidates.add(method.name + method.desc);
                for (FieldNode field : after.fields) candidates.add(field.name + ":" + field.desc);
            }
            entry.put("candidates", candidates);
        }
        return entry;
    }

    private static Map<String, Object> member(String target, String moved, String kind, String member) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("target", target.replace('/', '.'));
        entry.put("movedTo", moved == null ? null : moved.replace('/', '.'));
        entry.put("kind", kind);
        entry.put("member", member);
        return entry;
    }

    private static List<Object> code(Server server, String className, ClassNode node, TargetDrift.Dependency dependency) {
        List<Object> code = new ArrayList<>();
        if (node == null) return code;
        if (dependency.field()) {
            for (FieldNode field : node.fields) {
                if (field.name.equals(dependency.name()) && field.desc.equals(dependency.desc())) {
                    code.add(Map.of("name", field.name, "desc", field.desc, "bytecode", field.name + ":" + field.desc));
                }
            }
            return code;
        }
        ClassNode debug = server.debug(className);
        for (MethodNode method : TargetDrift.selected(node, dependency)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", method.name);
            entry.put("desc", method.desc);
            entry.put("bytecode", TargetDrift.normalised(method));
            int first = -1;
            int last = -1;
            for (MethodNode lines : debug.methods) {
                if (!lines.name.equals(method.name) || !lines.desc.equals(method.desc)) continue;
                for (AbstractInsnNode instruction : lines.instructions) {
                    if (instruction instanceof LineNumberNode line) {
                        first = first < 0 ? line.line : Math.min(first, line.line);
                        last = Math.max(last, line.line);
                    }
                }
            }
            int slash = className.lastIndexOf('/');
            entry.put("source", debug.sourceFile == null ? null : className.substring(0, slash + 1) + debug.sourceFile);
            entry.put("first", first);
            entry.put("last", last);
            code.add(entry);
        }
        return code;
    }

    private static List<Object> injectors(ClassNode mixin) {
        List<Object> injectors = new ArrayList<>();
        for (MethodNode method : mixin.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (List.of(SHADOW, ACCESSOR, INVOKER).contains(annotation.desc)) continue;
                List<String> selectors = new ArrayList<>();
                if (annotation.desc.equals(OVERWRITE)) selectors.add(method.name + method.desc);
                else if (value(annotation, "method") instanceof List<?> methods)
                    for (Object selector : methods) selectors.add(selector.toString());
                if (selectors.isEmpty()) continue;
                List<String> at = new ArrayList<>();
                Object points = value(annotation, "at");
                for (Object point : points instanceof List<?> list ? list : points == null ? List.of() : List.of(points)) {
                    at.add(at((AnnotationNode) point));
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                String desc = annotation.desc;
                entry.put("annotation", desc.substring(desc.lastIndexOf('/') + 1, desc.length() - 1));
                entry.put("handler", method.name);
                entry.put("methods", selectors);
                entry.put("at", at);
                injectors.add(entry);
            }
        }
        return injectors;
    }

    private static String at(AnnotationNode at) {
        StringBuilder text = new StringBuilder(String.valueOf(value(at, "value")));
        if (value(at, "target") instanceof String target && !target.isEmpty()) text.append(' ').append(target);
        if (value(at, "ordinal") instanceof Integer ordinal) text.append(" ordinal ").append(ordinal);
        if (value(at, "shift") instanceof String[] shift) text.append(" shift ").append(shift[1]);
        return text.toString();
    }

    private static Map<String, String> switches() throws ReflectiveOperationException {
        Field toggles = RefixesMixinPlugin.class.getDeclaredField("TOGGLES");
        toggles.setAccessible(true);
        Map<String, String> switches = new HashMap<>();
        for (Object toggle : (List<?>) toggles.get(null)) {
            String path = String.join(".", (String[]) accessor(toggle, "jsonPath"));
            for (String part : List.of("mixins", "uses")) {
                for (Object mixin : (List<?>) accessor(toggle, part)) switches.putIfAbsent((String) mixin, path);
            }
        }
        return switches;
    }

    private static Object accessor(Object record, String name) throws ReflectiveOperationException {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((key, item) -> parts.add(json(key.toString()) + ":" + json(item)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(PortingWorksheet::json).toList()) + "]";
        StringBuilder text = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            if (c == '"' || c == '\\') text.append('\\').append(c);
            else if (c < 0x20) text.append(String.format("\\u%04x", (int) c));
            else text.append(c);
        }
        return text.append('"').toString();
    }

    private static final class Server {
        private final JarFile jar;
        private final Map<String, ClassNode> plain = new HashMap<>();
        private final Map<String, ClassNode> debug = new HashMap<>();

        Server(JarFile jar) {
            this.jar = jar;
        }

        ClassNode plain(String name) {
            return plain.computeIfAbsent(name, key -> TargetDrift.serverClass(jar, key));
        }

        ClassNode debug(String name) {
            return debug.computeIfAbsent(name, key -> {
                try {
                    return TargetDrift.readClass(jar, key, ClassReader.SKIP_FRAMES);
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot read " + key + " from " + jar.getName(), failure);
                }
            });
        }

        List<String> sameSimpleName(String name) {
            String simple = name.substring(name.lastIndexOf('/') + 1) + ".class";
            return jar.stream()
                    .map(entry -> entry.getName())
                    .filter(entry -> entry.endsWith("/" + simple))
                    .map(entry -> entry.substring(0, entry.length() - ".class".length()).replace('/', '.'))
                    .sorted()
                    .toList();
        }
    }
}
