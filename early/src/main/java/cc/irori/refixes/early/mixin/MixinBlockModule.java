package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Optionally disables BlockModule's ChunkPreLoadProcessEvent handler.

@Mixin(BlockModule.class)
public class MixinBlockModule {

    @ModifyArg(
            method = "setup",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/event/EventRegistry;registerGlobal(Lcom/hypixel/hytale/event/EventPriority;Ljava/lang/Class;Ljava/util/function/Consumer;)Lcom/hypixel/hytale/event/EventRegistration;"),
            index = 2)
    private Consumer<ChunkPreLoadProcessEvent> refixes$asyncBlockChunkPreProcess(
            Consumer<ChunkPreLoadProcessEvent> consumer) {
        return event -> {};
    }
}
