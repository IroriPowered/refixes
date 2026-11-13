package com.hypixel.hytale.component;
public final class CommandBuffer<E> {
public void tryRemoveComponent(Ref<E> ref,ComponentType<E,?> type) {ref.components.remove(type);}
}
