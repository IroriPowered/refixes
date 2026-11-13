package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.transport.Transport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerManager.class)
public abstract class MixinServerManagerShutdown {

    @WrapOperation(
            method = "shutdown()V",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/io/transport/Transport;shutdown()V"),
            require = 1,
            allow = 1)
    private void refixes$skipUnboundTransport(Transport transport, Operation<Void> original) {
        if (transport != null) original.call(transport);
    }
}
