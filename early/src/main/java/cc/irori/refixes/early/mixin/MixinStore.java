package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import java.lang.reflect.Constructor;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thread-safety patches for Store:
 * 1. Relaxes assertThread() to allow ForkJoinWorkerThread (parallel entity ticking)
 * 2. Disables assertWriteProcessing() when parallel ticking is enabled
 * 3. Makes the command buffer pool (takeCommandBuffer/storeCommandBuffer) thread-safe
 */
@Mixin(Store.class)
public abstract class MixinStore<ECS_TYPE> {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    @Final
    private Thread thread;

    @Shadow
    @Final
    private Deque<CommandBuffer<ECS_TYPE>> commandBuffers;

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

    // Allow ForkJoinPool worker threads used by parallel entity ticking
    @Overwrite
    public void assertThread() {
        Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ForkJoinWorkerThread) {
            return;
        }
        if (!currentThread.equals(this.thread) && this.thread.isAlive()) {
            throw new IllegalStateException("Assert not in thread! " + this.thread + " but was in " + currentThread);
        }
    }

    @Unique
    private static final ThreadLocal<Boolean> refixes$SUPPRESS_WRITE_ASSERT = ThreadLocal.withInitial(() -> false);

    @Inject(method = "assertWriteProcessing", at = @At("HEAD"), cancellable = true)
    private void refixes$disableProcessingAssert(CallbackInfo ci) {
        if (refixes$SUPPRESS_WRITE_ASSERT.get()) {
            ci.cancel();
            return;
        }
        if (EarlyOptions.isAvailable() && EarlyOptions.PARALLEL_ENTITY_TICKING.get()) {
            ci.cancel();
        }
    }

    // synchronize the isEmpty + pop sequence
    @Overwrite
    CommandBuffer<ECS_TYPE> takeCommandBuffer() {
        assertThread();
        synchronized (commandBuffers) {
            if (commandBuffers.isEmpty()) {
                return refixes$newCommandBuffer();
            }
            return commandBuffers.pop();
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
        refixes$SUPPRESS_WRITE_ASSERT.set(true);
        try {
            removeComponentIfExists(ref, componentType);
        } catch (IllegalStateException e) {
            refixes$LOGGER.atWarning().withCause(e).log("Store#tryRemoveComponent(): Failed to remove component");
        } finally {
            refixes$SUPPRESS_WRITE_ASSERT.set(false);
        }
    }

    // Redirects the CompletableFuture.join() call in shutdown0() to use a timeout
    @Redirect(
            method = "shutdown0",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private Object refixes$joinWithTimeout(CompletableFuture<?> future) {
        boolean wasInterrupted = Thread.interrupted(); // clear interrupt flag
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            refixes$LOGGER.atWarning().log(
                    "Store#shutdown0(): saveAllResources timed out after 10s, continuing shutdown");
            return null;
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "Store#shutdown0(): saveAllResources failed, continuing shutdown");
            return null;
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
