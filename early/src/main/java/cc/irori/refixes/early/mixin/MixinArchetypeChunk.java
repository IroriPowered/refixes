package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArchetypeChunk.class)
public class MixinArchetypeChunk {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method = "copySerializableEntity",
            at = @At(value = "NEW", target = "(I)Ljava/lang/IndexOutOfBoundsException;"),
            cancellable = true)
    private void refixes$ignoreCopySerializableEntityOutOfBounds(
            ComponentRegistry.Data<?> data, int entityIndex, Holder<?> target, CallbackInfoReturnable<Holder<?>> cir) {
        refixes$LOGGER.atWarning().log(
                "ArchetypeChunk#copySerializableEntity(): Entity index out of bounds (%d)", entityIndex);
        cir.setReturnValue(null);
    }

    @Inject(
            method = "__internal_getComponent",
            at = @At(value = "NEW", target = "(I)Ljava/lang/IndexOutOfBoundsException;"),
            cancellable = true)
    private void refixes$ignoreGetComponentOutOfBounds(
            int index, ComponentType<?, ?> componentType, CallbackInfoReturnable<?> cir) {
        refixes$LOGGER.atWarning().log("ArchetypeChunk#getComponent(): Entity index out of bounds (%d)", index);
        cir.setReturnValue(null);
    }
}
