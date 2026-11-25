package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.duck.IndexedWorldOwner;
import cc.irori.refixes.early.util.IndexedWorldStorage;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
public abstract class MixinWorldTransferCleanup extends TickingThread implements IndexedWorldOwner {
    @Shadow
    private void deleteWorldFromDisk() throws IOException {}

    @Unique
    private final IndexedWorldStorage refixes$indexedStorage = new IndexedWorldStorage((World) (Object) this);

    protected MixinWorldTransferCleanup(String threadName) {
        super(threadName);
    }

    @Override
    public IndexedWorldStorage refixes$indexedStorage() {
        return refixes$indexedStorage;
    }

    @IfHytale("<0.8.0-pre.1")
    @WrapMethod(method = "stopIndividualWorld(Ljava/util/Map;)V")
    private void refixes$stopIndexedStorage(Map<UUID, PlayerRef> players, Operation<Void> original) {
        refixes$indexedStorage.stopping(getThread());
        original.call(players);
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @WrapMethod(method = "stopIndividualWorldAsync(Ljava/util/Map;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> refixes$stopIndexedStorageAsync(
            Map<UUID, PlayerRef> players, Operation<CompletableFuture<Void>> original) {
        refixes$indexedStorage.stopping(getThread());
        return original.call(players);
    }

    @WrapMethod(method = "onShutdown")
    private void refixes$sourceClosed(Operation<Void> original) {
        refixes$indexedStorage.stopping(getThread());
        try {
            original.call();
        } finally {
            refixes$indexedStorage.closeRemaining();
        }
    }

    @WrapMethod(method = "validateDeleteOnRemove")
    private void refixes$deleteAfterIndexedStorage(Operation<Void> original) {
        refixes$indexedStorage.stopping(getThread());
        World world = (World) (Object) this;
        if (!EarlyOptions.isMixinEnabled("MixinWorldRetirement")
                && world.getWorldConfig().isDeleteOnRemove()
                && RetiredWorlds.runWhenIdle(world.getSavePath(), world, () -> {
                    try {
                        deleteWorldFromDisk();
                    } catch (Throwable error) {
                        Logs.logger()
                                .atSevere()
                                .withCause(error)
                                .log("Could not delete stopped world '%s'", world.getName());
                    }
                })) return;
        original.call();
    }
}
