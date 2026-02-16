package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingManager.class)
public interface MixinCraftingManagerAccessor {

    @Accessor
    BlockType getBlockType();
}
