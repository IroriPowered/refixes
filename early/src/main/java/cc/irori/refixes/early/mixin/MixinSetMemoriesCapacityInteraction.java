package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.adventure.memories.component.PlayerMemories;
import com.hypixel.hytale.builtin.adventure.memories.interactions.SetMemoriesCapacityInteraction;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SetMemoriesCapacityInteraction.class)
public class MixinSetMemoriesCapacityInteraction {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method = "firstRun",
            at = @At("HEAD"),
            cancellable = true
    )
    private void refixes$validatePlayerRefOnFirstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler, CallbackInfo ci) {
        try {
            PlayerMemories.getComponentType().validate();
        } catch (IllegalStateException e) {
            refixes$LOGGER.atWarning().withCause(e).log("SetMemoriesCapacityInteraction#firstRun(): PlayerMemories component is invalid");
            context.getState().state = InteractionState.Failed;
            ci.cancel();
        }
    }
}
