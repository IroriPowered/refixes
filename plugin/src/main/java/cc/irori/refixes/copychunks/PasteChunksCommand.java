package cc.irori.refixes.copychunks;

import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.BufferChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public class PasteChunksCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = Logs.logger();

    @Nonnull
    private final RequiredArg<String> nameArg;

    @Nonnull
    private final OptionalArg<String> worldArg;

    @Nonnull
    private final FlagArg hereArg;

    public PasteChunksCommand() {
        super("pastechunks", "refixes.commands.pastechunks.desc");
        this.nameArg = this.withRequiredArg("name", "refixes.commands.pastechunks.name.desc", ArgTypes.STRING);
        this.worldArg = this.withOptionalArg("world", "refixes.commands.pastechunks.world.desc", ArgTypes.STRING);
        this.hereArg = this.withFlagArg("here", "refixes.commands.pastechunks.here.desc");
        this.setPermissionGroups(new String[] {"hytale:WorldEditor"});
        this.requirePermission(HytalePermissions.EDITOR_SELECTION_MODIFY);
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
            context.sendMessage(Message.raw("pastechunks: invalid name. Allowed: [A-Za-z0-9._-], 1-64 chars."));
            return;
        }
        Path bundlePath = ChunkBundle.pathFor(name);
        if (!Files.isRegularFile(bundlePath)) {
            context.sendMessage(Message.raw("pastechunks: bundle not found at " + bundlePath.toAbsolutePath()));
            return;
        }

        ChunkBundle bundle;
        try {
            bundle = ChunkBundle.read(bundlePath);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("pastechunks: read failed");
            context.sendMessage(Message.raw("pastechunks: read failed: " + e.getMessage()));
            return;
        }

        World targetWorld = world;
        if (this.worldArg.provided(context)) {
            String requested = (String) this.worldArg.get(context);
            World resolved = Universe.get().getWorld(requested);
            if (resolved == null || !resolved.isAlive()) {
                context.sendMessage(Message.raw("pastechunks: target world '"
                        + requested
                        + "' is not loaded. Run /world load "
                        + requested
                        + " first."));
                return;
            }
            targetWorld = resolved;
        }

        int dx = 0;
        int dz = 0;
        if (this.hereArg.get(context)) {
            Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                context.sendMessage(Message.raw("pastechunks: player component missing for --here."));
                return;
            }
            BuilderToolsPlugin.BuilderState state = BuilderToolsPlugin.getState(playerComponent, playerRef);
            BlockSelection selection = state.getSelection();
            if (selection == null) {
                context.sendMessage(
                        Message.raw("pastechunks: --here requires a selection (use /pos1 at the new min corner)."));
                return;
            }
            Vector3i newMin = selection.getSelectionMin();
            if (newMin == null) {
                context.sendMessage(Message.raw("pastechunks: --here selection has no bounds."));
                return;
            }
            if (((newMin.x & 31) != 0) || ((newMin.z & 31) != 0)) {
                context.sendMessage(Message.raw(String.format(
                        "pastechunks: --here selection min (%d,%d,%d) is not chunk-aligned. "
                                + "Snap pos1 to a multiple of 32 on X and Z. "
                                + "(Blocks-only fallback for misaligned origins is not yet implemented.)",
                        newMin.x, newMin.y, newMin.z)));
                return;
            }
            int newMinCx = newMin.x >> 5;
            int newMinCz = newMin.z >> 5;
            dx = newMinCx - bundle.minChunkX;
            dz = newMinCz - bundle.minChunkZ;
        }

        ChunkStore chunkStore = targetWorld.getChunkStore();
        IChunkSaver rawSaver = chunkStore.getSaver();
        if (rawSaver == null) {
            context.sendMessage(Message.raw("pastechunks: no chunk saver for target world."));
            return;
        }
        if (!(rawSaver instanceof BufferChunkSaver)) {
            context.sendMessage(Message.raw("pastechunks: target chunk saver is not buffer-based."));
            return;
        }
        final BufferChunkSaver saver = (BufferChunkSaver) rawSaver;

        List<int[]> destCoords = new ArrayList<>(bundle.chunks.size());
        for (ChunkBundle.Entry entry : bundle.chunks) {
            destCoords.add(new int[] {entry.chunkX + dx, entry.chunkZ + dz});
        }

        final int finalDx = dx;
        final int finalDz = dz;
        final String targetWorldName = targetWorld.getName();
        final Path finalBundlePath = bundlePath;

        ChunkClipboardOps.forceUnloadAndDrain(targetWorld, chunkStore, saver, destCoords)
                .thenComposeAsync(unused -> {
                    List<CompletableFuture<Void>> writes = new ArrayList<>(bundle.chunks.size());
                    int skipped = 0;
                    for (ChunkBundle.Entry entry : bundle.chunks) {
                        if (entry.blob.remaining() == 0) {
                            skipped++;
                            continue;
                        }
                        writes.add(saver.saveBuffer(
                                entry.chunkX + finalDx, entry.chunkZ + finalDz, entry.blob.duplicate()));
                    }
                    final int writtenCount = writes.size();
                    final int skippedCount = skipped;
                    return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]))
                            .thenApply(v -> new int[] {writtenCount, skippedCount});
                })
                .thenAccept(counts -> context.sendMessage(Message.raw(String.format(
                        "pastechunks: wrote %d chunks (skipped %d empty) to world '%s' "
                                + "from %s. Translation: dx=%d, dz=%d. Re-enter the area to reload.",
                        counts[0], counts[1], targetWorldName, finalBundlePath.toAbsolutePath(), finalDx, finalDz))))
                .exceptionally(e -> {
                    LOGGER.atSevere().withCause(e).log("pastechunks: failed");
                    context.sendMessage(Message.raw("pastechunks: failed: " + e.getMessage()));
                    return null;
                });
    }
}
