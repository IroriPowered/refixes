package cc.irori.refixes.util;

import cc.irori.refixes.early.accessor.PlayerRadiusAccess;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;

public final class PlayerRadiusRevisions {

    private PlayerRadiusRevisions() {}

    public static long hot(ChunkTracker tracker) {
        return Early.isEnabled() ? Access.hot(tracker) : -1;
    }

    public static long min(ChunkTracker tracker) {
        return Early.isEnabled() ? Access.min(tracker) : -1;
    }

    public static long view(ChunkTracker tracker) {
        return Early.isEnabled() ? Access.view(tracker) : -1;
    }

    public static void restoreHot(ChunkTracker tracker, int radius, long revision) {
        tracker.setMaxHotLoadedRadius(radius);
        if (revision >= 0 && Early.isEnabled()) Access.restoreHot(tracker, revision);
    }

    private static final class Access {
        private static long hot(ChunkTracker tracker) {
            return tracker instanceof PlayerRadiusAccess access ? access.refixes$getHotRadiusRevision() : -1;
        }

        private static void restoreHot(ChunkTracker tracker, long revision) {
            if (tracker instanceof PlayerRadiusAccess access) access.refixes$restoreHotRadiusRevision(revision);
        }

        private static long min(ChunkTracker tracker) {
            return tracker instanceof PlayerRadiusAccess access ? access.refixes$getMinRadiusRevision() : -1;
        }

        private static long view(ChunkTracker tracker) {
            return tracker instanceof PlayerRadiusAccess access ? access.refixes$getViewRadiusRevision() : -1;
        }
    }
}
