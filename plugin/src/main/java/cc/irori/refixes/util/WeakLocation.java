package cc.irori.refixes.util;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public record WeakLocation(String worldName, Vector3d position, Rotation3f rotation) {

    public WeakLocation(String worldName, Transform transform) {
        this(
                worldName,
                transform != null ? transform.getPosition() : null,
                transform != null ? transform.getRotation() : null);
    }

    public @Nullable World getWorld() {
        return Universe.get().getWorld(worldName);
    }

    public Transform getTransform() {
        return new Transform(position, rotation);
    }
}
