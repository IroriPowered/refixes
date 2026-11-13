package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.assets.spawnmarker.config.SpawnMarker;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnMarkerEntity.class)
public class MixinSpawnMarkerSpawnSaving {

    @Inject(
            method =
                    "spawnNPC(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/spawning/assets/spawnmarker/config/SpawnMarker;Ljava/util/List;Lcom/hypixel/hytale/component/Store;)Z",
            at = @At("TAIL"),
            require = 1)
    private void refixes$saveSpawnedMarker(
            Ref<EntityStore> ref,
            SpawnMarker marker,
            List<Ref<EntityStore>> outSpawnedList,
            Store<EntityStore> store,
            CallbackInfoReturnable<Boolean> cir) {
        Dirty dirty = store.getComponent(ref, Dirty.getComponentType());
        if (dirty != null) dirty.forceMarkDirty();
    }
}
