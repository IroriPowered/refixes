package com.hypixel.hytale.server.core.universe.world.chunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
public class WorldChunk {
private static final ComponentType<ChunkStore,WorldChunk> TYPE = new ComponentType<>();
public int keeps;
public int acquired;
public int released;
public static ComponentType<ChunkStore,WorldChunk> getComponentType() {return TYPE;}
public void addKeepLoaded() {keeps++;acquired++;}
public void removeKeepLoaded() {keeps--;released++; if(keeps<0) throw new AssertionError("column underflow");}
}
