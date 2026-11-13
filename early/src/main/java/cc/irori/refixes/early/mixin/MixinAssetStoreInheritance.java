package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.JsonAsset;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AssetStore.class)
public abstract class MixinAssetStoreInheritance {

    @Shadow
    @Final
    protected Set<Class<? extends JsonAsset<?>>> loadsBefore;

    @WrapOperation(
            method =
                    "loadContainedAssets(Ljava/lang/String;Ljava/util/Collection;Ljava/util/Map;Lcom/hypixel/hytale/assetstore/AssetUpdateQuery;Z)V",
            at = {
                @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", ordinal = 0),
                @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", ordinal = 2)
            },
            require = 2,
            allow = 2)
    private Set<Map.Entry<Class<?>, Object>> refixes$orderByDeclaredDependencies(
            Map<Class<?>, Object> byClass, Operation<Set<Map.Entry<Class<?>, Object>>> original) {
        Set<Map.Entry<Class<?>, Object>> entries = original.call(byClass);
        if (entries.size() < 2) return entries;
        List<Map.Entry<Class<?>, Object>> remaining = new ArrayList<>(entries);
        Map<Class<?>, Object> ordered = new LinkedHashMap<>();
        while (!remaining.isEmpty()) {
            int next = -1;
            for (int i = 0; i < remaining.size() && next < 0; i++) {
                Class<?> candidate = remaining.get(i).getKey();
                boolean blocked = false;
                for (int j = 0; j < remaining.size() && !blocked; j++) {
                    blocked = j != i && refixes$mustPrecede(remaining.get(j).getKey(), candidate);
                }
                if (!blocked) next = i;
            }
            if (next < 0) {
                for (Map.Entry<Class<?>, Object> entry : remaining) ordered.put(entry.getKey(), entry.getValue());
                break;
            }
            Map.Entry<Class<?>, Object> entry = remaining.remove(next);
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered.entrySet();
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean refixes$mustPrecede(Class<?> first, Class<?> second) {
        AssetStore<?, ?, ?> secondStore = AssetRegistry.getAssetStore((Class) second);
        if (secondStore != null && secondStore.getLoadsAfter().contains(first)) return true;
        AssetStore<?, ?, ?> firstStore = AssetRegistry.getAssetStore((Class) first);
        return firstStore != null && ((MixinAssetStoreInheritance) (Object) firstStore).loadsBefore.contains(second);
    }
}
