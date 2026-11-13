package com.hypixel.hytale.component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
public final class Store<E> {
public final List<Ref<E>> refs = new ArrayList<>();
public Thread ownerThread;
public volatile boolean shutdown;
public int scans;
public int tickingRemovals;
private final E data;
public Store(E data) { this.data=data; }
public E getExternalData() { return data; }
public boolean isShutdown() { return shutdown; }
public boolean isInThread() { return Thread.currentThread() == ownerThread; }
public void assertThread() { if(!isInThread()) throw new IllegalStateException("wrong owner"); }
@SuppressWarnings("unchecked") public <T> T getComponent(Ref<E> ref,ComponentType<E,T> type) { return (T)ref.components.get(type); }
public void tryRemoveComponent(Ref<E> ref,ComponentType<E,?> type) { assertThread(); tickingRemovals++; ref.components.remove(type); }
public <T> void forEachChunk(ComponentType<E,T> type,BiConsumer<Chunk<E>,CommandBuffer<E>> action) {
scans++;
action.accept(new Chunk<>(refs.stream().filter(r -> r.valid && r.components.containsKey(type)).toList()),new CommandBuffer<>());
}
public static final class Chunk<E> {
private final List<Ref<E>> refs;
Chunk(List<Ref<E>> refs) {this.refs=refs;}
public int size() {return refs.size();}
public Ref<E> getReferenceTo(int i) {return refs.get(i);}
@SuppressWarnings("unchecked") public <T> T getComponent(int i,ComponentType<E,T> type) {return (T)refs.get(i).components.get(type);}
}
}
