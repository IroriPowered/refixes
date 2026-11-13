package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public interface RoleChangeRequestAccess {

    Ref<EntityStore> refixes$getReference();

    int refixes$getRoleIndex();

    String refixes$getState();

    boolean refixes$isDetachFromSpawning();
}
