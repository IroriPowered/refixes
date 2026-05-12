package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.copychunks.CopyChunksCommand;
import cc.irori.refixes.early.copychunks.PasteChunksCommand;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuilderToolsPlugin.class)
public abstract class MixinBuilderToolsPlugin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void refixes$registerCopyChunks(CallbackInfo ci) {
        CommandRegistry registry = ((PluginBase) (Object) this).getCommandRegistry();
        registry.registerCommand(new CopyChunksCommand());
        registry.registerCommand(new PasteChunksCommand());
    }
}
