package cc.irori.refixes.early.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.hypixel.hytale.component.Store$ProcessingCounter")
public abstract class MixinStoreProcessingCounter {

    @Shadow
    private int count;

    @Overwrite
    public synchronized boolean isHeld() {
        return count > 0;
    }

    @Overwrite
    public synchronized void lock() {
        count++;
    }

    @Overwrite
    public synchronized void unlock() {
        count--;
    }
}
