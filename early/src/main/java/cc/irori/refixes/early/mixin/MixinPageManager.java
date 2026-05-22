package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PageManager.class)
public abstract class MixinPageManager {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method = "handleEvent",
            at = @At(value = "NEW", target = "java/lang/IllegalArgumentException"),
            cancellable = true)
    private void refixes$swallowUnexpectedAck(
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            CustomPageEvent event,
            CallbackInfo ci) {
        refixes$LOGGER.atWarning().log(
                "PageManager#handleEvent: ignoring unexpected client acknowledgement");
        ci.cancel();
    }
}
