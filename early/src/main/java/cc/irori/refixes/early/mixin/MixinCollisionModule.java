package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CollisionModule.class)
public class MixinCollisionModule {

    private static final HytaleLogger refixes$LOGGER = Logs.logger();
    private static volatile boolean refixes$emptyBoxLogged = false;

    @Inject(method = "findBlockCollisionsIterative", at = @At("HEAD"), cancellable = true)
    private static void refixes$skipNonFiniteCollision(
            World world,
            Box collider,
            Vector3d pos,
            Vector3d v,
            boolean stopOnCollisionFound,
            CollisionResult result,
            CallbackInfo ci) {
        if (!Double.isFinite(pos.x)
                || !Double.isFinite(pos.y)
                || !Double.isFinite(pos.z)
                || !Double.isFinite(v.x)
                || !Double.isFinite(v.y)
                || !Double.isFinite(v.z)) {
            ci.cancel();
            return;
        }
        Vector3d cmin = collider.getMin();
        Vector3d cmax = collider.getMax();
        if (cmin.x > cmax.x || cmin.y > cmax.y || cmin.z > cmax.z) {
            if (!refixes$emptyBoxLogged) {
                refixes$emptyBoxLogged = true;
                refixes$LOGGER.atWarning().log(
                        "Skipped collision for empty/inverted collider box (min=%s, max=%s) — likely an NPC with an unpopulated model AABB. Logged once.",
                        cmin, cmax);
            }
            ci.cancel();
        }
    }
}
