package com.hypixel.hytale.component;
import java.util.HashMap;
import java.util.Map;
public final class Ref<E> {
public boolean valid = true;
public final Map<ComponentType<E,?>,Object> components = new HashMap<>();
public boolean isValid() { return valid; }
public <T> Ref<E> put(ComponentType<E,T> type,T value) { components.put(type,value); return this; }
}
