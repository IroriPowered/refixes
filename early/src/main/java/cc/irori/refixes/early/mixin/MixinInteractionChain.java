package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(InteractionChain.class)
public class MixinInteractionChain {

    @Shadow
    private int tempSyncDataOffset;

    @Shadow
    @Final
    private List<InteractionSyncData> tempSyncData;

    /**
     * @author KabanFriends
     * @reason Handle tempSyncDataOffset gaps gracefully
     */
    @Overwrite
    public void updateSyncPosition(int index) {
        if (index > tempSyncDataOffset) {
            tempSyncData
                    .subList(0, Math.min(index + 1 - tempSyncDataOffset, tempSyncData.size()))
                    .clear();
        }
        if (index >= tempSyncDataOffset) {
            tempSyncDataOffset = index + 1;
        }
    }
}
