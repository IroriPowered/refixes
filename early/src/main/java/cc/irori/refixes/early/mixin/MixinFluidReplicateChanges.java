package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.fluid.FluidSystems;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.ServerSetFluid;
import com.hypixel.hytale.protocol.packets.world.ServerSetFluids;
import com.hypixel.hytale.protocol.packets.world.SetFluidCmd;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

// Makes FluidSystems.ReplicateChanges parallel-safe by deferring all packet sends

@Mixin(FluidSystems.ReplicateChanges.class)
public class MixinFluidReplicateChanges {

    @Shadow
    @Final
    @Nonnull
    private ComponentType<ChunkStore, ChunkSection> chunkSectionComponentType;

    @Shadow
    @Final
    @Nonnull
    private ComponentType<ChunkStore, FluidSection> fluidSectionComponentType;

    @Shadow
    @Final
    @Nonnull
    private ComponentType<ChunkStore, WorldChunk> worldChunkComponentType;

    @Overwrite
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        FluidSection fluidSection = archetypeChunk.getComponent(index, this.fluidSectionComponentType);
        assert fluidSection != null;
        IntOpenHashSet changes = fluidSection.getAndClearChangedPositions();
        if (changes.isEmpty()) {
            return;
        }

        ChunkSection section = archetypeChunk.getComponent(index, this.chunkSectionComponentType);
        assert section != null;

        World world = commandBuffer.getExternalData().getWorld();
        WorldChunk worldChunk =
                commandBuffer.getComponent(section.getChunkColumnReference(), this.worldChunkComponentType);
        int sectionY = section.getY();

        // Defer light invalidation to merge phase
        commandBuffer.run(s -> {
            if (worldChunk == null || worldChunk.getWorld() == null) return;
            worldChunk.getWorld().getChunkLighting().invalidateLightInChunkSection(worldChunk, sectionY);
        });

        Collection<PlayerRef> playerRefs = store.getExternalData().getWorld().getPlayerRefs();
        if (playerRefs.isEmpty()) {
            changes.clear();
            return;
        }

        long chunkIndex = ChunkUtil.indexChunk(fluidSection.getX(), fluidSection.getZ());

        if (changes.size() >= 1024) {
            // Defer entire async path to merge phase
            ObjectArrayList<PlayerRef> playersCopy = new ObjectArrayList<>(playerRefs);
            commandBuffer.run(s -> {
                fluidSection.getCachedPacket().whenComplete((packet, throwable) -> {
                    if (throwable != null) return;
                    for (PlayerRef playerRef : playersCopy) {
                        Ref<EntityStore> ref = playerRef.getReference();
                        ChunkTracker tracker;
                        if (ref == null
                                || !ref.isValid()
                                || !(tracker = playerRef.getChunkTracker()).isLoaded(chunkIndex)) continue;
                        playerRef.getPacketHandler().writeNoCache((ToClientPacket) packet);
                    }
                });
            });
            changes.clear();
            return;
        }

        if (changes.size() == 1) {
            int change = changes.iterator().nextInt();
            int x = ChunkUtil.minBlock(fluidSection.getX()) + ChunkUtil.xFromIndex(change);
            int y = ChunkUtil.minBlock(fluidSection.getY()) + ChunkUtil.yFromIndex(change);
            int z = ChunkUtil.minBlock(fluidSection.getZ()) + ChunkUtil.zFromIndex(change);
            int fluid = fluidSection.getFluidId(change);
            byte level = fluidSection.getFluidLevel(change);
            ServerSetFluid packet = new ServerSetFluid(x, y, z, fluid, level);

            commandBuffer.run(s -> {
                for (PlayerRef playerRef : playerRefs) {
                    Ref<EntityStore> ref = playerRef.getReference();
                    ChunkTracker tracker;
                    if (ref == null || !ref.isValid() || !(tracker = playerRef.getChunkTracker()).isLoaded(chunkIndex))
                        continue;
                    playerRef.getPacketHandler().writeNoCache(packet);
                }
            });
        } else {
            SetFluidCmd[] cmds = new SetFluidCmd[changes.size()];
            IntIterator iter = changes.intIterator();
            int i = 0;
            while (iter.hasNext()) {
                int change = iter.nextInt();
                int fluid = fluidSection.getFluidId(change);
                byte level = fluidSection.getFluidLevel(change);
                cmds[i++] = new SetFluidCmd((short) change, fluid, level);
            }
            ServerSetFluids packet =
                    new ServerSetFluids(fluidSection.getX(), fluidSection.getY(), fluidSection.getZ(), cmds);

            commandBuffer.run(s -> {
                for (PlayerRef playerRef : playerRefs) {
                    Ref<EntityStore> ref = playerRef.getReference();
                    ChunkTracker tracker;
                    if (ref == null || !ref.isValid() || !(tracker = playerRef.getChunkTracker()).isLoaded(chunkIndex))
                        continue;
                    playerRef.getPacketHandler().writeNoCache(packet);
                }
            });
        }
        changes.clear();
    }
}
