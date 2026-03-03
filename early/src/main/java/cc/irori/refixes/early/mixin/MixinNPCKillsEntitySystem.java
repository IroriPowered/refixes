package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.NPCDeathSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NPCDeathSystems.NPCKillsEntitySystem.class)
public class MixinNPCKillsEntitySystem {

    @Redirect(
            method = "onComponentAdded",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;",
                            ordinal = 0))
    private <T extends Component<EntityStore>> T refixes$guardKillerRef(
            Store<EntityStore> store, Ref<EntityStore> ref, ComponentType<EntityStore, T> type) {
        if (!ref.isValid()) {
            return null;
        }
        return store.getComponent(ref, type);
    }
}
