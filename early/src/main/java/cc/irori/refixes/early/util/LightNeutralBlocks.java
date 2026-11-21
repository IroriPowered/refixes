package cc.irori.refixes.early.util;

import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

public final class LightNeutralBlocks {

    private static volatile int assetGeneration;

    private static int assetChanges;

    private LightNeutralBlocks() {}

    public static int assetGeneration() {
        return assetGeneration;
    }

    public static synchronized void assetsChanging() {
        assetChanges++;
        assetGeneration = (assetGeneration + 2) | 1;
    }

    public static synchronized void assetsChanged() {
        if (--assetChanges == 0) assetGeneration += 1;
    }

    public static boolean neutral(int oldId, int newId) {
        if (oldId == newId) return true;
        if (oldId == 0 || newId == 0) return false;
        var assets = BlockType.getAssetMap();
        BlockType oldType = assets.getAsset(oldId);
        BlockType newType = assets.getAsset(newId);
        if (oldType == null || newType == null) return false;
        return oldType.getOpacity() == newType.getOpacity() && sameLight(oldType.getLight(), newType.getLight());
    }

    private static boolean sameLight(ColorLight a, ColorLight b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.red == b.red && a.green == b.green && a.blue == b.blue;
    }
}
