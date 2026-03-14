package cc.irori.refixes.command;

import cc.irori.refixes.service.ChunkLoaderService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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
        context.sender().sendMessage(Message.raw("§cUsage: /refixes chunkloader <add|remove|list>"));
    }

    private static class AddCommand extends CommandBase {
        private final ChunkLoaderService service;
        private final OptionalArg<World> worldArg;
        private final OptionalArg<Integer> xArg;
        private final OptionalArg<Integer> zArg;
        private final OptionalArg<String> labelArg;

        AddCommand(ChunkLoaderService service) {
            super("add", "refixes.commands.chunkloader.add.desc");
            this.service = service;
            this.worldArg = this.withOptionalArg("world", "target world", ArgTypes.WORLD);
            this.xArg = this.withOptionalArg("x", "chunk x coordinate", ArgTypes.INTEGER);
            this.zArg = this.withOptionalArg("z", "chunk z coordinate", ArgTypes.INTEGER);
            this.labelArg = this.withOptionalArg("label", "chunk loader label", ArgTypes.STRING);
            this.requirePermission(HytalePermissions.fromCommand("refixes.chunkloader.add"));
        }

        @Override
        protected void executeSync(CommandContext context) {
            World world = worldArg.getProcessed(context);
            if (world == null && !context.isPlayer()) {
                context.sender().sendMessage(Message.raw("§cProvide world argument or run as a player"));
                return;
            }
            if (world == null) {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                world = playerRef.getStore().getExternalData().getWorld();
            }

            int chunkX, chunkZ;
            if (xArg.provided(context) && zArg.provided(context)) {
                chunkX = xArg.getProcessed(context);
                chunkZ = zArg.getProcessed(context);
            } else if (!context.isPlayer()) {
                context.sender().sendMessage(Message.raw("§cProvide coordinates or run as a player"));
                return;
            } else {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                TransformComponent transformComponent =
                        playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
                if (transformComponent == null) {
                    context.sender().sendMessage(Message.raw("§cCould not get player position"));
                    return;
                }
                chunkX = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().getX());
                chunkZ = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().getZ());
            }

            String label = labelArg.provided(context) ? labelArg.getProcessed(context) : null;
            service.addChunk(world, chunkX, chunkZ, label);

            String message = "§aAdded chunk loader at " + chunkX + ", " + chunkZ;
            if (label != null && !label.isEmpty()) {
                message += " (" + label + ")";
            }
            context.sender().sendMessage(Message.raw(message));
        }
    }

    private static class RemoveCommand extends CommandBase {
        private final ChunkLoaderService service;
        private final OptionalArg<World> worldArg;
        private final OptionalArg<Integer> xArg;
        private final OptionalArg<Integer> zArg;
        private final OptionalArg<String> labelArg;

        RemoveCommand(ChunkLoaderService service) {
            super("remove", "refixes.commands.chunkloader.remove.desc");
            this.service = service;
            this.worldArg = this.withOptionalArg("world", "target world", ArgTypes.WORLD);
            this.xArg = this.withOptionalArg("x", "chunk x coordinate", ArgTypes.INTEGER);
            this.zArg = this.withOptionalArg("z", "chunk z coordinate", ArgTypes.INTEGER);
            this.labelArg = this.withOptionalArg("label", "chunk loader label", ArgTypes.STRING);
            this.requirePermission(HytalePermissions.fromCommand("refixes.chunkloader.remove"));
        }

        @Override
        protected void executeSync(CommandContext context) {
            World world = worldArg.getProcessed(context);
            if (world == null && !context.isPlayer()) {
                context.sender().sendMessage(Message.raw("§cProvide world argument or run as a player"));
                return;
            }
            if (world == null) {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                world = playerRef.getStore().getExternalData().getWorld();
            }

            if (labelArg.provided(context)) {
                String label = labelArg.getProcessed(context);
                Long chunkIndex = service.findChunkByLabel(world.getName(), label);
                if (chunkIndex == null) {
                    context.sender().sendMessage(Message.raw("§cNo chunk loader found with label: " + label));
                    return;
                }
                int x = ChunkUtil.xOfChunkIndex(chunkIndex);
                int z = ChunkUtil.zOfChunkIndex(chunkIndex);
                service.removeChunk(world, x, z);
                context.sender()
                        .sendMessage(Message.raw("§aRemoved chunk loader at " + x + ", " + z + " (" + label + ")"));
                return;
            }

            int chunkX, chunkZ;
            if (xArg.provided(context) && zArg.provided(context)) {
                chunkX = xArg.getProcessed(context);
                chunkZ = zArg.getProcessed(context);
            } else if (!context.isPlayer()) {
                context.sender().sendMessage(Message.raw("§cProvide coordinates, label, or run as a player"));
                return;
            } else {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                TransformComponent transformComponent =
                        playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
                if (transformComponent == null) {
                    context.sender().sendMessage(Message.raw("§cCould not get player position"));
                    return;
                }
                chunkX = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().getX());
                chunkZ = ChunkUtil.chunkCoordinate(
                        (int) transformComponent.getTransform().getPosition().getZ());
            }

            service.removeChunk(world, chunkX, chunkZ);
            context.sender().sendMessage(Message.raw("§aRemoved chunk loader at " + chunkX + ", " + chunkZ));
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
                context.sender().sendMessage(Message.raw("§cProvide world argument or run as a player"));
                return;
            }
            if (world == null) {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                world = playerRef.getStore().getExternalData().getWorld();
            }

            Map<Long, String> chunks = service.getKeptChunks(world.getName());
            if (chunks.isEmpty()) {
                context.sender().sendMessage(Message.raw("§eNo chunk loaders in this world"));
                return;
            }

            context.sender().sendMessage(Message.raw("§aChunk loaders in " + world.getName() + ":"));
            for (Map.Entry<Long, String> entry : chunks.entrySet()) {
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
