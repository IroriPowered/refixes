package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.fluid.FluidPlugin;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FluidPlugin.class)
public class MixinFluidPlugin {

    @ModifyArg(
            method = "setup",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/event/EventRegistry;registerGlobal(Lcom/hypixel/hytale/event/EventPriority;Ljava/lang/Class;Ljava/util/function/Consumer;)Lcom/hypixel/hytale/event/EventRegistration;"),
            index = 2)
    private Consumer<ChunkPreLoadProcessEvent> refixes$disableFluidChunkPreProcess(
            Consumer<ChunkPreLoadProcessEvent> consumer) {
        return event -> {};
    }
}
