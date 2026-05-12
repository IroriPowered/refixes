package cc.irori.refixes.early.copychunks;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
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
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkLoader;
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

        int minCx = min.x >> 5;
        int minCz = min.z >> 5;
        int maxCx = max.x >> 5;
        int maxCz = max.z >> 5;

        ChunkStore chunkStore = world.getChunkStore();
        IChunkLoader rawLoader = chunkStore.getLoader();
        if (rawLoader == null) {
            context.sendMessage(Message.raw("copychunks: no chunk loader for this world."));
            return;
        }
        if (!(rawLoader instanceof BufferChunkLoader)) {
            context.sendMessage(Message.raw("copychunks: world chunk loader is not buffer-based."));
            return;
        }
        BufferChunkLoader loader = (BufferChunkLoader) rawLoader;

        List<int[]> blockers = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx && blockers.size() < 5; cx++) {
            for (int cz = minCz; cz <= maxCz && blockers.size() < 5; cz++) {
                long idx = ChunkUtil.indexChunk(cx, cz);
                if (chunkStore.getChunkReference(idx) != null) {
                    blockers.add(new int[] {cx, cz});
                }
            }
        }
        if (!blockers.isEmpty()) {
            context.sendMessage(Message.raw(formatBlockerMessage("copychunks", blockers)));
            return;
        }

        int totalChunks = (maxCx - minCx + 1) * (maxCz - minCz + 1);
        List<CompletableFuture<ChunkBundle.Entry>> futures = new ArrayList<>(totalChunks);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                final int fcx = cx;
                final int fcz = cz;
                futures.add(loader.loadBuffer(cx, cz).thenApply(buf -> {
                    ByteBuffer blob = (buf == null) ? ByteBuffer.allocate(0) : buf;
                    return new ChunkBundle.Entry(fcx, fcz, blob);
                }));
            }
        }

        final String worldName = world.getName();
        final Path bundlePath = ChunkBundle.pathFor(name);
        final int finalMinCx = minCx;
        final int finalMinCz = minCz;
        final int finalMaxCx = maxCx;
        final int finalMaxCz = maxCz;

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(unused -> {
                    try {
                        List<ChunkBundle.Entry> entries = new ArrayList<>(futures.size());
                        int nonEmpty = 0;
                        for (CompletableFuture<ChunkBundle.Entry> f : futures) {
                            ChunkBundle.Entry e = f.join();
                            entries.add(e);
                            if (e.blob.remaining() > 0) {
                                nonEmpty++;
                            }
                        }
                        ChunkBundle bundle =
                                new ChunkBundle(worldName, finalMinCx, finalMinCz, finalMaxCx, finalMaxCz, 0, entries);
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
                    LOGGER.atSevere().withCause(e).log("copychunks: load failed");
                    context.sendMessage(Message.raw("copychunks: load failed: " + e.getMessage()));
                    return null;
                });
    }

    static String formatBlockerMessage(String label, List<int[]> blockers) {
        StringBuilder sb = new StringBuilder(label).append(": refusing - chunks currently loaded: ");
        for (int i = 0; i < blockers.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("(")
                    .append(blockers.get(i)[0])
                    .append(", ")
                    .append(blockers.get(i)[1])
                    .append(")");
        }
        sb.append(". Move all players far away or run /chunk unload <x> <z> for each, then retry.");
        return sb.toString();
    }
}
