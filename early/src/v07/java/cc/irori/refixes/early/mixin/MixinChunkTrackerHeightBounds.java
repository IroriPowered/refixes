package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.WorldHeight;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(ChunkTracker.class)
public abstract class MixinChunkTrackerHeightBounds {

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/ChunkStore;supportsCubicSections()Z"),
            require = 1,
            allow = 1)
    private boolean refixes$boundColumnWorlds(ChunkStore store, Operation<Boolean> original) {
        return WorldHeight.isUnbounded(store, original.call(store));
    }
}
