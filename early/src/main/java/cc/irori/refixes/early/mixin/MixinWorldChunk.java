package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.common.util.CompletableFutureUtil;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class MixinWorldChunk {

    @Shadow
    private World world;

    @Inject(method = "setState", at = @At("HEAD"), cancellable = true)
    private void refixes$deferSetStateOffWorldThread(
            int x,
            int y,
            int z,
            BlockType blockType,
            int rotation,
            @Nullable Holder<ChunkStore> holder,
            CallbackInfo ci) {
        Thread current = Thread.currentThread();
        if (current instanceof ForkJoinWorkerThread fjwt && fjwt.getPool() == ForkJoinPool.commonPool()) {
            ci.cancel();
            if (y >= 0 && y < 320 && blockType != null) {
                CompletableFutureUtil._catch(CompletableFuture.runAsync(
                        () -> ((WorldChunk) (Object) this).setState(x, y, z, blockType, rotation, holder), this.world));
            }
        }
    }
}
