package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.commands.worldconfig.WorldPauseCommand;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldPauseCommand.class)
public class MixinWorldPauseCommand {

    @Inject(
            method =
                    "execute(Lcom/hypixel/hytale/server/core/command/system/CommandContext;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/Store;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void refixes$pauseEmptyWorld(
            CommandContext context, World world, Store<EntityStore> store, CallbackInfo ci) {
        if (!Constants.SINGLEPLAYER && world.getPlayerCount() == 0) {
            world.setPaused(!world.isPaused());
            context.sendMessage(Message.translation("server.commands.pause.updated")
                    .param(
                            "state",
                            Message.translation(
                                    world.isPaused()
                                            ? "server.commands.pause.paused"
                                            : "server.commands.pause.unpaused")));
            ci.cancel();
        }
    }
}
