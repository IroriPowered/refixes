package com.hypixel.hytale.component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class CommandBufferRemovalRegression {
    private CommandBufferRemovalRegression() {}

    public static final class Marker implements Component<Object> {
        @Override
        public Component<Object> clone() {
            return new Marker();
        }
    }

    public static void assertPatched() throws Exception {
        ComponentRegistry<Object> registry = new ComponentRegistry<>();
        ComponentType<Object, Marker> type = registry.registerComponent(Marker.class, Marker::new);
        Store<Object> store = registry.addStore(new Object(), EmptyResourceStorage.get());
        ComponentRegistry<Object> otherRegistry = new ComponentRegistry<>();
        ComponentType<Object, Marker> otherType = otherRegistry.registerComponent(Marker.class, Marker::new);
        Store<Object> other = otherRegistry.addStore(new Object(), EmptyResourceStorage.get());
        Method queued = CommandBuffer.class.getDeclaredMethod(
                "lambda$removeEntity$0", Ref.class, RemoveReason.class, Throwable.class, Store.class);
        queued.setAccessible(true);

        StringBuilder failures = new StringBuilder();
        Ref<Object> ref = store.addEntity(Archetype.of(type), AddReason.SPAWN);
        Throwable source = new Throwable("queued removal");
        check(failures, () -> {
            invoke(queued, ref, source, store);
            try {
                ref.validate();
                throw new AssertionError("Queued removal left the entity valid");
            } catch (IllegalStateException removed) {
                if (removed.getCause() != source) {
                    throw new AssertionError("Queued removal dropped its proxy source: " + removed.getCause());
                }
            }
        });
        check(failures, () -> invoke(queued, ref, new Throwable("repeated removal"), store));
        Ref<Object> foreign = other.addEntity(Archetype.of(otherType), AddReason.SPAWN);
        check(failures, () -> {
            invoke(queued, foreign, new Throwable("foreign removal"), store);
            if (!foreign.isValid()) throw new AssertionError("Queued removal removed an entity from another store");
        });
        if (!failures.isEmpty()) throw new AssertionError(failures.toString());
    }

    private interface Check {
        void run() throws Exception;
    }

    private static void check(StringBuilder failures, Check check) {
        try {
            check.run();
        } catch (Throwable failure) {
            failures.append(System.lineSeparator()).append(failure).append(" cause=").append(failure.getCause());
        }
    }

    private static void invoke(Method queued, Ref<Object> ref, Throwable source, Store<Object> store)
            throws Exception {
        try {
            queued.invoke(null, ref, RemoveReason.REMOVE, source, store);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Queued removal threw for " + source.getMessage(), failure.getCause());
        }
    }
}
