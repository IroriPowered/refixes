package cc.irori.refixes.system;

import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.builtin.crafting.state.ProcessingBenchState;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class ProcessingBenchFixSystem extends RefSystem<ChunkStore> {

    private static final HytaleLogger LOGGER = Logs.logger();

    @Override
    public void onEntityAdded(
            @NonNullDecl Ref<ChunkStore> ref,
            @NonNullDecl AddReason addReason,
            @NonNullDecl Store<ChunkStore> store,
            @NonNullDecl CommandBuffer<ChunkStore> commandBuffer) {
        // No-op
    }

    @Override
    public void onEntityRemove(
            @NonNullDecl Ref<ChunkStore> ref,
            @NonNullDecl RemoveReason removeReason,
            @NonNullDecl Store<ChunkStore> store,
            @NonNullDecl CommandBuffer<ChunkStore> commandBuffer) {
        if (removeReason == RemoveReason.UNLOAD) {
            return;
        }

        try {
            ProcessingBenchState benchState =
                    store.getComponent(ref, BlockStateModule.get().getComponentType(ProcessingBenchState.class));
            if (benchState == null) return;
            Map<UUID, BenchWindow> windows = benchState.getWindows();
            if (windows.isEmpty()) return;

            LOGGER.atWarning().log("Clearing %d open windows on bench remove", windows.size());
            windows.clear();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to apply processing bench fix");
        }
    }

    @NullableDecl
    @Override
    public Query<ChunkStore> getQuery() {
        return BlockStateModule.get().getComponentType(ProcessingBenchState.class);
    }
}
