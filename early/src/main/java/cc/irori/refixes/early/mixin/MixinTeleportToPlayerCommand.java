package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.teleport.commands.teleport.variant.TeleportToPlayerCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TeleportToPlayerCommand.class)
public class MixinTeleportToPlayerCommand {

    @Inject(method = "lambda$execute$1", at = @At("HEAD"), cancellable = true)
    private static void refixes$skipStaleRef(
            World targetWorld,
            Transform targetTransform,
            Store<?> store,
            Ref<?> ref,
            CommandContext context,
            String targetUsername,
            World world,
            Vector3d pos,
            Rotation3f rotation,
            CallbackInfo ci) {
        if (ref == null || !ref.isValid()) {
            ci.cancel();
        }
    }
}
