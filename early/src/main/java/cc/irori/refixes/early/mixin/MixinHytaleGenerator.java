package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.builtin.hytalegenerator.plugin.HytaleGenerator;
import java.util.concurrent.Semaphore;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HytaleGenerator.class)
public class MixinHytaleGenerator {

    @Shadow
    @Final
    @Mutable
    private Semaphore chunkGenerationSemaphore;

    @Inject(method = "start", at = @At("HEAD"))
    private void refixes$increaseChunkGenerationPermits(CallbackInfo ci) {
        if (EarlyOptions.isAvailable()) {
            int permits = EarlyOptions.CHUNK_GENERATION_PERMITS.get();
            this.chunkGenerationSemaphore = new Semaphore(permits);
        }
    }
}
