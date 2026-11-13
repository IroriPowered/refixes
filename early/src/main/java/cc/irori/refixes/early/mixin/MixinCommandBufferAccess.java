package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.CommandBufferAccess;
import com.hypixel.hytale.component.CommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandBuffer.class)
public interface MixinCommandBufferAccess extends CommandBufferAccess {
    @Override
    @Invoker("consume")
    void refixes$consume();
}
