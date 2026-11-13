package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightDataBuilder;
import com.hypixel.hytale.server.core.universe.world.lighting.FloodLightCalculation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Field;
import java.util.BitSet;

public final class LightPolicyRegression {
    public static void main(String[] args) throws Exception {
        installBlockMap();
        BlockSection emptyAir = new BlockSection();
        require(emptyAir.isSolidAir(), "empty fixture must be solid air");
        require(emptyAir.getLocalLight() == ChunkLightData.EMPTY, "empty fixture must use native EMPTY light");
        FloodLightCalculation calculation = new FloodLightCalculation(null);
        BlockSection[][] sections = new BlockSection[][] {new BlockSection[6], new BlockSection[12], new BlockSection[8]};
        java.util.Arrays.fill(sections[0], emptyAir);
        java.util.Arrays.fill(sections[1], emptyAir);
        java.util.Arrays.fill(sections[2], emptyAir);
        set(calculation, "fromSections", sections);
        BlockSection destination = new BlockSection();
        BitSet queue = new BitSet();
        queue.set(17);
        long[] expectedQueue = queue.toLongArray();
        ChunkLightDataBuilder output = new ChunkLightDataBuilder((short) 0);
        short[] expected = new short[32768];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = (short) ((index * 0x1F3D) ^ 0xA529);
            output.setLightRaw(index, expected[index]);
        }
        calculation.propagateSides(destination, output, queue);
        assertUnchanged(output, queue, expected, expectedQueue, "side");
        calculation.propagateEdges(destination, output, queue);
        assertUnchanged(output, queue, expected, expectedQueue, "edge");
        calculation.propagateCorners(destination, output, queue);
        assertUnchanged(output, queue, expected, expectedQueue, "corner");
        System.out.println("LightPolicyRegression PASS native solid-air EMPTY side/edge/corner output and queue equivalence");
    }

    private static void assertUnchanged(ChunkLightDataBuilder output, BitSet queue, short[] expected,
            long[] expectedQueue, String kind) {
        require(java.util.Arrays.equals(queue.toLongArray(), expectedQueue), "native " + kind + " propagation changed queue");
        for (int index = 0; index < expected.length; index++) {
            require(output.getLightRaw(index) == expected[index],
                    "native " + kind + " propagation changed packed light at " + index);
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void installBlockMap() throws Exception {
        Object map = new com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType>(BlockType[]::new, BlockType::getGroup);
        Field index = map.getClass().getDeclaredField("keyToIndex");
        index.setAccessible(true);
        ((Object2IntMap<String>) index.get(map)).put("Empty", 0);
        Field next = map.getClass().getDeclaredField("nextIndex");
        next.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicInteger) next.get(map)).set(1);
        Field array = map.getClass().getDeclaredField("array");
        array.setAccessible(true);
        array.set(map, new BlockType[] {BlockType.EMPTY});
        Field storesField = com.hypixel.hytale.assetstore.AssetRegistry.class.getDeclaredField("storeMap");
        storesField.setAccessible(true);
        ((java.util.Map<Class<?>, Object>) storesField.get(null)).put(BlockType.class, new NativeBlockTypeStore((com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType>) map));
    }

    private static final class NativeBlockTypeStore extends com.hypixel.hytale.assetstore.AssetStore<
            String, BlockType, com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType>> {
        private NativeBlockTypeStore(
                com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType> map) {
            super(new Builder(map));
        }

        @Override
        protected com.hypixel.hytale.event.IEventBus getEventBus() {
            return null;
        }

        @Override
        public void addFileMonitor(String pack, java.nio.file.Path path) {}

        @Override
        public void removeFileMonitor(java.nio.file.Path path) {}

        @Override
        protected void handleRemoveOrUpdate(
                java.util.Set<String> keys,
                java.util.Map<String, BlockType> assets,
                com.hypixel.hytale.assetstore.AssetUpdateQuery query) {}

        private static final class Builder extends com.hypixel.hytale.assetstore.AssetStore.Builder<
                String, BlockType, com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType>, Builder> {
            private Builder(com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType> map) {
                super(String.class, BlockType.class, map);
                setReplaceOnRemove(key -> BlockType.EMPTY);
            }

            @Override
            public com.hypixel.hytale.assetstore.AssetStore<String, BlockType,
                    com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String, BlockType>> build() {
                return new NativeBlockTypeStore(assetMap);
            }
        }
    }



    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
