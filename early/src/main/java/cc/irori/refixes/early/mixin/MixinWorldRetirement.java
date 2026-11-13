package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldRetirement extends TickingThread {

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    private void deleteWorldFromDisk() throws IOException {}

    @Unique
    private volatile boolean refixes$quiesced;

    protected MixinWorldRetirement(String threadName) {
        super(threadName);
    }

    @WrapMethod(method = "stopIndividualWorld(Ljava/util/Map;)V")
    private void refixes$retireRunningThread(Map<UUID, PlayerRef> players, Operation<Void> original) {
        World world = (World) (Object) this;
        Thread thread = isAlive() && !refixes$quiesced ? getThread() : null;
        Path reserved = RetiredWorlds.reserve(world, thread);
        try {
            original.call(players);
        } finally {
            RetiredWorlds.retire(reserved, world, thread);
        }
    }

    @Inject(
            method = "onShutdown",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicBoolean;set(Z)V"),
            require = 1,
            allow = 1)
    private void refixes$releaseQuiescedFolder(CallbackInfo ci) {
        refixes$quiesced = true;
        RetiredWorlds.quiesce((World) (Object) this);
    }

    @WrapMethod(method = "validateDeleteOnRemove")
    private void refixes$deleteAfterRetirement(Operation<Void> original) {
        World world = (World) (Object) this;
        Thread thread = getThread();
        if (!refixes$quiesced && (thread == null || !thread.isAlive())) RetiredWorlds.drain(world);
        if (!world.getWorldConfig().isDeleteOnRemove()
                || !RetiredWorlds.runWhenIdle(world.getSavePath(), world, () -> {
                    try {
                        deleteWorldFromDisk();
                    } catch (Throwable t) {
                        Logs.logger()
                                .atSevere()
                                .withCause(t)
                                .log("Exception while deleting world '%s' after it stopped", world.getName());
                    }
                })) {
            original.call();
            return;
        }
        Logs.logger().atWarning().log("Deleting world '%s' after its previous generation finishes", world.getName());
    }
}
