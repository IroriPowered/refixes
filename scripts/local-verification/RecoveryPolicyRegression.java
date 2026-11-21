package local.hyinit.verify;

import cc.irori.refixes.early.mixin.MixinBlockSectionSafety;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.EmptySectionPalette;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class RecoveryPolicyRegression {
    private static final String POLICY_CLASS = "cc.irori.refixes.early.util.RecoveryPolicy";

    private RecoveryPolicyRegression() {}

    public static void main(String[] args) throws Exception {
        mixinKeepsSectionOnSuccessOrError();
        recoveryRunsOnlyAfterAtomicPreservation();
        failedPreservationDoesNotRecover();
        mixinResetsOnlyAfterPreservingBytes();
        mixinRefusesResetWhenPreservationFails();
        labelCarriesWorldAndSection();
        System.out.println("RecoveryPolicyRegression PASS");
    }

    private static void recoveryRunsOnlyAfterAtomicPreservation() throws Exception {
        Path directory = Files.createTempDirectory("refixes-recovery-success-");
        byte[] original = {0, 1, -1, 42, 127};
        AtomicInteger recoveries = new AtomicInteger();
        Path quarantined = quarantineThen(original, directory, recoveries::incrementAndGet);
        require(recoveries.get() == 1, "recovery did not run exactly once after preservation");
        require(Files.exists(quarantined), "quarantine result does not exist");
        require(Arrays.equals(original, Files.readAllBytes(quarantined)), "returned quarantine path has different bytes");
        try (Stream<Path> files = Files.list(directory)) {
            require(files.count() == 1, "incomplete quarantine file was left behind");
        }
    }

    private static void failedPreservationDoesNotRecover() throws Exception {
        Path root = Files.createTempDirectory("refixes-recovery-failure-");
        Path notDirectory = Files.createFile(root.resolve("not-a-directory"));
        AtomicInteger recoveries = new AtomicInteger();
        try {
            quarantineThen(new byte[] {1, 2, 3}, notDirectory, recoveries::incrementAndGet);
            throw new AssertionError("quarantine unexpectedly succeeded in a regular file path");
        } catch (IOException expected) {
            require(recoveries.get() == 0, "recovery ran without a successful quarantine");
        }
    }

    private static void mixinKeepsSectionOnSuccessOrError() throws Exception {
        SectionSafety safety = dirtySection();
        invokeDeserialize(safety, new byte[] {1, 2, 3}, arguments -> null);
        require(safety.invalidations == 0 && field(safety, "tickingBlocksCount").equals(3), "successful deserialize reset the section");
        LinkageError error = new LinkageError("decode error");
        try {
            invokeDeserialize(safety, new byte[] {1, 2, 3}, arguments -> {
                throw error;
            });
            throw new AssertionError("deserialize swallowed an Error");
        } catch (LinkageError actual) {
            require(actual == error, "deserialize replaced the Error");
        }
        require(safety.invalidations == 0 && field(safety, "tickingBlocksCount").equals(3), "an Error reset the section");
    }

    private static void mixinResetsOnlyAfterPreservingBytes() throws Exception {
        byte[] original = {4, 0, -1, 31, 99};
        SectionSafety safety = dirtySection();
        IllegalStateException decodeFailure = new IllegalStateException("native decode failed");
        invokeDeserialize(safety, original, arguments -> {
            throw decodeFailure;
        });
        require(safety.invalidations == 1, "successful preservation did not reset and invalidate the full section");
        require(field(safety, "chunkSection") == EmptySectionPalette.INSTANCE, "block palette was not reset");
        require(field(safety, "fillerSection") == EmptySectionPalette.INSTANCE, "filler palette was not reset");
        require(field(safety, "rotationSection") == EmptySectionPalette.INSTANCE, "rotation palette was not reset");
        require(((BitSet) field(safety, "tickingBlocks")).isEmpty(), "ticking blocks were not reset");
        require(field(safety, "tickingBlocksCount").equals(0), "ticking count was not reset");
        require(field(safety, "localLight") == ChunkLightData.EMPTY, "local light was not reset");
        require(field(safety, "globalLight") == ChunkLightData.EMPTY, "global light was not reset");
        require(field(safety, "localChangeCounter").equals((short) 0), "local counter was not reset");
        require(field(safety, "globalChangeCounter").equals((short) 0), "global counter was not reset");
        Path directory = RecoveryPolicyPath.blockSectionDirectory();
        Path taken = safety.refixes$takeQuarantine();
        require(taken != null && taken.getParent().equals(directory), "mixin did not hand over the quarantine path");
        require(safety.refixes$takeQuarantine() == null, "quarantine path was handed over twice");
        try (Stream<Path> files = Files.list(directory)) {
            Path preserved = files.filter(path -> path.getFileName().toString().endsWith(".bin")).findFirst().orElseThrow();
            require(preserved.equals(taken), "handed-over quarantine path differs from the preserved file");
            require(Arrays.equals(original, Files.readAllBytes(preserved)), "mixin quarantine did not preserve original bytes");
            Files.delete(preserved);
        }
    }

    private static void labelCarriesWorldAndSection() throws Exception {
        Path directory = Files.createTempDirectory("refixes-recovery-label-");
        byte[] original = {5, 6, 7};
        Path quarantined = quarantineThen(original, directory, () -> {});
        Class<?> type = Class.forName(POLICY_CLASS);
        Method label = type.getMethod("label", Path.class, String.class, int.class, int.class, int.class);
        Path labelled = (Path) label.invoke(null, quarantined, "my world/../x", -3, 7, 12);
        String name = labelled.getFileName().toString();
        require(labelled.getParent().equals(directory), "labelled quarantine left its directory");
        require(name.startsWith("block-section-my_world_.._x--3.7.12-") && name.endsWith(".bin"),
                "labelled quarantine name lacks world and section: " + name);
        require(!Files.exists(quarantined), "unlabelled quarantine file was left behind");
        require(Arrays.equals(original, Files.readAllBytes(labelled)), "labelled quarantine changed bytes");
    }

    private static void mixinRefusesResetWhenPreservationFails() throws Exception {
        Path directory = RecoveryPolicyPath.blockSectionDirectory();
        Path quarantineRoot = directory.getParent();
        Files.delete(directory);
        Files.delete(quarantineRoot);
        Files.createFile(quarantineRoot);
        SectionSafety safety = dirtySection();
        BitSet originalTicks = (BitSet) field(safety, "tickingBlocks");
        IllegalStateException decodeFailure = new IllegalStateException("native decode failed");
        try {
            invokeDeserialize(safety, new byte[] {8, 9}, arguments -> {
                throw decodeFailure;
            });
            throw new AssertionError("decode failure was swallowed when quarantine failed");
        } catch (IllegalStateException actual) {
            require(actual == decodeFailure, "quarantine failure replaced the original decode failure");
            require(actual.getSuppressed().length == 1, "quarantine failure context was not attached to the original failure");
        }
        require(safety.invalidations == 0, "section was invalidated without preserved source bytes");
        require(field(safety, "chunkSection") == null, "block palette was reset without preservation");
        require(field(safety, "fillerSection") == null, "filler palette was reset without preservation");
        require(field(safety, "rotationSection") == null, "rotation palette was reset without preservation");
        require(field(safety, "tickingBlocks") == originalTicks && originalTicks.get(11), "ticking state changed without preservation");
        require(field(safety, "tickingBlocksCount").equals(3), "ticking count changed without preservation");
        require(field(safety, "localLight") == null && field(safety, "globalLight") == null, "light changed without preservation");
        require(field(safety, "localChangeCounter").equals((short) 17), "local counter changed without preservation");
        require(field(safety, "globalChangeCounter").equals((short) 19), "global counter changed without preservation");
        Files.delete(quarantineRoot);
    }

    private static SectionSafety dirtySection() throws Exception {
        SectionSafety safety = new SectionSafety();
        set(safety, "tickingBlocks", new BitSet());
        ((BitSet) field(safety, "tickingBlocks")).set(11);
        set(safety, "tickingBlocksCount", 3);
        set(safety, "localChangeCounter", (short) 17);
        set(safety, "globalChangeCounter", (short) 19);
        return safety;
    }

    private static void invokeDeserialize(SectionSafety safety, byte[] bytes, Operation<Void> original) throws Exception {
        Method method = MixinBlockSectionSafety.class.getDeclaredMethod(
                "refixes$safeDeserialize", byte[].class, ExtraInfo.class, Operation.class);
        method.setAccessible(true);
        try {
            method.invoke(safety, bytes, new ExtraInfo(), original);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new AssertionError("unexpected deserialize failure", cause);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = MixinBlockSectionSafety.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = MixinBlockSectionSafety.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Path quarantineThen(byte[] bytes, Path directory, Runnable recovery) throws IOException {
        try {
            Class<?> type = Class.forName(POLICY_CLASS);
            Method method = type.getMethod("quarantineThen", byte[].class, Path.class, Runnable.class);
            return (Path) method.invoke(null, bytes, directory, recovery);
        } catch (ClassNotFoundException | NoSuchMethodException failure) {
            throw new AssertionError("required recovery policy is unavailable", failure);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("recovery policy is not accessible", failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new AssertionError("unexpected recovery failure", cause);
        }
    }

    private static final class RecoveryPolicyPath {
        private static Path blockSectionDirectory() {
            return Path.of("mods", "IroriPowered_Refixes", "quarantine", "block-sections");
        }
    }

    private static final class SectionSafety extends MixinBlockSectionSafety {
        private int invalidations;

        @Override
        public void invalidate() {
            invalidations++;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
