package cc.irori.refixes.command;

import cc.irori.refixes.service.ChunkLoaderService;
import cc.irori.refixes.util.ChunkSelection;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ChunkLoaderCommand extends CommandBase {

    private final ChunkLoaderService service;

    public ChunkLoaderCommand(ChunkLoaderService service) {
        super("chunkloader", "refixes.commands.chunkloader.desc");
        this.service = service;

        this.addSubCommand(new AddCommand(service));
        this.addSubCommand(new RemoveCommand(service));
        this.addSubCommand(new ListCommand(service));
    }

    @Override
    protected void executeSync(CommandContext context) {
        context.sender()
                .sendMessage(
                        Message.raw("Usage: /chunkloader <add|remove|list>").color(Color.WHITE));
    }

    private static class AddCommand extends CommandBase {
        private final ChunkLoaderService service;
        private final OptionalArg<World> worldArg;
        private final OptionalArg<Integer> xArg;
        private final OptionalArg<Integer> zArg;
        private final OptionalArg<String> labelArg;
        private final FlagArg hereArg;

        AddCommand(ChunkLoaderService service) {
            super("add", "refixes.commands.chunkloader.add.desc");
            this.service = service;
            this.worldArg = this.withOptionalArg("world", "target world", ArgTypes.WORLD);
            this.xArg = this.withOptionalArg("x", "chunk x coordinate", ArgTypes.INTEGER);
            this.zArg = this.withOptionalArg("z", "chunk z coordinate", ArgTypes.INTEGER);
            this.labelArg = this.withOptionalArg("label", "chunk loader label", ArgTypes.STRING);
            this.hereArg = this.withFlagArg("here", "keep every chunk in your /pos1,/pos2 selection loaded");
            this.requirePermission(HytalePermissions.fromCommand("refixes.chunkloader.add"));
        }

        @Override
        protected void executeSync(CommandContext context) {
            World world = worldArg.getProcessed(context);
            if (world == null && !context.isPlayer()) {
                context.sender()
                        .sendMessage(Message.raw("Provide world argument or run as a player")
                                .color(Color.RED));
                return;
            }
            if (world == null) {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                world = playerRef.getStore().getExternalData().getWorld();
            }

            if (hereArg.get(context)) {
                if (!context.isPlayer()) {
                    context.sender()
                            .sendMessage(Message.raw("--here can only be used by a player")
                                    .color(Color.RED));
                    return;
                }
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                Player player = playerRef.getStore().getComponentConcurrent(playerRef, Player.getComponentType());
                PlayerRef playerComponent =
                        playerRef.getStore().getComponentConcurrent(playerRef, PlayerRef.getComponentType());
                if (player == null || playerComponent == null) {
                    context.sender()
                            .sendMessage(Message.raw("Could not resolve player").color(Color.RED));
                    return;
                }
                ChunkSelection.Region region = ChunkSelection.of(player, playerComponent);
                if (region == null) {
                    context.sender()
                            .sendMessage(Message.raw("No selection. Use /pos1 and /pos2 first.")
                                    .color(Color.RED));
                    return;
                }
                if (labelArg.provided(context)) {
                    context.sender()
                            .sendMessage(Message.raw("Note: --label is ignored for --here region adds.")
                                    .color(Color.YELLOW));
                }
                List<Long> indexes = new ArrayList<>(region.chunkCount());
                for (int cx = region.minChunkX; cx <= region.maxChunkX; cx++) {
                    for (int cz = region.minChunkZ; cz <= region.maxChunkZ; cz++) {
                        indexes.add(ChunkUtil.indexChunk(cx, cz));
                    }
                }
                int added = service.addChunks(world, indexes);
                context.sender()
                        .sendMessage(Message.raw(String.format(
                                        "Keeping %d chunk(s) loaded across region [%d, %d] to [%d, %d] (%d new).",
                                        region.chunkCount(),
                                        region.minChunkX,
                                        region.minChunkZ,
                                        region.maxChunkX,
                                        region.maxChunkZ,
                                        added))
                                .color(Color.GREEN));
                return;
            }

            int chunkX, chunkZ;
            if (xArg.provided(context) && zArg.provided(context)) {
                chunkX = xArg.get(context);
                chunkZ = zArg.get(context);
            } else if (!context.isPlayer()) {
                context.sender()
                        .sendMessage(Message.raw("Provide coordinates or run as a player")
                                .color(Color.RED));
                return;
            } else {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                TransformComponent transformComponent =
                        playerRef.getStore().getComponentConcurrent(playerRef, TransformComponent.getComponentType());
                if (transformComponent == null) {
                    context.sender()
                            .sendMessage(
                                    Message.raw("Could not get player position").color(Color.RED));
                    return;
                }
                chunkX = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().x());
                chunkZ = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().z());
            }

            String label = labelArg.provided(context) ? labelArg.get(context) : null;
            long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

            if (label != null && !label.isEmpty()) {
                Long existing = service.findChunkByLabel(world.getName(), label);
                if (existing != null && existing != chunkIndex) {
                    context.sender()
                            .sendMessage(Message.raw("Label '" + label + "' is already used at "
                                            + ChunkUtil.xOfChunkIndex(existing) + ", "
                                            + ChunkUtil.zOfChunkIndex(existing)
                                            + ". Choose another.")
                                    .color(Color.YELLOW));
                    return;
                }
            }

            boolean existed = service.getKeptChunks(world.getName()).containsKey(chunkIndex);
            service.addChunk(world, chunkX, chunkZ, label);

            String message = (existed ? "Updated" : "Added") + " chunk loader at " + chunkX + ", " + chunkZ;
            if (label != null && !label.isEmpty()) {
                message += " (" + label + ")";
            }
            context.sender().sendMessage(Message.raw(message).color(Color.GREEN));
        }
    }

    private static class RemoveCommand extends CommandBase {
        private final ChunkLoaderService service;
        private final OptionalArg<World> worldArg;
        private final OptionalArg<Integer> xArg;
        private final OptionalArg<Integer> zArg;
        private final OptionalArg<String> labelArg;
        private final FlagArg hereArg;

        RemoveCommand(ChunkLoaderService service) {
            super("remove", "refixes.commands.chunkloader.remove.desc");
            this.service = service;
            this.worldArg = this.withOptionalArg("world", "target world", ArgTypes.WORLD);
            this.xArg = this.withOptionalArg("x", "chunk x coordinate", ArgTypes.INTEGER);
            this.zArg = this.withOptionalArg("z", "chunk z coordinate", ArgTypes.INTEGER);
            this.labelArg = this.withOptionalArg("label", "chunk loader label", ArgTypes.STRING);
            this.hereArg = this.withFlagArg("here", "remove every chunk loader in your /pos1,/pos2 selection");
            this.requirePermission(HytalePermissions.fromCommand("refixes.chunkloader.remove"));
        }

        @Override
        protected void executeSync(CommandContext context) {
            World world = worldArg.getProcessed(context);
            if (world == null && !context.isPlayer()) {
                context.sender()
                        .sendMessage(Message.raw("Provide world argument or run as a player")
                                .color(Color.RED));
                return;
            }
            if (world == null) {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                world = playerRef.getStore().getExternalData().getWorld();
            }

            if (hereArg.get(context)) {
                if (!context.isPlayer()) {
                    context.sender()
                            .sendMessage(Message.raw("--here can only be used by a player")
                                    .color(Color.RED));
                    return;
                }
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                Player player = playerRef.getStore().getComponentConcurrent(playerRef, Player.getComponentType());
                PlayerRef playerComponent =
                        playerRef.getStore().getComponentConcurrent(playerRef, PlayerRef.getComponentType());
                if (player == null || playerComponent == null) {
                    context.sender()
                            .sendMessage(Message.raw("Could not resolve player").color(Color.RED));
                    return;
                }
                ChunkSelection.Region region = ChunkSelection.of(player, playerComponent);
                if (region == null) {
                    context.sender()
                            .sendMessage(Message.raw("No selection. Use /pos1 and /pos2 first.")
                                    .color(Color.RED));
                    return;
                }
                List<Long> indexes = new ArrayList<>(region.chunkCount());
                for (int cx = region.minChunkX; cx <= region.maxChunkX; cx++) {
                    for (int cz = region.minChunkZ; cz <= region.maxChunkZ; cz++) {
                        indexes.add(ChunkUtil.indexChunk(cx, cz));
                    }
                }
                int removed = service.removeChunks(world, indexes);
                context.sender()
                        .sendMessage(Message.raw(String.format(
                                        "Removed %d chunk loader(s) in region [%d, %d] to [%d, %d].",
                                        removed,
                                        region.minChunkX,
                                        region.minChunkZ,
                                        region.maxChunkX,
                                        region.maxChunkZ))
                                .color(Color.GREEN));
                return;
            }

            if (labelArg.provided(context)) {
                String label = labelArg.get(context);
                Long chunkIndex = service.findChunkByLabel(world.getName(), label);
                if (chunkIndex == null) {
                    context.sender()
                            .sendMessage(Message.raw("No chunk loader found with label: " + label)
                                    .color(Color.RED));
                    return;
                }
                int x = ChunkUtil.xOfChunkIndex(chunkIndex);
                int z = ChunkUtil.zOfChunkIndex(chunkIndex);
                service.removeChunk(world, x, z);
                context.sender()
                        .sendMessage(Message.raw("Removed chunk loader at " + x + ", " + z + " (" + label + ")")
                                .color(Color.GREEN));
                return;
            }

            int chunkX, chunkZ;
            if (xArg.provided(context) && zArg.provided(context)) {
                chunkX = xArg.get(context);
                chunkZ = zArg.get(context);
            } else if (!context.isPlayer()) {
                context.sender()
                        .sendMessage(Message.raw("Provide coordinates, label, or run as a player")
                                .color(Color.RED));
                return;
            } else {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                TransformComponent transformComponent =
                        playerRef.getStore().getComponentConcurrent(playerRef, TransformComponent.getComponentType());
                if (transformComponent == null) {
                    context.sender()
                            .sendMessage(
                                    Message.raw("Could not get player position").color(Color.RED));
                    return;
                }
                chunkX = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().x());
                chunkZ = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().z());
            }

            service.removeChunk(world, chunkX, chunkZ);
            context.sender()
                    .sendMessage(Message.raw("Removed chunk loader at " + chunkX + ", " + chunkZ)
                            .color(Color.GREEN));
        }
    }

    private static class ListCommand extends CommandBase {
        private final ChunkLoaderService service;
        private final OptionalArg<World> worldArg;

        ListCommand(ChunkLoaderService service) {
            super("list", "refixes.commands.chunkloader.list.desc");
            this.service = service;
            this.worldArg = this.withOptionalArg("world", "target world", ArgTypes.WORLD);
            this.requirePermission(HytalePermissions.fromCommand("refixes.chunkloader.list"));
        }

        @Override
        protected void executeSync(CommandContext context) {
            World world = worldArg.getProcessed(context);
            if (world == null && !context.isPlayer()) {
                context.sender()
                        .sendMessage(Message.raw("Provide world argument or run as a player")
                                .color(Color.RED));
                return;
            }
            if (world == null) {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                world = playerRef.getStore().getExternalData().getWorld();
            }

            Map<Long, String> chunks = service.getKeptChunks(world.getName());
            if (chunks.isEmpty()) {
                context.sender()
                        .sendMessage(
                                Message.raw("No chunk loaders in this world").color(Color.YELLOW));
                return;
            }

            List<Map.Entry<Long, String>> sorted = new ArrayList<>(chunks.entrySet());
            sorted.sort(Comparator.comparingInt((Map.Entry<Long, String> e) -> ChunkUtil.xOfChunkIndex(e.getKey()))
                    .thenComparingInt(e -> ChunkUtil.zOfChunkIndex(e.getKey())));

            context.sender()
                    .sendMessage(Message.raw("Chunk loaders in " + world.getName() + " (" + sorted.size() + "):")
                            .color(Color.CYAN));
            for (Map.Entry<Long, String> entry : sorted) {
                int x = ChunkUtil.xOfChunkIndex(entry.getKey());
                int z = ChunkUtil.zOfChunkIndex(entry.getKey());
                String label = entry.getValue();

                String message = "  - " + x + ", " + z;
                if (label != null && !label.isEmpty()) {
                    message += " (" + label + ")";
                }
                context.sender().sendMessage(Message.raw(message));
            }
        }
    }
}
