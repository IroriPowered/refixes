package cc.irori.refixes.util;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ChunkSelection {

    public static final class Region {
        public final Vector3i min;
        public final Vector3i max;
        public final int minChunkX;
        public final int minChunkZ;
        public final int maxChunkX;
        public final int maxChunkZ;

        private Region(Vector3i min, Vector3i max) {
            this.min = min;
            this.max = max;
            this.minChunkX = min.x >> 5;
            this.minChunkZ = min.z >> 5;
            this.maxChunkX = max.x >> 5;
            this.maxChunkZ = max.z >> 5;
        }

        public int chunkCount() {
            return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        }
    }

    private ChunkSelection() {}

    @Nullable
    public static Region of(Player player, PlayerRef playerRef) {
        BuilderToolsPlugin.BuilderState state = BuilderToolsPlugin.getState(player, playerRef);
        BlockSelection selection = state.getSelection();
        if (selection == null) {
            return null;
        }
        Vector3i min = selection.getSelectionMin();
        Vector3i max = selection.getSelectionMax();
        if (min == null || max == null) {
            return null;
        }
        return new Region(min, max);
    }
}
