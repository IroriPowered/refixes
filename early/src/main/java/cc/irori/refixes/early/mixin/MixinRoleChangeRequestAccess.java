package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.RoleChangeRequestAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.hypixel.hytale.server.npc.systems.RoleChangeSystem$RoleChangeRequest")
public interface MixinRoleChangeRequestAccess extends RoleChangeRequestAccess {

    @Override
    @Accessor("reference")
    Ref<EntityStore> refixes$getReference();

    @Override
    @Accessor("roleIndex")
    int refixes$getRoleIndex();

    @Override
    @Accessor("state")
    String refixes$getState();

    @Override
    @Accessor("detachFromSpawning")
    boolean refixes$isDetachFromSpawning();
}
