package cc.irori.refixes.version;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.service.ChunkLoaderService;
import cc.irori.refixes.service.ChunkLoaderServiceV07;
import cc.irori.refixes.service.SharedInstanceStorage;
import cc.irori.refixes.service.SharedInstanceStorageV07;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@IfHytale("<0.8.0-pre.1")
public final class HytaleV07 implements HytaleVersion {

    @Override
    public ChunkLoaderService chunkLoaderService(Path pluginDataDir) {
        return new ChunkLoaderServiceV07(pluginDataDir);
    }

    @Override
    public SharedInstanceStorage sharedInstanceStorage(IChunkSaver.Cubic saver, Store<ChunkStore> chunkStore) {
        return new SharedInstanceStorageV07(saver, chunkStore);
    }

    @Override
    public ComponentType<EntityStore, ? extends INonPlayerCharacter> npcComponentType() {
        return NPCEntity.getComponentType();
    }

    @Override
    public Role role(INonPlayerCharacter npc) {
        return ((NPCEntity) npc).getRole();
    }

    @Override
    public CompletableFuture<Boolean> removeWorldAsync(String name) {
        return CompletableFuture.completedFuture(Universe.get().removeWorld(name));
    }
}
