package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.BuilderDependencyAccess;
import cc.irori.refixes.early.accessor.RoleChangeFlagAccess;
import cc.irori.refixes.early.accessor.RoleChangeRequestAccess;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.NPCActivationRecovery;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.entity.EntityRemovalSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.components.NPCComponent;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RoleChangeSystem.class)
public abstract class MixinUnresolvedNPCRoleChange {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final Set<String> refixes$REPORTED_ROLES = ConcurrentHashMap.newKeySet();

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ljava/util/Deque;isEmpty()Z"),
            require = 1,
            allow = 1)
    private boolean refixes$keepNPCsWithUnpreparedRole(
            Deque<Object> requests,
            Operation<Boolean> original,
            @Local(argsOnly = true) Store<EntityStore> store,
            @Share("refixes$reloadSection") LocalRef<EntitySection> reloadSection) {
        reloadSection.set(null);
        while (!original.call(requests) && refixes$keepCurrentRole(requests.peek(), store, reloadSection)) {
            requests.poll();
        }
        return original.call(requests);
    }

    @Unique
    private static boolean refixes$keepCurrentRole(
            Object request, Store<EntityStore> store, LocalRef<EntitySection> reloadSection) {
        RoleChangeRequestAccess access = (RoleChangeRequestAccess) request;
        Ref<EntityStore> ref = access.refixes$getReference();
        if (ref == null
                || !ref.isValid()
                || access.refixes$getState() != null
                || access.refixes$isDetachFromSpawning()) {
            return false;
        }
        NPCComponent npc = store.getComponent(ref, NPCComponent.getComponentType());
        if (npc == null) {
            return false;
        }
        Role role = npc.getRole();
        int roleIndex = access.refixes$getRoleIndex();
        if (role == null || npc.getRoleIndex() != roleIndex) {
            return false;
        }
        NPCPlugin npcPlugin = NPCPlugin.get();
        try {
            BuilderInfo builderInfo = npcPlugin.prepareRoleBuilderInfo(roleIndex);
            if (builderInfo.getBuilder().isSpawnable()) {
                ((BuilderDependencyAccess) npcPlugin.getBuilderManager())
                        .refixes$isDependant(builderInfo.getBuilder(), roleIndex, Integer.MIN_VALUE);
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                Ref<ChunkStore> sectionRef = transform == null ? null : transform.getSectionRef();
                if (sectionRef != null && sectionRef.isValid()) {
                    Store<ChunkStore> chunkStore =
                            store.getExternalData().getWorld().getChunkStore().getStore();
                    if (sectionRef.getStore() == chunkStore) {
                        reloadSection.set(chunkStore.getComponent(sectionRef, EntitySection.getComponentType()));
                    }
                }
                return false;
            }
        } catch (SkipSentryException ignored) {
        }
        ((RoleChangeFlagAccess) role).refixes$setRoleChangeRequested(false);
        String roleName = npc.getRoleName();
        if (refixes$REPORTED_ROLES.add(String.valueOf(roleName))) {
            refixes$LOGGER.atWarning().log(
                    "Keeping live NPCs on their current role because role %s cannot be prepared", roleName);
        }
        return true;
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;addEntity(Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/component/AddReason;)Lcom/hypixel/hytale/component/Ref;"),
            require = 1,
            allow = 1)
    private Ref<EntityStore> refixes$retainFailedReload(
            Store<EntityStore> store,
            Holder<EntityStore> holder,
            AddReason reason,
            Operation<Ref<EntityStore>> original,
            @Share("refixes$reloadSection") LocalRef<EntitySection> reloadSection) {
        EntitySection section = reloadSection.get();
        if (section == null) return original.call(store, holder, reason);
        int held = section.getEntityHolders().size();
        EntitySection previous = NPCActivationRecovery.section(section);
        try {
            return original.call(store, holder, reason);
        } finally {
            NPCActivationRecovery.section(previous);
            if (section.getEntityHolders().size() > held) {
                section.markNeedsSaving();
                refixes$keepParkedRows(store, section.getEntityHolders(), held);
            }
        }
    }

    @Unique
    private static void refixes$keepParkedRows(Store<EntityStore> store, List<Holder<EntityStore>> holders, int held) {
        Set<UUID> parked = new HashSet<>();
        for (int i = held; i < holders.size(); i++) {
            UUIDComponent uuid = holders.get(i).getComponent(UUIDComponent.getComponentType());
            if (uuid != null) parked.add(uuid.getUuid());
        }
        if (parked.isEmpty()) return;
        ChunkSavingSystems.Data data =
                store.getExternalData().getWorld().getChunkStore().getStore().getResource(ChunkStore.SAVE_RESOURCE);
        for (EntityRemovalSystem.PendingRemoval removal : data.drainPendingEntityRemovals()) {
            if (!parked.contains(removal.uuid())) data.queuePendingEntityRemoval(removal);
        }
    }
}
