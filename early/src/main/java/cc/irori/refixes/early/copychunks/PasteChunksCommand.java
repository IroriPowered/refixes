package cc.irori.refixes.early.copychunks;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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

public class PasteChunksCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = Logs.logger();

    @Nonnull
    private final RequiredArg<String> nameArg;

    public PasteChunksCommand() {
        super("pastechunks", "refixes.commands.pastechunks.desc");
        this.nameArg = this.withRequiredArg("name", "refixes.commands.pastechunks.name.desc", ArgTypes.STRING);
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

        ChunkStore chunkStore = world.getChunkStore();
        IChunkSaver rawSaver = chunkStore.getSaver();
        if (rawSaver == null) {
            context.sendMessage(Message.raw("pastechunks: no chunk saver for this world."));
            return;
        }
        if (!(rawSaver instanceof BufferChunkSaver)) {
            context.sendMessage(Message.raw("pastechunks: world chunk saver is not buffer-based."));
            return;
        }
        BufferChunkSaver saver = (BufferChunkSaver) rawSaver;

        List<int[]> blockers = new ArrayList<>();
        for (ChunkBundle.Entry entry : bundle.chunks) {
            long idx = ChunkUtil.indexChunk(entry.chunkX, entry.chunkZ);
            if (chunkStore.getChunkReference(idx) != null) {
                blockers.add(new int[] {entry.chunkX, entry.chunkZ});
                if (blockers.size() >= 5) {
                    break;
                }
            }
        }
        if (!blockers.isEmpty()) {
            context.sendMessage(Message.raw(CopyChunksCommand.formatBlockerMessage("pastechunks", blockers)));
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>(bundle.chunks.size());
        int written = 0;
        for (ChunkBundle.Entry entry : bundle.chunks) {
            if (entry.blob.remaining() == 0) {
                continue;
            }
            futures.add(saver.saveBuffer(entry.chunkX, entry.chunkZ, entry.blob.duplicate()));
            written++;
        }

        final int writtenFinal = written;
        final int skipped = bundle.chunks.size() - written;
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> context.sendMessage(Message.raw(String.format(
                        "pastechunks: wrote %d chunks (skipped %d empty) from %s. Re-enter the area to reload.",
                        writtenFinal, skipped, bundlePath.toAbsolutePath()))))
                .exceptionally(e -> {
                    LOGGER.atSevere().withCause(e).log("pastechunks: save failed");
                    context.sendMessage(Message.raw("pastechunks: save failed: " + e.getMessage()));
                    return null;
                });
    }
}
