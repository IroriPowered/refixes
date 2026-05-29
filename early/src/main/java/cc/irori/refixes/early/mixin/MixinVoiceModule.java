package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VoiceModule.class)
public class MixinVoiceModule {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(
            method = "lambda$updateAllPlayerPositions$1",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/EntityStore;getWorld()Lcom/hypixel/hytale/server/core/universe/world/World;"))
    private World refixes$skipIfPlayerMovedWorlds(EntityStore externalData) {
        World freshWorld = externalData.getWorld();
        if (freshWorld != null && !freshWorld.isInThread()) {
            refixes$LOGGER.atFine().log(
                    "VoiceModule: skipped position update for player no longer on this world's thread");
            return null;
        }
        return freshWorld;
    }
}
