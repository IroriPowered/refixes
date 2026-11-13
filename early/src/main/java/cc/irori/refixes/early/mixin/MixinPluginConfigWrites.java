package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PluginConfigWrites;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.util.Config;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PluginBase.class)
public abstract class MixinPluginConfigWrites {
    @Shadow
    @Final
    private List<Config<?>> configs;

    @WrapMethod(method = "shutdown0", require = 1)
    private void refixes$retireConfigWrites(boolean shutdown, Operation<Void> original) {
        try {
            original.call(shutdown);
        } finally {
            PluginConfigWrites.retire(((PluginBase) (Object) this).getIdentifier(), configs);
        }
    }
}
