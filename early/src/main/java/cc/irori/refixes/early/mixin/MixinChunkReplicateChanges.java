package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlocks;
import com.hypixel.hytale.protocol.packets.world.SetBlockCmd;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.systems.ChunkSystems;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Makes ChunkSystems.ReplicateChanges parallel-safe by deferring all packet sends

@Mixin(ChunkSystems.ReplicateChanges.class)
public class MixinChunkReplicateChanges {

    @Overwrite
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        BlockSection blockSection = archetypeChunk.getComponent(index, BlockSection.getComponentType());
        assert blockSection != null;
        IntOpenHashSet changes = blockSection.getAndClearChangedPositions();
        if (changes.isEmpty()) {
            return;
        }

        ChunkSection section = archetypeChunk.getComponent(index, ChunkSection.getComponentType());
        assert section != null;

        Collection<PlayerRef> players = store.getExternalData().getWorld().getPlayerRefs();
        if (players.isEmpty()) {
            changes.clear();
            return;
        }

        long chunkIndex = ChunkUtil.indexChunk(section.getX(), section.getZ());

        if (changes.size() >= 1024) {
            // Defer the entire async compression
            ObjectArrayList<PlayerRef> playersCopy = new ObjectArrayList<>(players);
            int sx = section.getX(), sy = section.getY(), sz = section.getZ();
            commandBuffer.run(s -> {
                blockSection.getCachedChunkPacket(sx, sy, sz).thenAccept(packet -> {
                    for (PlayerRef player : playersCopy) {
                        Ref<EntityStore> ref = player.getReference();
                        ChunkTracker tracker;
                        if (ref == null
                                || (tracker = player.getChunkTracker()) == null
                                || !tracker.isLoaded(chunkIndex)) continue;
                        player.getPacketHandler().writeNoCache((ToClientPacket) packet);
                    }
                });
            });
            changes.clear();
            return;
        }

        if (changes.size() == 1) {
            int change = changes.iterator().nextInt();
            int x = ChunkUtil.minBlock(section.getX()) + ChunkUtil.xFromIndex(change);
            int y = ChunkUtil.minBlock(section.getY()) + ChunkUtil.yFromIndex(change);
            int z = ChunkUtil.minBlock(section.getZ()) + ChunkUtil.zFromIndex(change);
            int blockId = blockSection.get(change);
            int filler = blockSection.getFiller(change);
            int rotation = blockSection.getRotationIndex(change);
            ServerSetBlock packet = new ServerSetBlock(x, y, z, blockId, (short) filler, (byte) rotation);

            commandBuffer.run(s -> {
                for (PlayerRef player : players) {
                    Ref<EntityStore> ref = player.getReference();
                    ChunkTracker tracker;
                    if (ref == null || (tracker = player.getChunkTracker()) == null || !tracker.isLoaded(chunkIndex))
                        continue;
                    player.getPacketHandler().writeNoCache(packet);
                }
            });
        } else {
            SetBlockCmd[] cmds = new SetBlockCmd[changes.size()];
            IntIterator iter = changes.intIterator();
            int i = 0;
            while (iter.hasNext()) {
                int change = iter.nextInt();
                int blockId = blockSection.get(change);
                int filler = blockSection.getFiller(change);
                int rotation = blockSection.getRotationIndex(change);
                cmds[i++] = new SetBlockCmd((short) change, blockId, (short) filler, (byte) rotation);
            }
            ServerSetBlocks packet = new ServerSetBlocks(section.getX(), section.getY(), section.getZ(), cmds);

            commandBuffer.run(s -> {
                for (PlayerRef player : players) {
                    Ref<EntityStore> ref = player.getReference();
                    ChunkTracker tracker;
                    if (ref == null || (tracker = player.getChunkTracker()) == null || !tracker.isLoaded(chunkIndex))
                        continue;
                    player.getPacketHandler().writeNoCache(packet);
                }
            });
        }
        changes.clear();
    }
}
