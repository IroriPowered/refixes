package cc.irori.refixes.early.copychunks;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.BufferChunkLoader;
import com.hypixel.hytale.server.core.universe.world.storage.BufferChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkLoader;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public class CopyChunksCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = Logs.logger();

    @Nonnull
    private final RequiredArg<String> nameArg;

    public CopyChunksCommand() {
        super("copychunks", "refixes.commands.copychunks.desc");
        this.nameArg = this.withRequiredArg("name", "refixes.commands.copychunks.name.desc", ArgTypes.STRING);
        this.setPermissionGroups(new String[] {"hytale:WorldEditor"});
        this.requirePermission(HytalePermissions.EDITOR_SELECTION_USE);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
        String name = (String) this.nameArg.get(context);
        if (!ChunkBundle.isValidName(name)) {
            context.sendMessage(Message.raw("copychunks: invalid name. Allowed: [A-Za-z0-9._-], 1-64 chars."));
            return;
        }

        Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            context.sendMessage(Message.raw("copychunks: player component missing."));
            return;
        }
        BuilderToolsPlugin.BuilderState state = BuilderToolsPlugin.getState(playerComponent, playerRef);
        BlockSelection selection = state.getSelection();
        if (selection == null) {
            context.sendMessage(Message.raw("copychunks: no selection. Use /pos1 and /pos2 first."));
            return;
        }
        Vector3i min = selection.getSelectionMin();
        Vector3i max = selection.getSelectionMax();
        if (min == null || max == null) {
            context.sendMessage(Message.raw("copychunks: selection has no bounds."));
            return;
        }

        boolean aligned =
                ((min.x & 31) == 0) && ((min.z & 31) == 0) && (((max.x + 1) & 31) == 0) && (((max.z + 1) & 31) == 0);
        if (!aligned) {
            context.sendMessage(Message.raw(String.format(
                    "copychunks: selection not chunk-aligned; snapping to enclosing chunks (min=(%d,%d,%d), max=(%d,%d,%d)).",
                    min.x, min.y, min.z, max.x, max.y, max.z)));
        }

        final int minCx = min.x >> 5;
        final int minCz = min.z >> 5;
        final int maxCx = max.x >> 5;
        final int maxCz = max.z >> 5;

        ChunkStore chunkStore = world.getChunkStore();
        IChunkLoader rawLoader = chunkStore.getLoader();
        IChunkSaver rawSaver = chunkStore.getSaver();
        if (rawLoader == null || rawSaver == null) {
            context.sendMessage(Message.raw("copychunks: no chunk loader/saver for this world."));
            return;
        }
        if (!(rawLoader instanceof BufferChunkLoader) || !(rawSaver instanceof BufferChunkSaver)) {
            context.sendMessage(Message.raw("copychunks: world chunk loader/saver is not buffer-based."));
            return;
        }
        BufferChunkLoader loader = (BufferChunkLoader) rawLoader;
        BufferChunkSaver saver = (BufferChunkSaver) rawSaver;

        List<int[]> coords = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                coords.add(new int[] {cx, cz});
            }
        }

        final String worldName = world.getName();
        final Path bundlePath = ChunkBundle.pathFor(name);

        ChunkClipboardOps.forceUnloadAndDrain(world, chunkStore, saver, coords)
                .thenComposeAsync(unused -> {
                    List<CompletableFuture<ChunkBundle.Entry>> futures = new ArrayList<>(coords.size());
                    for (int[] coord : coords) {
                        final int fcx = coord[0];
                        final int fcz = coord[1];
                        futures.add(loader.loadBuffer(fcx, fcz).thenApply(buf -> {
                            ByteBuffer blob = (buf == null) ? ByteBuffer.allocate(0) : buf;
                            return new ChunkBundle.Entry(fcx, fcz, blob);
                        }));
                    }
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> {
                                List<ChunkBundle.Entry> entries = new ArrayList<>(futures.size());
                                for (CompletableFuture<ChunkBundle.Entry> f : futures) {
                                    entries.add(f.join());
                                }
                                return entries;
                            });
                })
                .thenAccept(entries -> {
                    int nonEmpty = 0;
                    for (ChunkBundle.Entry e : entries) {
                        if (e.blob.remaining() > 0) {
                            nonEmpty++;
                        }
                    }
                    try {
                        ChunkBundle bundle = new ChunkBundle(worldName, minCx, minCz, maxCx, maxCz, 0, entries);
                        bundle.write(bundlePath);
                        context.sendMessage(Message.raw(String.format(
                                "copychunks: wrote %d chunks (%d non-empty) to %s",
                                entries.size(), nonEmpty, bundlePath.toAbsolutePath())));
                    } catch (IOException e) {
                        LOGGER.atSevere().withCause(e).log("copychunks: write failed");
                        context.sendMessage(Message.raw("copychunks: write failed: " + e.getMessage()));
                    }
                })
                .exceptionally(e -> {
                    LOGGER.atSevere().withCause(e).log("copychunks: failed");
                    context.sendMessage(Message.raw("copychunks: failed: " + e.getMessage()));
                    return null;
                });
    }
}
