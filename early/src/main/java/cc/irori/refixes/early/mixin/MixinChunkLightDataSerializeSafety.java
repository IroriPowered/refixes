package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.util.io.MemorySegmentUtil;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkLightData.class)
public abstract class MixinChunkLightDataSerializeSafety {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    @Final
    private short changeId;

    @Shadow
    @Final
    private MemorySegment lightData;

    @Shadow
    public abstract int serialize(MemorySegment data, int offset);

    @Shadow
    public abstract int serializeForPacket(MemorySegment data, int offset);

    @Inject(method = "serialize(Ljava/lang/foreign/MemorySegment;I)I", at = @At("HEAD"), cancellable = true)
    private void refixes$safeSerialize(MemorySegment data, int offset, CallbackInfoReturnable<Integer> cir) {
        if (refixes$WRAPPING.get()) {
            return;
        }
        refixes$WRAPPING.set(true);
        try {
            cir.setReturnValue(serialize(data, offset));
        } catch (RuntimeException e) {
            MemorySegment ld = this.lightData;
            int n = ld == null ? 0 : (int) (ld.byteSize() / 17L);
            refixes$LOGGER.atWarning().withCause(e).log(
                    "ChunkLightData#serialize(): octree write overflowed buffer (nodes=%d), writing neutral chain fallback",
                    n);
            cir.setReturnValue(refixes$writeNeutralChain(data, offset, true, n));
        } finally {
            refixes$WRAPPING.set(false);
        }
    }

    @Inject(method = "serializeForPacket(Ljava/lang/foreign/MemorySegment;I)I", at = @At("HEAD"), cancellable = true)
    private void refixes$safeSerializeForPacket(MemorySegment data, int offset, CallbackInfoReturnable<Integer> cir) {
        if (refixes$WRAPPING.get()) {
            return;
        }
        refixes$WRAPPING.set(true);
        try {
            cir.setReturnValue(serializeForPacket(data, offset));
        } catch (RuntimeException e) {
            MemorySegment ld = this.lightData;
            int n = ld == null ? 0 : (int) (ld.byteSize() / 17L);
            refixes$LOGGER.atWarning().withCause(e).log(
                    "ChunkLightData#serializeForPacket(): octree write overflowed buffer (nodes=%d), writing neutral chain fallback",
                    n);
            cir.setReturnValue(refixes$writeNeutralChain(data, offset, false, n));
        } finally {
            refixes$WRAPPING.set(false);
        }
    }

    @Unique
    private int refixes$writeNeutralChain(MemorySegment data, int offset, boolean withHeader, int n) {
        if (n <= 0) {
            if (withHeader) {
                data.set(MemorySegmentUtil.SHORT_BE, (long) offset, (short) 0);
                data.set(ValueLayout.JAVA_BOOLEAN, (long) (offset + 2), false);
                return 3;
            } else {
                data.set(ValueLayout.JAVA_BOOLEAN, (long) offset, false);
                return 1;
            }
        }

        int octreeSize = 15 * n + 2;
        int headerSize = withHeader ? 7 : 1;
        int totalSize = headerSize + octreeSize;

        data.asSlice((long) offset, (long) totalSize).fill((byte) 0);

        if (withHeader) {
            data.set(MemorySegmentUtil.SHORT_BE, (long) offset, this.changeId);
            data.set(ValueLayout.JAVA_BOOLEAN, (long) (offset + 2), true);
            data.set(MemorySegmentUtil.INT_BE, (long) (offset + 3), octreeSize);
        } else {
            data.set(ValueLayout.JAVA_BOOLEAN, (long) offset, true);
        }

        int octreeStart = offset + headerSize;
        for (int i = 0; i < n - 1; i++) {
            data.set(ValueLayout.JAVA_BYTE, (long) (octreeStart + i), (byte) 0x01);
        }
        data.set(ValueLayout.JAVA_BYTE, (long) (octreeStart + (n - 1)), (byte) 0x00);

        return totalSize;
    }
}
