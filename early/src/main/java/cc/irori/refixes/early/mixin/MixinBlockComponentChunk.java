package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ReferenceMap;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockComponentSection.class)
public class MixinBlockComponentChunk {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    @Final
    private Short2ObjectMap<Holder<ChunkStore>> blockHolders;

    @Shadow
    @Final
    private Short2ReferenceMap<Ref<ChunkStore>> blockReferences;

    @Shadow
    private ShortSet dirtyBlocks;

    @Shadow
    private ShortSet removedBlocks;

    @Inject(
            method = {
                "addBlockReference(SLcom/hypixel/hytale/component/Ref;)V",
                "loadBlockReference(SLcom/hypixel/hytale/component/Ref;)V"
            },
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalArgumentException;"),
            cancellable = true)
    private void refixes$ignoreDuplicateBlockComponentException(
            short index, Ref<ChunkStore> reference, CallbackInfo ci) {
        ci.cancel();
        Ref<ChunkStore> existing = blockReferences.get(index);
        if (!blockHolders.containsKey(index) && existing != null && !existing.isValid()) {
            blockReferences.put(index, reference);
            return;
        }
        refixes$LOGGER.atWarning().log(
                "BlockComponentSection: Duplicate block component at index %d ignored, discarding the newer entity",
                index);
        refixes$discardUntracked(index, reference);
    }

    @Inject(
            method = "addBlockHolder(SLcom/hypixel/hytale/component/Holder;)V",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalArgumentException;", ordinal = 0),
            cancellable = true)
    private void refixes$ignoreHolderOverReference(short index, Holder<ChunkStore> holder, CallbackInfo ci) {
        refixes$LOGGER.atWarning().log(
                "BlockComponentSection: Block component holder at index %d ignored, a live block entity already exists",
                index);
        ci.cancel();
    }

    @Inject(
            method = "addBlockHolder(SLcom/hypixel/hytale/component/Holder;)V",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalArgumentException;", ordinal = 1),
            cancellable = true)
    private void refixes$replaceAddedHolder(short index, Holder<ChunkStore> holder, CallbackInfo ci) {
        ci.cancel();
        refixes$replaceHolder(index, holder);
        dirtyBlocks.add(index);
        ((BlockComponentSection) (Object) this).markNeedsSaving();
    }

    @Inject(
            method = "storeBlockHolder(SLcom/hypixel/hytale/component/Holder;)V",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalArgumentException;"),
            cancellable = true)
    private void refixes$replaceStoredHolder(short index, Holder<ChunkStore> holder, CallbackInfo ci) {
        ci.cancel();
        refixes$replaceHolder(index, holder);
    }

    @Unique
    private void refixes$replaceHolder(short index, Holder<ChunkStore> holder) {
        refixes$LOGGER.atWarning().log(
                "BlockComponentSection: Duplicate block component holder at index %d replaced by the newer holder",
                index);
        blockHolders.put(index, holder);
        removedBlocks.remove(index);
    }

    @Unique
    private void refixes$discardUntracked(short index, Ref<ChunkStore> reference) {
        BlockComponentSection section = (BlockComponentSection) (Object) this;
        Store<ChunkStore> store = reference.getStore();
        try {
            store.getExternalData().getWorld().execute(() -> {
                if (reference.isValid() && section.getBlockReference(index) != reference) {
                    store.removeEntity(reference, RemoveReason.UNLOAD);
                }
            });
        } catch (RuntimeException e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "BlockComponentSection: Failed to queue removal of a duplicate block entity");
        }
    }
}
