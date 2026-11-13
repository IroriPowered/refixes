package com.hypixel.hytale.component;
import java.util.ArrayList;
import java.util.List;
public final class Store<E> {
public final List<Ref<E>> refs = new ArrayList<>();
public Thread ownerThread;
public volatile boolean shutdown;
public int tickingRemovals;
private final E data;
public Store(E data) { this.data=data; }
public E getExternalData() { return data; }
public boolean isShutdown() { return shutdown; }
public boolean isInThread() { return Thread.currentThread() == ownerThread; }
public void assertThread() { if(!isInThread()) throw new IllegalStateException("wrong owner"); }
@SuppressWarnings("unchecked") public <T> T getComponent(Ref<E> ref,ComponentType<E,T> type) { return (T)ref.components.get(type); }
public void tryRemoveComponent(Ref<E> ref,ComponentType<E,?> type) { assertThread(); tickingRemovals++; ref.components.remove(type); }
}
