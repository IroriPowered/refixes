package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {
    @Shadow
    @Final
    private EntityStore entityStore;

    @Inject(method = "getPlayers()Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void refixes$nullGuardGetPlayers(CallbackInfoReturnable<List<Player>> cir) {
        if (this.entityStore == null || this.entityStore.getStore() == null) {
            cir.setReturnValue(Collections.emptyList());
        }
    }
}
