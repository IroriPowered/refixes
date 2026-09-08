package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.spawning.blockstates.SpawnMarkerBlockStateSystems;
import java.util.logging.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpawnMarkerBlockStateSystems.TickHeartbeat.class)
public class MixinSpawnMarkerBlockStateHeartbeat {

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/logger/HytaleLogger;at(Ljava/util/logging/Level;)Lcom/hypixel/hytale/logger/HytaleLogger$Api;"))
    private HytaleLogger.Api refixes$downgradeLogLevel(HytaleLogger instance, Level level) {
        return instance.at(Level.WARNING);
    }
}
