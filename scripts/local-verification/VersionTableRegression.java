package cc.irori.refixes.early;

import static local.hyinit.verify.RegressionRunner.require;

import cc.irori.refixes.version.HytaleLine;
import cc.irori.refixes.version.HytaleVersions;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

public final class VersionTableRegression {

    private static final String IF_HYTALE = "Lcc/irori/hyinit/api/IfHytale;";
    private static final HytaleLine V07 = HytaleLine.V07;
    private static final HytaleLine V08 = HytaleLine.V08;

    private record Row(String version, HytaleLine set, HytaleLine nearest, RefixesMixinPlugin.Gate gate) {}

    private static final List<Row> DEFAULT = List.of(
            new Row("0.7.6", V07, V07, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.7.7", V07, V07, RefixesMixinPlugin.Gate.ALL),
            new Row("0.7.7.1", null, V07, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.7.8", V07, V07, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.7.8-pre.1", null, V07, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.8.0-pre.1", V08, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.8.0-pre.5", V08, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.8.0-pre.6", V08, V08, RefixesMixinPlugin.Gate.ALL),
            new Row("0.8.0-pre.6.1", V08, V08, RefixesMixinPlugin.Gate.ALL),
            new Row("0.8.0-pre.7", V08, V08, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.8.0-pre.13.1", V08, V08, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.8.0", V08, V08, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.8.0.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.8.1", V08, V08, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.8.1-pre.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.9.0-pre.1", V08, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.9.0", V08, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.10.0-pre.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("unknown", null, V07, RefixesMixinPlugin.Gate.DISABLED),
            new Row(null, null, V07, RefixesMixinPlugin.Gate.DISABLED));

    private static final List<Row> ALL_PATCHES = List.of(
            new Row("0.7.6", V07, V07, RefixesMixinPlugin.Gate.OVERRIDDEN),
            new Row("0.7.7.1", null, V07, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.7.8-pre.1", null, V07, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.8.0-pre.5", V08, V08, RefixesMixinPlugin.Gate.OVERRIDDEN),
            new Row("0.8.0.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.8.1-pre.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.9.0-pre.1", V08, V08, RefixesMixinPlugin.Gate.OVERRIDDEN),
            new Row("0.9.0", V08, V08, RefixesMixinPlugin.Gate.OVERRIDDEN),
            new Row("0.10.0-pre.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("unknown", null, V07, RefixesMixinPlugin.Gate.DISABLED));

    private static final List<Row> ALL_NEWER = List.of(
            new Row("0.9.0", V08, V08, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.9.0-pre.1", V08, V08, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.9.0-pre.3.2", V08, V08, RefixesMixinPlugin.Gate.UNCHANGED),
            new Row("0.8.1-pre.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.10.0-pre.1", null, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.8.0-pre.5", V08, V08, RefixesMixinPlugin.Gate.DISABLED));

    private static final List<Row> NEVER = List.of(
            new Row("0.8.0-pre.7", V08, V08, RefixesMixinPlugin.Gate.DISABLED),
            new Row("0.9.0-pre.1", V08, V08, RefixesMixinPlugin.Gate.DISABLED));

    public static void main(String[] args) throws Exception {
        Set<String> ranges = ranges();
        require(
                ranges.equals(Set.of("<0.8.0-pre.1", ">=0.8.0-pre.1 || >=0.9.0-0")),
                "every @IfHytale in the Refixes JAR must name one of the two version sets, found " + ranges);
        Set<String> lines = new HashSet<>();
        for (HytaleLine line : HytaleLine.values()) lines.add(line.range());
        require(ranges.equals(lines), "the Hytale lines " + lines + " must equal the @IfHytale sets " + ranges);
        verify(DEFAULT, RefixesMixinPlugin.SAME_MINOR_VERSION);
        verify(ALL_PATCHES, RefixesMixinPlugin.ALL_PATCHES);
        verify(ALL_NEWER, RefixesMixinPlugin.ALL_NEWER_VERSIONS);
        verify(NEVER, RefixesMixinPlugin.NEVER);
        order("0.7.7", ServerVersion.Relation.TARGET, "0.7.7", "0.7.7.1");
        order("0.7.7", ServerVersion.Relation.OLDER, "0.7.6", "0.7.5", "0.6.9", "0.5.10", "0.7.0-pre.5.2", "0.7.7-pre.1");
        order("0.7.7", ServerVersion.Relation.NEWER_SAME_MINOR, "0.7.8", "0.7.8.1", "0.7.70");
        order("0.7.7", ServerVersion.Relation.NEWER_MINOR, "0.8.0-pre.5", "0.8.0-pre.6", "0.8.0", "1.0.0");
        order("0.8.0-pre.6", ServerVersion.Relation.TARGET, "0.8.0-pre.6", "0.8.0-pre.6.1");
        order("0.8.0-pre.6", ServerVersion.Relation.OLDER, "0.8.0-pre.5", "0.8.0-pre.5.2", "0.8.0-pre.1", "0.7.7", "0.6.0-pre.13.1");
        order("0.8.0-pre.6", ServerVersion.Relation.NEWER_SAME_MINOR, "0.8.0-pre.7", "0.8.0-pre.10", "0.8.0", "0.8.0.1", "0.8.1");
        order("0.8.0-pre.6", ServerVersion.Relation.NEWER_MINOR, "0.9.0-pre.1", "0.9.0");
        order("0.7.7", ServerVersion.Relation.UNKNOWN, null, "unknown", "0.7", "0.7.8-beta", "0.7.99999999999", " 0.7.7");
        require(ServerVersion.minor("0.8.0-pre.6").equals("0.8"), "the minor version of 0.8.0-pre.6 is 0.8");
        require(ServerVersion.minor("0.7.7").equals("0.7"), "the minor version of 0.7.7 is 0.7");
        System.out.println("PASS Hytale version table");
    }

    private static void verify(List<Row> rows, String otherServerVersions) throws Exception {
        for (Row row : rows) {
            String label = row.version() + " with " + otherServerVersions;
            HytaleLine set = null;
            for (HytaleLine line : HytaleLine.values()) {
                if (!HytaleVersions.matches(line.range(), row.version())) continue;
                require(set == null, label + ": matches both @IfHytale sets");
                set = line;
            }
            require(set == row.set(), label + ": set " + set + ", expected " + row.set());
            HytaleLine nearest = HytaleLine.nearest(row.version());
            require(nearest == row.nearest(), label + ": nearest line " + nearest + ", expected " + row.nearest());
            RefixesMixinPlugin.Gate gate = RefixesMixinPlugin.gate(row.version(), otherServerVersions);
            require(gate == row.gate(), label + ": gate " + gate + ", expected " + row.gate());
            if (gate != RefixesMixinPlugin.Gate.DISABLED) {
                require(set != null, label + ": patches apply although no @IfHytale set matches");
                require(set == nearest, label + ": set, adapter and target are from different lines");
            }
            String adapter = nearest.adapter();
            System.out.println("  " + label + ": set " + set + ", adapter " + adapter.substring(adapter.lastIndexOf('.') + 1)
                    + ", target " + nearest.target() + ", " + gate);
        }
    }

    private static void order(String target, ServerVersion.Relation expected, String... running) {
        for (String version : running) {
            ServerVersion.Relation relation = ServerVersion.relation(target, version);
            require(relation == expected, "target " + target + " running " + version + " must be " + expected + ", got " + relation);
        }
    }

    private static Set<String> ranges() throws Exception {
        Path jar = Path.of(RefixesMixinPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Set<String> ranges = new HashSet<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            for (JarEntry entry : Collections.list(file.entries())) {
                if (!entry.getName().startsWith("cc/irori/refixes/") || !entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (var input = file.getInputStream(entry)) {
                    new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_CODE);
                }
                collect(node.invisibleAnnotations, ranges);
                collect(node.visibleAnnotations, ranges);
                node.methods.forEach(method -> {
                    collect(method.invisibleAnnotations, ranges);
                    collect(method.visibleAnnotations, ranges);
                });
                node.fields.forEach(field -> {
                    collect(field.invisibleAnnotations, ranges);
                    collect(field.visibleAnnotations, ranges);
                });
            }
        }
        return ranges;
    }

    private static void collect(List<AnnotationNode> annotations, Set<String> ranges) {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(IF_HYTALE)) ranges.add((String) annotation.values.get(1));
        }
    }

    private VersionTableRegression() {}
}
