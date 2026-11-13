package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.RoleChangeFlagAccess;
import com.hypixel.hytale.server.npc.role.Role;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Role.class)
public interface MixinRoleChangeFlagAccess extends RoleChangeFlagAccess {

    @Override
    @Accessor("roleChangeRequested")
    void refixes$setRoleChangeRequested(boolean requested);
}
