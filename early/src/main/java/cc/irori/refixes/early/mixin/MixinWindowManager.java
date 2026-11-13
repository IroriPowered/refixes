package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.WindowAccess;
import com.hypixel.fastutil.ints.Int2ObjectConcurrentHashMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.packets.window.CloseWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WindowManager.class)
public abstract class MixinWindowManager {

    @Shadow
    @Final
    private Int2ObjectConcurrentHashMap<Window> windows;

    @Shadow
    @Final
    private Int2ObjectConcurrentHashMap<EventRegistration<?, ?>> windowChangeEvents;

    @Shadow
    private PlayerRef playerRef;

    @WrapOperation(
            method = {"openWindow", "clientOpenWindow"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/entity/entities/player/windows/Window;onOpen(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Store;)Z"),
            require = 2)
    private boolean refixes$rollbackFailedOpen(
            Window window, Ref<EntityStore> ref, Store<EntityStore> store, Operation<Boolean> original) {
        int id = window.getId();
        try {
            return original.call(window, ref, store);
        } catch (Throwable failure) {
            if (windows.get(id) == window && windows.remove(id, window)) {
                try {
                    playerRef.getPacketHandler().writeNoCache(new CloseWindow(id));
                } catch (Throwable cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
                try {
                    EventRegistration<?, ?> registration = windowChangeEvents.remove(id);
                    if (registration != null) registration.unregister();
                } catch (Throwable cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
                try {
                    ((WindowAccess) window).refixes$onClose(ref, store);
                } catch (Throwable cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
            }
            if (window.getId() == id && windows.get(id) != window) window.setId(-1);
            throw failure;
        }
    }
}
