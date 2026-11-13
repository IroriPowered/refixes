package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.BuilderDependencyAccess;
import cc.irori.refixes.early.util.NPCDependencyValidation;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import com.hypixel.hytale.server.npc.util.expression.Scope;
import com.hypixel.hytale.server.npc.validators.NPCLoadTimeValidationHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BuilderManager.class)
public abstract class MixinBuilderDependencyAccess implements BuilderDependencyAccess {

    @Override
    @Invoker("isDependant")
    public abstract boolean refixes$isDependant(Builder<?> builder, int builderIndex, int dependencyIndex);

    @WrapMethod(method = "isDependant")
    private boolean refixes$findDependency(
            Builder<?> builder, int builderIndex, int dependencyIndex, Operation<Boolean> original) {
        if (dependencyIndex < 0) return original.call(builder, builderIndex, dependencyIndex);
        if (builderIndex == dependencyIndex) return true;
        IntOpenHashSet dependencies = new IntOpenHashSet();
        refixes$collectDependencies(builder, builderIndex, dependencies, new IntArrayList());
        return dependencies.contains(dependencyIndex);
    }

    @Unique
    private void refixes$collectDependencies(
            Builder<?> builder, int builderIndex, IntOpenHashSet dependencies, IntArrayList path) {
        BuilderManager manager = (BuilderManager) (Object) this;
        if (path.contains(builderIndex)) {
            throw new SkipSentryException(new IllegalArgumentException(
                    "Cyclic reference detected for builder: " + manager.lookupName(builderIndex)));
        }
        path.add(builderIndex);
        IntArrayList pending = new IntArrayList();
        refixes$queueDependencies(builder, pending);
        for (int i = 0; i < pending.size(); i++) {
            int index = pending.getInt(i);
            if (!dependencies.add(index)) continue;
            var info = manager.tryGetBuilderInfo(index);
            if (info != null) refixes$collectDependencies(info.getBuilder(), index, dependencies, path);
        }
        path.removeInt(path.size() - 1);
    }

    @Unique
    private void refixes$queueDependencies(Builder<?> builder, IntArrayList pending) {
        pending.addAll(builder.getDependencies());
        if (builder.hasDynamicDependencies()) pending.addAll(builder.getDynamicDependencies());
        BuilderManager manager = (BuilderManager) (Object) this;
        for (String name : NPCDependencyValidation.references(builder)) {
            int index = manager.getIndex(name);
            if (index >= 0) pending.add(index);
        }
    }

    @WrapOperation(
            method = "*",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/asset/builder/Builder;validate(Ljava/lang/String;Lcom/hypixel/hytale/server/npc/validators/NPCLoadTimeValidationHelper;Lcom/hypixel/hytale/server/npc/util/expression/ExecutionContext;Lcom/hypixel/hytale/server/npc/util/expression/Scope;Ljava/util/List;)Z"),
            require = 2,
            allow = 2)
    private static boolean refixes$trackValidation(
            Builder<?> builder,
            String name,
            NPCLoadTimeValidationHelper helper,
            ExecutionContext context,
            Scope scope,
            List<String> errors,
            Operation<Boolean> original) {
        return NPCDependencyValidation.validate(
                builder, () -> original.call(builder, name, helper, context, scope, errors));
    }
}
