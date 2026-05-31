package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.collision.CollisionConfig;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CollisionConfig.class)
public class MixinCollisionConfig {

    @Unique
    private Ref<ChunkStore> refixes$fluidRef;

    @Unique
    private Component<ChunkStore> refixes$fluidSection;

    @Redirect(
            method = "canCollide(III)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;"))
    private Component<ChunkStore> refixes$cacheFluidSection(
            Store<ChunkStore> store, Ref<ChunkStore> ref, ComponentType<ChunkStore, ?> componentType) {
        if (ref == this.refixes$fluidRef) {
            return this.refixes$fluidSection;
        }
        Component<ChunkStore> section = store.getComponent(ref, componentType);
        this.refixes$fluidRef = ref;
        this.refixes$fluidSection = section;
        return section;
    }
}
