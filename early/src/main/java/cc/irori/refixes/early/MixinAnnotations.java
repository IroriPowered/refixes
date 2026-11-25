package cc.irori.refixes.early;

import cc.irori.refixes.version.HytaleVersions;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class MixinAnnotations {

    public static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    public static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    public static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    public static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    public static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String IF_HYTALE = "Lcc/irori/hyinit/api/IfHytale;";
    private static final Pattern QUANTIFIER = Pattern.compile("(?:\\*|\\+|\\{\\d*(?:,\\d*)?})$");

    public record Selector(String name, String desc, boolean lambdas) {}

    public record Target(String name, String desc) {}

    private MixinAnnotations() {}

    public static boolean applies(ClassNode node, String version) {
        return applies(node.visibleAnnotations, node.invisibleAnnotations, version);
    }

    public static boolean applies(MethodNode node, String version) {
        return applies(node.visibleAnnotations, node.invisibleAnnotations, version);
    }

    public static boolean applies(FieldNode node, String version) {
        return applies(node.visibleAnnotations, node.invisibleAnnotations, version);
    }

    private static boolean applies(List<AnnotationNode> visible, List<AnnotationNode> invisible, String version) {
        for (AnnotationNode annotation : annotations(visible, invisible)) {
            if (annotation.desc.equals(IF_HYTALE) && value(annotation, "value") instanceof String range)
                return HytaleVersions.matches(range, version);
        }
        return true;
    }

    public static List<String> targets(ClassNode mixin) {
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

    public static Selector injector(String selector) {
        int arrow = selector.indexOf("->");
        String method = (arrow >= 0 ? selector.substring(0, arrow) : selector).trim();
        int paren = method.indexOf('(');
        String name = paren >= 0 ? method.substring(0, paren) : method;
        String desc = paren >= 0 ? method.substring(paren) : null;
        name = QUANTIFIER.matcher(name.substring(name.indexOf(';') + 1)).replaceFirst("");
        return new Selector(name.isEmpty() ? null : name, desc, arrow >= 0);
    }

    public static Target accessor(MethodNode method, AnnotationNode annotation) {
        Type type = Type.getMethodType(method.desc);
        String desc = type.getReturnType() == Type.VOID_TYPE
                ? type.getArgumentTypes()[0].getDescriptor()
                : type.getReturnType().getDescriptor();
        String name = explicitName(annotation);
        if (name == null) name = inferredName(method.name, "get", "is", "set");
        return new Target(name, desc);
    }

    public static Target invoker(MethodNode method, AnnotationNode annotation) {
        String name = explicitName(annotation);
        if (name == null) name = inferredName(method.name, "call", "invoke");
        String desc = name.equals("<init>")
                ? Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(method.desc))
                : method.desc;
        return new Target(name, desc);
    }

    public static String shadowName(String name, AnnotationNode shadow) {
        Object prefix = value(shadow, "prefix");
        String text = prefix == null ? "shadow$" : prefix.toString();
        return name.startsWith(text) ? name.substring(text.length()) : name;
    }

    public static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    public static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
            }
        }
        return null;
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
}
