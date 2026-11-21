package cc.irori.refixes.early;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.MixinService;

final class TargetFingerprints {

    static final String RESOURCE = "target-fingerprints.tsv";
    static final String HEADER = "# Hytale ";
    static final String PLUGIN_GROUP = "@";
    static final String ANY = "*";
    static final String ABSENT = "-";
    static final int READER_FLAGS = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
    private static final Pattern LAMBDA_NUMBER = Pattern.compile("(lambda\\$\\w+\\$)\\d+");

    enum Role {
        TARGET("target class"),
        INJECT("injected method"),
        OVERWRITE("overwritten method"),
        SHADOW("shadowed member"),
        ACCESSOR("accessor field"),
        INVOKER("invoker method"),
        AT("injection point"),
        CALL("called Hytale code"),
        SUPERTYPE("implemented Hytale type"),
        LINK("Hytale code the runtime plugin uses"),
        PLUGIN("runtime plugin class"),
        RELIES("Hytale code the patch relies on");

        final String label;

        Role(String label) {
            this.label = label;
        }
    }

    enum Kind {
        CLASS,
        FIELD,
        METHOD,
        SIGNATURE,
        SHAPE,
        OVERRIDE
    }

    record Member(String mixin, Role role, String owner, Kind kind, String name, String desc, boolean lambdas) {

        String describe() {
            String type = owner.replace('/', '.');
            return switch (kind) {
                case CLASS, SHAPE -> type;
                case OVERRIDE -> "override of " + name + (desc.startsWith("(") ? desc : ":" + desc) + " in " + type;
                case FIELD -> type + "." + name + ":" + desc;
                case METHOD, SIGNATURE ->
                    type + "." + name + (desc.equals(ANY) ? "" : desc) + (lambdas ? " and its lambdas" : "");
            };
        }
    }

    record Fingerprint(Member member, String hash) {}

    record Baseline(String serverVersion, List<Fingerprint> fingerprints) {}

    record Result(Map<String, List<String>> changes, int classes, long nanos) {}

    @FunctionalInterface
    interface ClassSource {
        ClassNode read(String internalName) throws IOException;
    }

    private TargetFingerprints() {}

    static Baseline baseline() throws IOException {
        InputStream input = TargetFingerprints.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IOException(RESOURCE + " is missing from the Refixes JAR");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.startsWith(HEADER))
                throw new IOException(RESOURCE + " has no Hytale version header");
            List<Fingerprint> fingerprints = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] columns = line.split("\t", -1);
                if (columns.length != 8) throw new IOException("Malformed line in " + RESOURCE + ": " + line);
                Member member = new Member(
                        columns[0],
                        Role.valueOf(columns[1]),
                        columns[2],
                        Kind.valueOf(columns[3]),
                        columns[4],
                        columns[5],
                        columns[6].equals("1"));
                fingerprints.add(new Fingerprint(member, columns[7]));
            }
            return new Baseline(header.substring(HEADER.length()).trim(), List.copyOf(fingerprints));
        }
    }

    static String line(Fingerprint fingerprint) {
        Member member = fingerprint.member();
        return String.join(
                "\t",
                member.mixin(),
                member.role().name(),
                member.owner(),
                member.kind().name(),
                member.name(),
                member.desc(),
                member.lambdas() ? "1" : "0",
                fingerprint.hash());
    }

    static ClassSource mixinBytecode() {
        IClassBytecodeProvider provider = MixinService.getService().getBytecodeProvider();
        return internalName -> {
            try {
                return provider.getClassNode(internalName.replace('/', '.'), true, READER_FLAGS);
            } catch (ClassNotFoundException missing) {
                return null;
            }
        };
    }

    static Result compare(Baseline baseline, Collection<String> mixins, ClassSource source) throws IOException {
        long started = System.nanoTime();
        Set<String> checked = new HashSet<>(mixins);
        List<Member> pluginUsers = new ArrayList<>();
        for (Fingerprint fingerprint : baseline.fingerprints()) {
            Member member = fingerprint.member();
            if (member.role() == Role.PLUGIN && checked.contains(member.mixin())) pluginUsers.add(member);
        }
        for (Member user : pluginUsers) checked.add(user.owner());
        Map<String, Optional<ClassNode>> classes = new HashMap<>();
        Map<String, List<String>> changes = new LinkedHashMap<>();
        for (Fingerprint fingerprint : baseline.fingerprints()) {
            Member member = fingerprint.member();
            if (member.role() == Role.PLUGIN || !checked.contains(member.mixin())) continue;
            Optional<ClassNode> owner = classes.get(member.owner());
            if (owner == null) {
                owner = Optional.ofNullable(source.read(member.owner()));
                classes.put(member.owner(), owner);
            }
            String hash = hash(owner.orElse(null), member);
            if (hash.equals(fingerprint.hash())) continue;
            String state = hash.equals(ABSENT) ? "missing" : "changed";
            changes.computeIfAbsent(member.mixin(), unused -> new ArrayList<>())
                    .add(member.role().label + " " + member.describe() + " " + state);
        }
        for (Member user : pluginUsers) {
            List<String> plugin = changes.get(user.owner());
            if (plugin != null)
                changes.computeIfAbsent(user.mixin(), unused -> new ArrayList<>())
                        .add(Role.PLUGIN.label + " " + user.owner().substring(PLUGIN_GROUP.length()) + " uses it and "
                                + plugin.size() + " Hytale members it reaches changed");
        }
        return new Result(changes, classes.size(), System.nanoTime() - started);
    }

    static String hash(ClassNode owner, Member member) {
        if (owner == null) return ABSENT;
        return switch (member.kind()) {
            case CLASS -> "class " + owner.superName;
            case SHAPE -> {
                List<String> methods = new ArrayList<>();
                for (MethodNode method : owner.methods) {
                    if ((method.access & Opcodes.ACC_ABSTRACT) != 0) methods.add(signature(method));
                }
                methods.sort(null);
                yield digest(owner.access + " " + owner.superName + " " + String.join(",", owner.interfaces) + "\n"
                        + String.join("\n", methods));
            }
            case FIELD -> {
                for (FieldNode field : owner.fields) {
                    if (field.name.equals(member.name()) && field.desc.equals(member.desc()))
                        yield digest(field.access + " " + field.name + ":" + field.desc + " " + field.value);
                }
                yield ABSENT;
            }
            case OVERRIDE -> {
                if (member.desc().startsWith("(")) {
                    for (MethodNode method : owner.methods) {
                        if (method.name.equals(member.name()) && method.desc.equals(member.desc()))
                            yield digest(body(method));
                    }
                } else {
                    for (FieldNode field : owner.fields) {
                        if (field.name.equals(member.name()) && field.desc.equals(member.desc()))
                            yield digest(field.access + " " + field.name + ":" + field.desc);
                    }
                }
                yield "none";
            }
            case METHOD, SIGNATURE -> {
                List<String> bodies = new ArrayList<>();
                for (MethodNode method : owner.methods) {
                    if ((member.name().equals(ANY) || method.name.equals(member.name()))
                            && (member.desc().equals(ANY) || method.desc.equals(member.desc()))) {
                        bodies.add(member.kind() == Kind.METHOD ? body(method) : signature(method));
                    }
                }
                if (member.lambdas() && !bodies.isEmpty()) {
                    Pattern lambda = Pattern.compile("lambda\\$" + Pattern.quote(member.name()) + "\\$\\d+");
                    for (MethodNode method : owner.methods) {
                        if (lambda.matcher(method.name).matches()) bodies.add(body(method));
                    }
                }
                if (bodies.isEmpty()) yield ABSENT;
                bodies.sort(null);
                yield digest(String.join("\n\n", bodies));
            }
        };
    }

    static String signature(MethodNode method) {
        return method.access + " " + method.name + method.desc;
    }

    static String body(MethodNode method) {
        Set<LabelNode> referenced = new HashSet<>();
        for (AbstractInsnNode instruction : method.instructions) {
            switch (instruction) {
                case JumpInsnNode jump -> referenced.add(jump.label);
                case TableSwitchInsnNode table -> {
                    referenced.add(table.dflt);
                    referenced.addAll(table.labels);
                }
                case LookupSwitchInsnNode lookup -> {
                    referenced.add(lookup.dflt);
                    referenced.addAll(lookup.labels);
                }
                default -> {}
            }
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            referenced.add(block.start);
            referenced.add(block.end);
            referenced.add(block.handler);
        }
        Map<LabelNode, Integer> labels = new HashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LabelNode label && referenced.contains(label)) labels.put(label, labels.size());
        }
        StringBuilder out = new StringBuilder();
        out.append(method.access)
                .append(' ')
                .append(method.name)
                .append(method.desc)
                .append('\n');
        for (AbstractInsnNode instruction : method.instructions) {
            switch (instruction) {
                case LabelNode label -> {
                    Integer index = labels.get(label);
                    if (index == null) continue;
                    out.append('L').append(index);
                }
                case LineNumberNode ignored -> {
                    continue;
                }
                case FrameNode ignored -> {
                    continue;
                }
                case InsnNode insn -> out.append(insn.getOpcode());
                case IntInsnNode insn ->
                    out.append(insn.getOpcode()).append(' ').append(insn.operand);
                case VarInsnNode insn ->
                    out.append(insn.getOpcode()).append(' ').append(insn.var);
                case TypeInsnNode insn ->
                    out.append(insn.getOpcode()).append(' ').append(insn.desc);
                case FieldInsnNode insn ->
                    out.append(insn.getOpcode())
                            .append(' ')
                            .append(insn.owner)
                            .append('.')
                            .append(insn.name)
                            .append(':')
                            .append(insn.desc);
                case MethodInsnNode insn ->
                    out.append(insn.getOpcode())
                            .append(' ')
                            .append(insn.owner)
                            .append('.')
                            .append(insn.name)
                            .append(insn.desc)
                            .append(insn.itf ? " itf" : "");
                case InvokeDynamicInsnNode insn -> {
                    out.append(insn.getOpcode())
                            .append(' ')
                            .append(insn.name)
                            .append(insn.desc)
                            .append(' ');
                    constant(out, insn.bsm);
                    for (Object argument : insn.bsmArgs) constant(out.append(' '), argument);
                }
                case JumpInsnNode insn ->
                    out.append(insn.getOpcode()).append(" L").append(labels.get(insn.label));
                case LdcInsnNode insn -> constant(out.append(insn.getOpcode()).append(' '), insn.cst);
                case IincInsnNode insn ->
                    out.append(insn.getOpcode())
                            .append(' ')
                            .append(insn.var)
                            .append(' ')
                            .append(insn.incr);
                case TableSwitchInsnNode insn -> {
                    out.append(insn.getOpcode())
                            .append(' ')
                            .append(insn.min)
                            .append(' ')
                            .append(insn.max);
                    out.append(" L").append(labels.get(insn.dflt));
                    for (LabelNode label : insn.labels) out.append(" L").append(labels.get(label));
                }
                case LookupSwitchInsnNode insn -> {
                    out.append(insn.getOpcode()).append(" L").append(labels.get(insn.dflt));
                    for (int i = 0; i < insn.keys.size(); i++)
                        out.append(' ').append(insn.keys.get(i)).append(" L").append(labels.get(insn.labels.get(i)));
                }
                case MultiANewArrayInsnNode insn ->
                    out.append(insn.getOpcode())
                            .append(' ')
                            .append(insn.desc)
                            .append(' ')
                            .append(insn.dims);
                default -> throw new IllegalStateException("Unknown instruction " + instruction.getClass());
            }
            out.append('\n');
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            out.append("TRY L").append(labels.get(block.start));
            out.append(" L").append(labels.get(block.end));
            out.append(" L").append(labels.get(block.handler));
            out.append(' ').append(block.type).append('\n');
        }
        return LAMBDA_NUMBER.matcher(out).replaceAll("$1N");
    }

    private static void constant(StringBuilder out, Object value) {
        switch (value) {
            case String text ->
                out.append('S').append(text.length()).append(':').append(text);
            case Type type -> out.append('T').append(type.getDescriptor());
            case Handle handle ->
                out.append('H')
                        .append(handle.getTag())
                        .append(' ')
                        .append(handle.getOwner())
                        .append('.')
                        .append(handle.getName())
                        .append(handle.getDesc())
                        .append(handle.isInterface() ? " itf" : "");
            case ConstantDynamic dynamic -> {
                out.append('D')
                        .append(dynamic.getName())
                        .append(dynamic.getDescriptor())
                        .append(' ');
                constant(out, dynamic.getBootstrapMethod());
                for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++)
                    constant(out.append(' '), dynamic.getBootstrapMethodArgument(i));
            }
            default -> out.append(value.getClass().getSimpleName()).append(':').append(value);
        }
    }

    static String digest(CharSequence text) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
