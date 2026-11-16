package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.WorldHeight;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public abstract class MixinWorldHeightBounds {

    @WrapOperation(
            method = "onSetupPlayerJoining",
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
