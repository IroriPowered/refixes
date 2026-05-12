package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.copychunks.CopyChunksCommand;
import cc.irori.refixes.early.copychunks.PasteChunksCommand;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Registers /copychunks and /pastechunks alongside the builtin /pos1, /set, etc.
@Mixin(BuilderToolsPlugin.class)
public abstract class MixinBuilderToolsPlugin {

    @Shadow
    @Nonnull
    public abstract CommandRegistry getCommandRegistry();

    @Inject(method = "setup", at = @At("TAIL"))
    private void refixes$registerCopyChunks(CallbackInfo ci) {
        CommandRegistry registry = getCommandRegistry();
        registry.registerCommand(new CopyChunksCommand());
        registry.registerCommand(new PasteChunksCommand());
    }
}
