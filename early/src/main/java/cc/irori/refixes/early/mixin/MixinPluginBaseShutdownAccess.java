package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.PluginBaseShutdownAccess;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PluginBase.class)
public interface MixinPluginBaseShutdownAccess extends PluginBaseShutdownAccess {
    @Override
    @Invoker("shutdown0")
    void refixes$shutdown0(boolean shutdown);
}
