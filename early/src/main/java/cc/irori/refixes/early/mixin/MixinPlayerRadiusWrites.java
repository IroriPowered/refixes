package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.PlayerRadiusAccess;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Player.class)
public abstract class MixinPlayerRadiusWrites {

    @Shadow
    private PlayerRef playerRef;

    @WrapMethod(method = "setClientViewRadius", require = 1)
    private void refixes$trackViewRadius(int radius, Operation<Void> original) {
        if (playerRef == null) {
            original.call(radius);
            return;
        }
        ChunkTracker tracker = playerRef.getChunkTracker();
        synchronized (tracker) {
            original.call(radius);
            ((PlayerRadiusAccess) tracker).refixes$recordViewRadiusWrite();
        }
    }
}
