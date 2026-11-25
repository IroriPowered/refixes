package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.duck.CubicTransferSection;
import cc.irori.refixes.early.util.CubicTransfer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfHytale("<0.8.0-pre.1")
@Mixin(EntitySection.class)
public class MixinCubicTransferSection implements CubicTransferSection {

    @Unique
    private List<CubicTransfer.Pending> refixes$transfers;

    @Unique
    private Set<UUID> refixes$fence;

    @Override
    public Set<UUID> refixes$peekFence() {
        return refixes$fence;
    }

    @Override
    public Set<UUID> refixes$ensureFence() {
        if (refixes$fence == null) refixes$fence = ConcurrentHashMap.newKeySet(1);
        return refixes$fence;
    }

    @Override
    public List<CubicTransfer.Pending> refixes$peekTransfers() {
        return refixes$transfers;
    }

    @Override
    public List<CubicTransfer.Pending> refixes$ensureTransfers() {
        if (refixes$transfers == null) refixes$transfers = new ArrayList<>(1);
        return refixes$transfers;
    }

    @Inject(method = "removeEntityReference", at = @At("HEAD"))
    private void refixes$transferLeft(Ref<EntityStore> reference, CallbackInfo ci) {
        if (refixes$transfers != null) CubicTransfer.left(refixes$transfers, reference);
    }

    @Inject(method = "loadEntityHolder", at = @At("TAIL"))
    private void refixes$transferParked(Holder<EntityStore> holder, CallbackInfo ci) {
        if (refixes$transfers != null) CubicTransfer.parked(refixes$transfers, holder);
    }

    @Inject(method = "loadEntityReference", at = @At("TAIL"))
    private void refixes$transferResumed(Ref<EntityStore> reference, CallbackInfo ci) {
        if (refixes$transfers != null) CubicTransfer.resumed(refixes$transfers, reference);
        if (refixes$fence != null) CubicTransfer.reentered((EntitySection) (Object) this, reference);
    }

    @Inject(method = "addEntityReference", at = @At("TAIL"))
    private void refixes$transferReentered(Ref<EntityStore> reference, CallbackInfo ci) {
        if (refixes$fence != null) CubicTransfer.reentered((EntitySection) (Object) this, reference);
    }
}
