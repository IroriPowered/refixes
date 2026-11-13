package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.CommandBufferAccess;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.ArchetypeTickingSystem;
import com.hypixel.hytale.component.system.tick.TickableSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Store.class)
public abstract class MixinStoreTickGuard {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final long refixes$RELOG_INTERVAL_NANOS = 300_000_000_000L;

    @Unique
    private final Map<TickableSystem<?>, Long> refixes$lastLogNanos = new IdentityHashMap<>();

    @Unique
    private static final long refixes$SAVE_FAILURE_WINDOW_NANOS = 300_000_000_000L;

    @Unique
    private static final int refixes$MAX_SAVE_FAILURES = 3;

    @Unique
    private final Map<TickableSystem<?>, long[]> refixes$saveFailureWindows = new IdentityHashMap<>();

    @Redirect(
            method = "tickInternal",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/system/tick/TickableSystem;tick(FILcom/hypixel/hytale/component/Store;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void refixes$guardSystemTick(TickableSystem system, float dt, int systemIndex, Store store) {
        try {
            system.tick(dt, systemIndex, store);
        } catch (Exception t) {
            if (refixes$isPersistenceSystem(system)) {
                if (refixes$persistenceFailureShouldEscalate(system, t)) {
                    refixes$sneakyThrow(t);
                }
                return;
            }
            refixes$reportFailed(system, t);
        }
    }

    @ModifyExpressionValue(
            method = "tick(Lcom/hypixel/hytale/component/system/tick/ArchetypeTickingSystem;FI)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;takeCommandBuffer()Lcom/hypixel/hytale/component/CommandBuffer;"),
            require = 1)
    private CommandBuffer<?> refixes$trackCommandBuffer(
            CommandBuffer<?> buffer, @Share("buffer") LocalRef<CommandBuffer<?>> pending) {
        pending.set(buffer);
        return buffer;
    }

    @Inject(
            method = "tick(Lcom/hypixel/hytale/component/system/tick/ArchetypeTickingSystem;FI)V",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/component/CommandBuffer;consume()V"),
            require = 1)
    private void refixes$commandBufferConsumed(CallbackInfo ci, @Share("buffer") LocalRef<CommandBuffer<?>> pending) {
        pending.set(null);
    }

    @WrapMethod(method = "tick(Lcom/hypixel/hytale/component/system/tick/ArchetypeTickingSystem;FI)V", require = 1)
    @SuppressWarnings("rawtypes")
    private void refixes$consumeAfterFailure(
            ArchetypeTickingSystem system,
            float dt,
            int systemIndex,
            Operation<Void> original,
            @Share("buffer") LocalRef<CommandBuffer<?>> pending) {
        try {
            original.call(system, dt, systemIndex);
        } catch (Exception t) {
            CommandBuffer<?> buffer = pending.get();
            if (buffer != null) {
                pending.set(null);
                try {
                    ((CommandBufferAccess) buffer).refixes$consume();
                } catch (Exception consumeFailure) {
                    t.addSuppressed(consumeFailure);
                }
            }
            throw t;
        }
    }

    @Unique
    private static boolean refixes$isPersistenceSystem(TickableSystem system) {
        String name = system.getClass().getName();
        return name.contains("Saving") || name.contains("Save") || name.endsWith(".WorldEventStorageSystem");
    }

    // Returns true if the save has failed too many times within the window (caller should rethrow to crash).
    // Otherwise swallows the failure: the world keeps running and the save retries next interval.
    @Unique
    private boolean refixes$persistenceFailureShouldEscalate(TickableSystem system, Throwable t) {
        String name = system.getClass().getName();
        long now = System.nanoTime();
        long[] window = refixes$saveFailureWindows.computeIfAbsent(system, k -> new long[] {now, 0});
        boolean escalate;
        synchronized (window) {
            if (now - window[0] > refixes$SAVE_FAILURE_WINDOW_NANOS) {
                window[0] = now;
                window[1] = 1;
            } else {
                window[1]++;
            }
            escalate = window[1] >= refixes$MAX_SAVE_FAILURES;
        }
        if (escalate) {
            refixes$saveFailureWindows.remove(system);
            refixes$LOGGER.at(Level.SEVERE).withCause(t).log(
                    "%s",
                    "Refixes TickSurvival: persistence system " + name + " failed "
                            + refixes$MAX_SAVE_FAILURES + " times; letting it crash so the watchdog can"
                            + " recover the world from the last good save instead of running unsaved.");
        } else {
            refixes$LOGGER.at(Level.WARNING).withCause(t).log(
                    "%s",
                    "Refixes TickSurvival: persistence system " + name + " failed to save; keeping the"
                            + " world alive and retrying next save interval.");
        }
        return escalate;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void refixes$sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }

    @Unique
    private void refixes$reportFailed(TickableSystem system, Throwable t) {
        String name = system.getClass().getName();
        long now = System.nanoTime();
        Long prev = refixes$lastLogNanos.get(system);
        if (prev != null && now - prev < refixes$RELOG_INTERVAL_NANOS) {
            return;
        }
        refixes$lastLogNanos.put(system, now);
        PluginIdentifier culprit = PluginIdentifier.identifyThirdPartyPlugin(t);
        String suffix = culprit == null ? "" : " (likely caused by plugin " + culprit.getName() + ")";
        refixes$LOGGER.at(Level.SEVERE).withCause(t).log(
                "%s",
                "Refixes TickSurvival: system " + name + " threw" + suffix
                        + "; aborted this system's tick after partial execution; commands it queued before"
                        + " the failure were applied and no rollback was performed."
                        + " Fix the system or disable Mixins.Recovery.TickSurvival.");
    }
}
