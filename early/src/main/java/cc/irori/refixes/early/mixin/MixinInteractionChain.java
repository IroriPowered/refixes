package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.entity.InteractionChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(InteractionChain.class)
public class MixinInteractionChain {

    @Shadow
    private int tempSyncDataOffset;

    /**
     * @author KabanFriends
     * @reason Handle tempSyncDataOffset gaps gracefully
     */
    @Overwrite
    public void updateSyncPosition(int index) {
        if (index >= tempSyncDataOffset) {
            tempSyncDataOffset = index + 1;
        }
    }
}
