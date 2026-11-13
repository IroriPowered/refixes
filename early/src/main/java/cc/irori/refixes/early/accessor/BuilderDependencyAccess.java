package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.server.npc.asset.builder.Builder;

public interface BuilderDependencyAccess {

    boolean refixes$isDependant(Builder<?> builder, int builderIndex, int dependencyIndex);
}
