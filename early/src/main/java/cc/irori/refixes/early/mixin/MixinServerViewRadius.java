package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ServerViewRadiusAccess;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HytaleServerConfig.class)
public abstract class MixinServerViewRadius implements ServerViewRadiusAccess {

    @Unique
    private long refixes$viewRadiusRevision;

    @WrapMethod(method = "setMaxViewRadius", require = 1)
    private void refixes$trackViewRadius(int radius, Operation<Void> original) {
        synchronized (this) {
            original.call(radius);
            refixes$viewRadiusRevision++;
        }
    }

    @Override
    public synchronized long refixes$getViewRadiusRevision() {
        return refixes$viewRadiusRevision;
    }
}
