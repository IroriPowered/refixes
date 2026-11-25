package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.accessor.LoadStateAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfHytale("<0.8.0-pre.1")
@Mixin(targets = "com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid$LoadState")
public interface MixinLoadStateAccess extends LoadStateAccess {

    @Override
    @Accessor("lock")
    StampedLock refixes$getLock();

    @Override
    @Accessor("flags")
    int refixes$getFlags();

    @Override
    @Accessor("future")
    CompletableFuture<Ref<ChunkStore>> refixes$getFuture();

    @Override
    @Accessor("reference")
    Ref<ChunkStore> refixes$getReference();
}
