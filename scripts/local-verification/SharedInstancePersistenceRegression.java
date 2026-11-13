package cc.irori.refixes.system;

import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.resources.IResourceStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.resources.EmptyResourceStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.IWorldMapProvider;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.DisabledWorldMapProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;

public final class SharedInstancePersistenceRegression {
    public static void main(String[] args) throws Exception {
        try {
            verify();
        } catch (Throwable failure) {
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            throw failure;
        }
    }

    private static void verify() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[0]);
        var effects = com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(
                com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.class,
                new com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap<>(
                        com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect[]::new))
                .setCodec(com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.CODEC)
                .setKeyFunction(com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect::getId)
                .setReplaceOnRemove(com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect::new)
                .build();
        var store = com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.class.getDeclaredField("STORE");
        store.setAccessible(true);
        store.set(null, effects);
        IWorldGenProvider.CODEC.register("Test", VoidWorldGenProvider.class, VoidWorldGenProvider.CODEC);
        IWorldMapProvider.CODEC.register("Test", DisabledWorldMapProvider.class, DisabledWorldMapProvider.CODEC);
        IChunkStorageProvider.CODEC.register("Test", EmptyChunkStorageProvider.class, EmptyChunkStorageProvider.CODEC);
        IResourceStorageProvider.CODEC.register("Test", EmptyResourceStorageProvider.class, EmptyResourceStorageProvider.CODEC);
        WorldConfig config = new WorldConfig();
        config.setDeleteOnRemove(true);
        config.setDeleteOnUniverseStart(true);
        boolean saveChunks = config.canSaveChunks();
        SharedInstancePersistenceSystem.preserveStorage(config);
        if (config.isDeleteOnRemove() || config.isDeleteOnUniverseStart()) {
            throw new AssertionError("Existing shared storage remains deletable");
        }
        if (config.canSaveChunks() != saveChunks) throw new AssertionError("Storage maintenance changed chunk-save policy");
        SharedInstancePersistenceSystem.preserveStorage(config);
        System.out.println("SharedInstancePersistenceRegression passed");
    }
}
