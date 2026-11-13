package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.component.SectionUnloadingSystem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionUnloadingSystem.class)
public class MixinSectionUnloadingSystem {

    // Protect the sections of the spawn column at origin (0,0)
    @WrapOperation(
            method =
                    "poll(Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkSection;Lcom/hypixel/hytale/server/core/modules/entity/player/ChunkTracker$ChunkVisibility;ZZI)Lcom/hypixel/hytale/server/core/universe/world/storage/component/SectionUnloadingSystem$Poll;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkSection;shouldKeepLoaded()Z"),
            require = 1,
            allow = 1)
    private static boolean refixes$protectSpawnSections(ChunkSection section, Operation<Boolean> original) {
        if (original.call(section)) {
            return true;
        }
        return EarlyOptions.isAvailable()
                && EarlyOptions.VANILLA_KEEP_SPAWN_LOADED.get()
                && section.getX() == 0
                && section.getZ() == 0
                && section.getY() >= 0
                && section.getY() < ChunkUtil.HEIGHT_SECTIONS;
    }
}
