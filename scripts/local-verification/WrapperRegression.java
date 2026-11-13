package local.hyinit.verify;

import cc.irori.refixes.early.duck.QuarantinedSection;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.EmptySectionPalette;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public final class WrapperRegression {
    private static final String PREFIX = "cc.irori.refixes.early.mixin.";
    private static int sequence;

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Throwable failure) {
            var error = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err));
            failure.printStackTrace(error);
            error.flush();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("wrappers")) {
            worldMap();
            spawning();
            blockSection();
            forwarding();
            print("PASS: original-operation composition, exception boundaries, nested shared guards, fallback reset");
        }
        if (args.length == 0 || args[0].equals("capability")) {
            capability();
            print("PASS: Fabric capability supported, absent, older, missing field, initialization linkage error");
        }
    }

    private static void worldMap() throws Exception {
        Object first = fixture("MixinWorldMapTracker");
        Object second = fixture("MixinWorldMapTracker");
        AtomicInteger calls = new AtomicInteger();
        call(first, "refixes$wrapUnloadImages", 3, 7, -9, (Operation<Void>) args -> {
            check(Arrays.equals(args, new Object[] {3, 7, -9}), "world-map arguments changed");
            calls.incrementAndGet();
            return null;
        });
        check(calls.get() == 1, "world-map original was not called exactly once");
        call(first, "refixes$wrapUnloadImages", 0, 0, 0, failing(new NullPointerException()));
        expect(IllegalStateException.class, () -> call(first, "refixes$wrapUnloadImages", 0, 0, 0,
                failing(new IllegalStateException())));
        AtomicInteger continued = new AtomicInteger();
        call(first, "refixes$wrapUnloadImages", 0, 0, 0, (Operation<Void>) args -> {
            call(second, "refixes$wrapUnloadImages", 0, 0, 0, failing(new NullPointerException()));
            continued.incrementAndGet();
            return null;
        });
        check(continued.get() == 1, "nested world-map containment changed");
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        call(first, "refixes$wrapUnloadImages", 0, 0, 0, (Operation<Void>) args -> {
            Thread thread = new Thread(() -> {
                try {
                    call(second, "refixes$wrapUnloadImages", 0, 0, 0, failing(new NullPointerException()));
                } catch (Throwable failure) {
                    threadFailure.set(failure);
                }
            });
            thread.start();
            try {
                thread.join(5000);
            } catch (InterruptedException failure) {
                throw new AssertionError(failure);
            }
            check(!thread.isAlive(), "thread-isolation probe did not finish");
            return null;
        });
        check(threadFailure.get() == null, "guard leaked between threads");
    }

    private static void spawning() throws Exception {
        Object first = fixture("MixinWorldSpawningSystem");
        Object second = fixture("MixinWorldSpawningSystem");
        Object token = new Ref<>(null, 7);
        check(call(first, "refixes$wrapPickRandomChunk", null, null, null, null,
                (Operation<Object>) args -> token) == token, "spawn return identity changed");
        check(call(first, "refixes$wrapPickRandomChunk", null, null, null, null,
                failing(new Exception())) == null, "spawn exception did not return null");
        expect(AssertionError.class, () -> call(first, "refixes$wrapPickRandomChunk", null, null, null, null,
                failing(new AssertionError())));
        AtomicInteger continued = new AtomicInteger();
        check(call(first, "refixes$wrapPickRandomChunk", null, null, null, null, (Operation<Object>) args -> {
            check(call(second, "refixes$wrapPickRandomChunk", null, null, null, null,
                    failing(new Exception())) == null, "inner spawn exception did not return null");
            continued.incrementAndGet();
            return token;
        }) == token, "outer spawn return changed by inner containment");
        check(continued.get() == 1, "nested spawn containment changed");
    }

    private static void blockSection() throws Exception {
        Object first = fixture("MixinBlockSectionSafety");
        dirty(first);
        byte[] bytes = {1, 2, 3};
        call(first, "refixes$safeDeserialize", bytes, null, (Operation<Void>) args -> {
            check(args[0] == bytes && args[1] == null, "deserialize arguments changed");
            return null;
        });
        check(field(first, "tickingBlocksCount").equals(7), "successful deserialize was reset");
        call(first, "refixes$safeDeserialize", bytes, null, failing(new Exception()));
        reset(first, 1);
        Path quarantined = ((QuarantinedSection) first).refixes$takeQuarantine();
        check(quarantined != null && Files.deleteIfExists(quarantined), "reset section did not hand over its quarantine file");
        dirty(first);
        expect(AssertionError.class, () -> call(first, "refixes$safeDeserialize", bytes, null,
                failing(new AssertionError())));
        check(field(first, "tickingBlocksCount").equals(7), "Error unexpectedly reset section");
    }

    private static void forwarding() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Class<?> component = Class.forName("com.hypixel.hytale.component.Component");
        Object token = java.lang.reflect.Proxy.newProxyInstance(component.getClassLoader(),
                new Class<?>[] {component}, (proxy, method, args) -> null);
        Object npc = fixture("MixinNPCKillsEntitySystem");
        Ref<?> ref = new Ref<>(null, 7);
        check(ref.isValid(), "NPC fixture reference was not valid");
        calls.set(0);
        check(call(npc, "refixes$guardKillerRef", null, ref, null, (Operation<Object>) args -> {
            check(args.length == 3 && args[0] == null && args[1] == ref && args[2] == null,
                    "NPC receiver/reference/type were not forwarded");
            calls.incrementAndGet();
            return token;
        }) == token, "NPC operation result changed");
        check(calls.get() == 1, "NPC valid reference did not chain exactly once");
        expect(IllegalStateException.class, () -> call(npc, "refixes$guardKillerRef", null, ref, null,
                failing(new IllegalStateException())));
        Method invalidate = Ref.class.getDeclaredMethod("invalidate");
        invalidate.setAccessible(true);
        invoke(invalidate, ref);
        check(!ref.isValid(), "NPC fixture reference did not become invalid");
        check(call(npc, "refixes$guardKillerRef", null, ref, null,
                failing(new AssertionError("invalid reference reached original operation"))) == null,
                "NPC invalid reference did not return null");
        expect(NullPointerException.class, () -> call(npc, "refixes$guardKillerRef", null, null, null,
                failing(new AssertionError("null reference reached original operation"))));
    }

    private static void capability() throws Exception {
        Class<?> plugin = Class.forName("cc.irori.refixes.early.RefixesMixinPlugin");
        Method guard = plugin.getDeclaredMethod("requireFabricMixinSupport", ClassLoader.class);
        guard.setAccessible(true);
        invoke(guard, null, plugin.getClassLoader());
        invoke(guard, null, fabricLoader(17005, false, false));
        invoke(guard, null, fabricLoader(18000, false, false));
        for (ClassLoader loader : new ClassLoader[] {
                fabricLoader(null, false, false), fabricLoader(17004, false, false),
                fabricLoader(17005, true, false), fabricLoader(17005, false, true)}) {
            Throwable failure = expect(IllegalStateException.class, () -> invoke(guard, null, loader));
            check(failure.getMessage().contains("0.17.5") && failure.getMessage().contains("Hyinit"),
                    "runtime rejection was not actionable: " + failure);
        }
    }

    private static ClassLoader fabricLoader(Integer compatibility, boolean omitField, boolean brokenInit) {
        return new ClassLoader(WrapperRegression.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("org.spongepowered.asm.mixin.FabricUtil")) return super.loadClass(name, resolve);
                if (compatibility == null) throw new ClassNotFoundException(name);
                Class<?> found = findLoadedClass(name);
                if (found != null) return found;
                ClassWriter writer = new ClassWriter(0);
                writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
                if (!omitField) writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "COMPATIBILITY_LATEST", "I", null, compatibility).visitEnd();
                if (brokenInit) {
                    var method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    method.visitCode();
                    method.visitTypeInsn(Opcodes.NEW, "java/lang/NoClassDefFoundError");
                    method.visitInsn(Opcodes.DUP);
                    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NoClassDefFoundError", "<init>", "()V", false);
                    method.visitInsn(Opcodes.ATHROW);
                    method.visitMaxs(2, 0);
                    method.visitEnd();
                }
                writer.visitEnd();
                byte[] bytes = writer.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
    }

    private static Object fixture(String mixin) throws Exception {
        String parent = PREFIX + mixin;
        String name = "local.hyinit.verify.GeneratedWrapper" + sequence++;
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, parent.replace('.', '/'), null);
        writer.visitField(Opcodes.ACC_PUBLIC, "invalidations", "I", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent.replace('.', '/'), "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        var invalidate = writer.visitMethod(Opcodes.ACC_PUBLIC, "invalidate", "()V", null, null);
        invalidate.visitCode();
        invalidate.visitVarInsn(Opcodes.ALOAD, 0);
        invalidate.visitInsn(Opcodes.DUP);
        invalidate.visitFieldInsn(Opcodes.GETFIELD, name.replace('.', '/'), "invalidations", "I");
        invalidate.visitInsn(Opcodes.ICONST_1);
        invalidate.visitInsn(Opcodes.IADD);
        invalidate.visitFieldInsn(Opcodes.PUTFIELD, name.replace('.', '/'), "invalidations", "I");
        invalidate.visitInsn(Opcodes.RETURN);
        invalidate.visitMaxs(3, 1);
        invalidate.visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(WrapperRegression.class.getClassLoader()) {
            Class<?> make() { return defineClass(name, bytes, 0, bytes.length); }
        }.make();
        return type.getConstructor().newInstance();
    }

    private static Object call(Object fixture, String name, Object... args) {
        Method method = Arrays.stream(fixture.getClass().getSuperclass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
        check(method.getParameterTypes()[method.getParameterCount() - 1] == Operation.class,
                name + " does not compose the original operation");
        method.setAccessible(true);
        return invoke(method, fixture, args);
    }

    private static Object invoke(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException failure) {
            return raise(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void dirty(Object fixture) throws Exception {
        for (String name : new String[] {"chunkSection", "fillerSection", "rotationSection", "localLight", "globalLight"}) {
            field(fixture, name, null);
        }
        BitSet ticking = new BitSet();
        ticking.set(10);
        field(fixture, "tickingBlocks", ticking);
        field(fixture, "tickingBlocksCount", 7);
        field(fixture, "localChangeCounter", (short) 8);
        field(fixture, "globalChangeCounter", (short) 9);
    }

    private static void reset(Object fixture, int invalidations) throws Exception {
        for (String name : new String[] {"chunkSection", "fillerSection", "rotationSection"}) {
            check(field(fixture, name) == EmptySectionPalette.INSTANCE, name + " was not reset");
        }
        for (String name : new String[] {"localLight", "globalLight"}) {
            check(field(fixture, name) == ChunkLightData.EMPTY, name + " was not reset");
        }
        check(((BitSet) field(fixture, "tickingBlocks")).isEmpty(), "ticking bits were not cleared");
        check(field(fixture, "tickingBlocksCount").equals(0), "ticking count was not cleared");
        check(field(fixture, "localChangeCounter").equals((short) 0), "local light counter was not cleared");
        check(field(fixture, "globalChangeCounter").equals((short) 0), "global light counter was not cleared");
        check(field(fixture, "invalidations").equals(invalidations), "section invalidation count changed");
    }

    private static Object field(Object fixture, String name) throws Exception {
        var field = name.equals("invalidations") ? fixture.getClass().getField(name)
                : fixture.getClass().getSuperclass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fixture);
    }

    private static void field(Object fixture, String name, Object value) throws Exception {
        var field = fixture.getClass().getSuperclass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(fixture, value);
    }

    private static Operation<Object> failing(Throwable failure) {
        return args -> raise(failure);
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T raise(Throwable failure) throws E {
        throw (E) failure;
    }

    private static Throwable expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return failure;
            throw new AssertionError("Expected " + type.getName() + " but got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void print(String message) {
        var output = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
        output.println(message);
        output.flush();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
