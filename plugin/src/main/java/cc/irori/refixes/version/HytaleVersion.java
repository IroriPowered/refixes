package cc.irori.refixes.version;

import cc.irori.refixes.service.ChunkLoaderService;
import cc.irori.refixes.service.SharedInstanceStorage;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.hypixel.hytale.server.npc.role.Role;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface HytaleVersion {

    static HytaleVersion get() {
        return Selected.INSTANCE;
    }

    ChunkLoaderService chunkLoaderService(Path pluginDataDir);

    SharedInstanceStorage sharedInstanceStorage(IChunkSaver.Cubic saver, Store<ChunkStore> chunkStore);

    ComponentType<EntityStore, ? extends INonPlayerCharacter> npcComponentType();

    Role role(INonPlayerCharacter npc);

    CompletableFuture<Boolean> removeWorldAsync(String name);

    final class Selected {
        private static final HytaleVersion INSTANCE = select(HytaleVersions.running());

        private Selected() {}

        static HytaleVersion select(String running) {
            String name = HytaleLine.nearest(running).adapter();
            try {
                return (HytaleVersion) Class.forName(name, true, HytaleVersion.class.getClassLoader())
                        .getConstructor()
                        .newInstance();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot load " + name + " for Hytale " + running, failure);
            }
        }
    }
}
