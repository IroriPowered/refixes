package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

@Mixin(InstancesPlugin.class)
public abstract class MixinInstancesPlugin {

    @Shadow
    @Nonnull
    public abstract CompletableFuture<World> spawnInstance(@NonNullDecl String name, @NullableDecl String worldName, @NonNullDecl World forWorld, @NonNullDecl Transform returnPoint);

    /**
     * @author KabanFriends
     * @reason Shared instance system
     */
    @Overwrite
    public CompletableFuture<World> spawnInstance(@Nonnull String name, @Nonnull World forWorld, @Nonnull Transform returnPoint) {
        if (name == null || !EarlyOptions.SHARED_INSTANCES_ENABLED.get()) {
            return spawnInstance(name, null, forWorld, returnPoint);
        }

        String worldName = "instance-shared-" + InstancesPlugin.safeName(name);
        World existing = Universe.get().getWorld(worldName);
        if (existing != null && existing.isAlive()) {
            return CompletableFuture.completedFuture(existing);
        } else if (Universe.get().isWorldLoadable(worldName)) {
            return Universe.get().loadWorld(worldName);
        }

        return spawnInstance(name, worldName, forWorld, returnPoint);
    }
}
