package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.NPCActivationRecovery;
import cc.irori.refixes.early.util.NPCActivationRecoveryV08;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.components.FailedSpawnComponent;
import com.hypixel.hytale.server.npc.components.NPCComponent;
import com.hypixel.hytale.server.npc.systems.RoleBuilderSystem;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
@Mixin(RoleBuilderSystem.class)
public abstract class MixinNPCRoleActivationV08 {
    @Unique
    private static final Set<String> refixes$REPORTED_ROLES = ConcurrentHashMap.newKeySet();

    @Invoker("fail")
    protected abstract void refixes$discardFailedRole(Holder<EntityStore> holder);

    @WrapMethod(method = "onEntityAdd")
    private void refixes$recoverSavedNPC(
            Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store, Operation<Void> original) {
        EntitySection section = NPCActivationRecovery.section();
        NPCComponent npc = holder.getComponent(NPCComponent.getComponentType());
        if (section == null || reason != AddReason.LOAD || npc == null || npc.getRole() != null) {
            original.call(holder, reason, store);
            return;
        }
        NPCActivationRecovery.Attempt attempt = new NPCActivationRecovery.Attempt(holder);
        NPCActivationRecovery.Attempt previous = NPCActivationRecovery.attempt(attempt);
        try {
            try {
                original.call(holder, reason, store);
            } catch (SkipSentryException | IllegalArgumentException failure) {
                if (attempt.saved == null) throw failure;
                refixes$discardFailedRole(holder);
                if (refixes$REPORTED_ROLES.add(String.valueOf(npc.getRoleName()))) {
                    Logs.logger()
                            .atWarning()
                            .withCause(failure)
                            .log("Keeping saved NPCs with unavailable role %s", npc.getRoleName());
                }
            }
            if (attempt.saved != null && holder.getComponent(FailedSpawnComponent.getComponentType()) != null) {
                section.loadEntityHolder(attempt.saved);
            }
        } finally {
            NPCActivationRecovery.attempt(previous);
        }
    }

    @WrapOperation(
            method = "onEntityAdd",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/NPCPlugin;get()Lcom/hypixel/hytale/server/npc/NPCPlugin;"),
            require = 1,
            allow = 1)
    private NPCPlugin refixes$saveBeforeRoleBuild(
            Operation<NPCPlugin> original, @Local(argsOnly = true) Holder<EntityStore> holder) {
        NPCActivationRecoveryV08.capture(holder);
        return original.call();
    }
}
