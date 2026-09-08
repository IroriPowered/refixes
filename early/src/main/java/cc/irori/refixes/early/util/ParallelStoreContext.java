package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;

public final class ParallelStoreContext {

    private static final ThreadLocal<CommandBuffer<?>> CURRENT = new ThreadLocal<>();

    private ParallelStoreContext() {}

    public static Store<?> current() {
        CommandBuffer<?> buffer = CURRENT.get();
        return buffer == null ? null : buffer.getStore();
    }

    public static CommandBuffer<?> commandBuffer() {
        return CURRENT.get();
    }

    public static CommandBuffer<?> enter(CommandBuffer<?> commandBuffer) {
        CommandBuffer<?> previous = CURRENT.get();
        CURRENT.set(commandBuffer);
        return previous;
    }

    public static void restore(CommandBuffer<?> previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
