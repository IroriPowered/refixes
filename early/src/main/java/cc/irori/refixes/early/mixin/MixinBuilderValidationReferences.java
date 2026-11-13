package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.NPCDependencyValidation;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;
import com.hypixel.hytale.server.npc.asset.builder.BuilderObjectReferenceHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BuilderObjectReferenceHelper.class)
public abstract class MixinBuilderValidationReferences {
    @WrapOperation(
            method =
                    "getBuilder(Lcom/hypixel/hytale/server/npc/asset/builder/BuilderManager;Lcom/hypixel/hytale/server/npc/util/expression/ExecutionContext;Lcom/hypixel/hytale/server/npc/asset/builder/Builder;)Lcom/hypixel/hytale/server/npc/asset/builder/Builder;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/asset/builder/BuilderManager;getIndex(Ljava/lang/String;)I"),
            require = 1,
            allow = 1)
    private int refixes$recordReference(BuilderManager manager, String name, Operation<Integer> original) {
        NPCDependencyValidation.reference(name);
        return original.call(manager, name);
    }
}
