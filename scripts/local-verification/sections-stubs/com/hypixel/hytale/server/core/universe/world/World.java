package com.hypixel.hytale.server.core.universe.world;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.concurrent.LinkedBlockingQueue;
public final class World {
private final String name;
private final ChunkStore chunks=new ChunkStore(this);
private final LinkedBlockingQueue<Runnable> queue=new LinkedBlockingQueue<>();
public volatile boolean alive=true;
public World(String name) {this.name=name;}
public String getName() {return name;}
public ChunkStore getChunkStore() {return chunks;}
public boolean isAlive() {return alive;}
public boolean isInThread() {return chunks.getStore().isInThread();}
public void execute(Runnable action) {queue.add(action);}
public void drain() {onOwner(() -> {Runnable action;while((action=queue.poll())!=null) action.run();});}
public void onOwner(Runnable action) {Thread old=chunks.getStore().ownerThread;chunks.getStore().ownerThread=Thread.currentThread();try{action.run();}finally{chunks.getStore().ownerThread=old;}}
}
