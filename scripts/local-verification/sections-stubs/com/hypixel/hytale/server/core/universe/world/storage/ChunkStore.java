package com.hypixel.hytale.server.core.universe.world.storage;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
public final class ChunkStore {
public static final Registry REGISTRY=new Registry();
public static final class Registry {
private final ComponentType<ChunkStore,Object> nonTicking=new ComponentType<>();
public ComponentType<ChunkStore,Object> getNonTickingComponentType() {return nonTicking;}
}
public final List<CompletableFuture<Ref<ChunkStore>>> columns=new ArrayList<>();
public final List<CompletableFuture<Ref<ChunkStore>>> sections=new ArrayList<>();
public final List<Integer> sectionYs=new ArrayList<>();
private final World world;
private final Store<ChunkStore> store;
public ChunkStore(World world) {this.world=world;store=new Store<>(this);}
public World getWorld() {return world;}
public Store<ChunkStore> getStore() {return store;}
public CompletableFuture<Ref<ChunkStore>> getChunkReferenceAsync(long index,int flags) {
if(flags!=GetChunkFlags.SET_TICKING) throw new AssertionError("column not ticking");
var f=new CompletableFuture<Ref<ChunkStore>>();columns.add(f);return f;
}
public CompletableFuture<Ref<ChunkStore>> getChunkSectionReferenceAsync(int x,int y,int z,int flags) {
if(flags!=GetChunkFlags.SET_TICKING) throw new AssertionError("section not ticking");
var f=new CompletableFuture<Ref<ChunkStore>>();sections.add(f);sectionYs.add(y);return f;
}
public CompletableFuture<Ref<ChunkStore>> section(int y) {return sections.get(sectionYs.lastIndexOf(y));}
}
