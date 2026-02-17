package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.task.ParallelRangeTask;
import com.hypixel.hytale.logger.HytaleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Enables parallel entity ticking for systems that opt in via isParallel()
 * This patch makes maybeUseParallel return true when the chunk is large enough to benefit from parallelism
 */
@Mixin(EntityTickingSystem.class)
public class MixinEntityTickingSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final boolean refixes$ENABLED =
            Boolean.parseBoolean(System.getProperty("refixes.parallelTicking", "true"));

    static {
        refixes$LOGGER.atInfo().log(
                "EntityTickingSystem parallel ticking: %s", refixes$ENABLED ? "ENABLED" : "DISABLED");
    }

    // Enable parallel entity ticking when chunk size is large enough
    @Overwrite
    protected static boolean maybeUseParallel(int archetypeChunkSize, int taskCount) {
        if (!refixes$ENABLED) {
            return false;
        }
        return taskCount > 0 || archetypeChunkSize > ParallelRangeTask.PARALLELISM;
    }
}
