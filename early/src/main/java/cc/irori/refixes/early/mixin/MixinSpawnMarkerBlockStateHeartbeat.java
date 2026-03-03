package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.reference.PersistentRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.blockstates.SpawnMarkerBlockStateSystems;
import java.util.UUID;
import java.util.logging.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpawnMarkerBlockStateSystems.TickHeartbeat.class)
public class MixinSpawnMarkerBlockStateHeartbeat {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/entity/reference/PersistentRef;getEntity(Lcom/hypixel/hytale/component/ComponentAccessor;)Lcom/hypixel/hytale/component/Ref;"))
    private Ref<EntityStore> refixes$retryResolve(PersistentRef instance, ComponentAccessor<EntityStore> accessor) {
        Ref<EntityStore> result = instance.getEntity(accessor);
        if (result != null) {
            return result;
        }
        UUID uuid = instance.getUuid();
        if (uuid != null) {
            instance.setUuid(uuid);
            result = instance.getEntity(accessor);
            if (result != null) {
                refixes$LOGGER.at(Level.FINE).log(
                        "SpawnMarkerBlockStateSystems: Recovered stale reference via UUID re-resolve");
            }
        }
        return result;
    }

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
