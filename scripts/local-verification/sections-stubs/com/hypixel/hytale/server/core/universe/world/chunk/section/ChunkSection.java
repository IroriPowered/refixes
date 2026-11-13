package com.hypixel.hytale.server.core.universe.world.chunk.section;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
public class ChunkSection {
private static final ComponentType<ChunkStore,ChunkSection> TYPE = new ComponentType<>();
public final int x,y,z;
public int keeps,acquired,released,active;
public ChunkSection(int x,int y,int z) {this.x=x;this.y=y;this.z=z;}
public static ComponentType<ChunkStore,ChunkSection> getComponentType() {return TYPE;}
public int getX() {return x;}
public int getY() {return y;}
public int getZ() {return z;}
public void addKeepLoaded() {keeps++;acquired++;}
public void removeKeepLoaded() {keeps--;released++; if(keeps<0) throw new AssertionError("section underflow");}
public void resetKeepAlive() {}
public void resetActiveTimer() {active++;}
}
