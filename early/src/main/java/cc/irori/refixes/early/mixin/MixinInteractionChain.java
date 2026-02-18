package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import java.util.List;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InteractionChain.class)
public class MixinInteractionChain {

    @Shadow
    private int tempSyncDataOffset;

    @Shadow
    @Final
    @Nonnull
    private List<InteractionSyncData> tempSyncData;

    @ModifyVariable(method = "putInteractionSyncData", at = @At("STORE"), ordinal = 0, argsOnly = true)
    private int refixes$fixInteractionDataIndex(int index) {
        if (index < 0) {
            for (int i = 0; i < -index; i++) {
                tempSyncData.addFirst(null);
            }
            tempSyncDataOffset += index;
            return 0;
        }
        return index;
    }

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
