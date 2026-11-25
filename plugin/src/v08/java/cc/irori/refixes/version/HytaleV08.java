package cc.irori.refixes.version;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.service.ChunkLoaderService;
import cc.irori.refixes.service.ChunkLoaderServiceV08;
import cc.irori.refixes.service.SharedInstanceStorage;
import cc.irori.refixes.service.SharedInstanceStorageV08;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.hypixel.hytale.server.npc.components.NPCComponent;
import com.hypixel.hytale.server.npc.role.Role;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
public final class HytaleV08 implements HytaleVersion {

    @Override
    public ChunkLoaderService chunkLoaderService(Path pluginDataDir) {
        return new ChunkLoaderServiceV08(pluginDataDir);
    }

    @Override
    public SharedInstanceStorage sharedInstanceStorage(IChunkSaver.Cubic saver, Store<ChunkStore> chunkStore) {
        return new SharedInstanceStorageV08(saver, chunkStore);
    }

    @Override
    public ComponentType<EntityStore, ? extends INonPlayerCharacter> npcComponentType() {
        return NPCComponent.getComponentType();
    }

    @Override
    public Role role(INonPlayerCharacter npc) {
        return ((NPCComponent) npc).getRole();
    }

    @Override
    public CompletableFuture<Boolean> removeWorldAsync(String name) {
        return Universe.get().removeWorldAsync(name);
    }
}
