package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.ParallelStoreContext;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import java.lang.reflect.Constructor;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Store.class)
public abstract class MixinStore<ECS_TYPE> {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    // Bound stack-walk overhead: capture at most one violating call site per this interval, per assert kind.
    @Unique
    private static final long refixes$CAPTURE_INTERVAL_NANOS = 50_000_000L;

    // Re-log a given call site at most once per this interval, so warnings never go permanently silent.
    @Unique
    private static final long refixes$RELOG_INTERVAL_NANOS = 300_000_000_000L;

    @Unique
    private static volatile long refixes$lastThreadCaptureNanos;

    @Unique
    private static final Map<String, Long> refixes$siteLastLogNanos = new ConcurrentHashMap<>();

    @Shadow
    @Final
    private Thread thread;

    @Shadow
    @Final
    private Deque<CommandBuffer<ECS_TYPE>> commandBuffers;

    @Shadow
    public abstract boolean isProcessing();

    @Shadow
    public abstract <T extends Component<ECS_TYPE>> boolean removeComponentIfExists(
            @NonNullDecl Ref<ECS_TYPE> ref, @NonNullDecl ComponentType<ECS_TYPE, T> componentType);

    @Unique
    private static volatile Constructor<?> refixes$cbCtor;

    @Unique
    @SuppressWarnings("unchecked")
    private CommandBuffer<ECS_TYPE> refixes$newCommandBuffer() {
        try {
            Constructor<?> ctor = refixes$cbCtor;
            if (ctor == null) {
                ctor = CommandBuffer.class.getDeclaredConstructor(Store.class);
                ctor.setAccessible(true);
                refixes$cbCtor = ctor;
            }
            return (CommandBuffer<ECS_TYPE>) ctor.newInstance((Store<ECS_TYPE>) (Object) this);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create CommandBuffer", e);
        }
    }

    @Overwrite
    public void assertThread() {
        Thread currentThread = Thread.currentThread();
        if (currentThread != this.thread
                && this.thread.isAlive()
                && ParallelStoreContext.current() == (Object) this
                && isProcessing()) {
            long now = System.nanoTime();
            if (now - refixes$lastThreadCaptureNanos >= refixes$CAPTURE_INTERVAL_NANOS) {
                refixes$lastThreadCaptureNanos = now;
                refixes$reportRelaxed("assertThread");
            }
            return;
        }
        if (!currentThread.equals(this.thread) && this.thread.isAlive()) {
            throw new IllegalStateException("Assert not in thread! " + this.thread + " but was in " + currentThread);
        }
    }

    @Inject(
            method = {"assertWriteProcessing", "forEachEntityParallel", "fetch", "tick", "pausedTick", "tickInternal"},
            at = @At("HEAD"))
    private void refixes$requireOwner(CallbackInfo ci) {
        Thread currentThread = Thread.currentThread();
        if (currentThread != this.thread && this.thread.isAlive()) {
            throw new IllegalStateException("Store mutation and task scheduling require the owner thread! "
                    + this.thread + " but was in " + currentThread);
        }
    }

    // Logs each distinct violating call site once per RELOG_INTERVAL with a trimmed stack, so operators can
    // see exactly which systems do unsafe concurrent access (and fix them) instead of a single generic line.
    @Unique
    private static void refixes$reportRelaxed(String assertName) {
        String site = refixes$callSite();
        String key = assertName + '|' + site;
        long now = System.nanoTime();
        Long prev = refixes$siteLastLogNanos.get(key);
        if (prev != null && now - prev < refixes$RELOG_INTERVAL_NANOS) {
            return;
        }
        refixes$siteLastLogNanos.put(key, now);
        refixes$LOGGER.atWarning().log(
                "%s",
                "Parallel ticking relaxed Store#" + assertName + " (thread "
                        + Thread.currentThread().getName()
                        + ") — engine thread-safety checks are downgraded; concurrent access is NOT made"
                        + " safe. Make this call site thread-safe or disable"
                        + " Experimental.Parallel.AllSystems.\n    at " + site);
    }

    @Unique
    private static String refixes$callSite() {
        return StackWalker.getInstance().walk(frames -> frames.map(StackWalker.StackFrame::toStackTraceElement)
                .filter(e -> {
                    String c = e.getClassName();
                    return !c.startsWith("java.")
                            && !c.startsWith("jdk.")
                            && !c.equals("cc.irori.refixes.early.mixin.MixinStore");
                })
                .limit(8)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n    at ")));
    }

    // synchronize the isEmpty + pop sequence
    @Overwrite
    CommandBuffer<ECS_TYPE> takeCommandBuffer() {
        assertThread();
        synchronized (commandBuffers) {
            if (commandBuffers.isEmpty()) {
                return refixes$newCommandBuffer();
            }
            CommandBuffer<ECS_TYPE> buffer = commandBuffers.pop();
            assert buffer.setThread();
            return buffer;
        }
    }

    @Overwrite
    void storeCommandBuffer(CommandBuffer<ECS_TYPE> commandBuffer) {
        assertThread();
        commandBuffer.validateEmpty();
        synchronized (commandBuffers) {
            commandBuffers.add(commandBuffer);
        }
    }

    @Overwrite
    public <T extends Component<ECS_TYPE>> void tryRemoveComponent(
            @Nonnull Ref<ECS_TYPE> ref, @Nonnull ComponentType<ECS_TYPE, T> componentType) {
        if (!ref.isValid() || ref.getStore() != (Object) this) {
            return;
        }
        removeComponentIfExists(ref, componentType);
    }
}
