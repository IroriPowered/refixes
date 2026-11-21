package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.NPCComponentTypeCache;
import com.hypixel.hytale.codec.DirectDecodeCodec;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.EntityRegistration;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityModule.class)
public class MixinNPCComponentType {

    @Unique
    private final NPCComponentTypeCache refixes$npcType = new NPCComponentTypeCache();

    @Redirect(
            method = "getComponentType",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$cachedNpcType(Map<?, ?> map, Object key) {
        if (key != NPCEntity.class) return map.get(key);
        return refixes$npcType.get(map, key);
    }

    @WrapMethod(method = "registerEntity")
    private EntityRegistration refixes$registerEntity(
            String id,
            Class<?> type,
            Function<?, ?> constructor,
            DirectDecodeCodec<?> codec,
            Operation<EntityRegistration> original) {
        refixes$npcType.beginWrite();
        try {
            return original.call(id, type, constructor, codec);
        } finally {
            refixes$npcType.endWrite();
        }
    }

    @WrapMethod(method = "unregisterEntity")
    private void refixes$unregisterEntity(Class<?> type, Operation<Void> original) {
        refixes$npcType.beginWrite();
        try {
            original.call(type);
        } finally {
            refixes$npcType.endWrite();
        }
    }
}
