package cc.irori.refixes.compat;

import cc.irori.refixes.config.impl.RefixesConfig;
import java.lang.reflect.Method;
import java.util.function.DoubleSupplier;

public final class BlackboxBridge {

    private static final String API_CLASS = "sh.harold.blackbox.hytale.BlackboxApi";
    private static final String OWNER = "refixes";
    private static final long RETRY_INTERVAL_NANOS = 60_000_000_000L;

    private static volatile Binding binding;
    private static volatile boolean attempted;
    private static long lastAttemptNanos;

    private BlackboxBridge() {}

    public static void event(String category, String message) {
        Binding b = available();
        if (b == null || b.event == null) {
            return;
        }
        try {
            b.event.invoke(null, OWNER, category, message);
        } catch (Throwable ignored) {
        }
    }

    public static void gauge(String name, double value) {
        Binding b = available();
        if (b == null || b.gauge == null) {
            return;
        }
        try {
            b.gauge.invoke(null, OWNER, name, value);
        } catch (Throwable ignored) {
        }
    }

    public static void count(String name, long delta) {
        Binding b = available();
        if (b == null || b.count == null) {
            return;
        }
        try {
            b.count.invoke(null, OWNER, name, delta);
        } catch (Throwable ignored) {
        }
    }

    public static AutoCloseable registerGauge(String name, DoubleSupplier supplier) {
        Binding b = available();
        if (b == null || b.registerGauge == null) {
            return () -> {};
        }
        try {
            Object registration = b.registerGauge.invoke(null, OWNER, name, supplier);
            if (registration instanceof AutoCloseable closeable) {
                return closeable;
            }
        } catch (Throwable ignored) {
        }
        return () -> {};
    }

    private static Binding available() {
        if (!integrationEnabled()) {
            return null;
        }
        Binding b = binding;
        if (b != null) {
            return b;
        }
        return resolve();
    }

    private static synchronized Binding resolve() {
        if (binding != null) {
            return binding;
        }
        long now = System.nanoTime();
        if (attempted && now - lastAttemptNanos < RETRY_INTERVAL_NANOS) {
            return null;
        }
        attempted = true;
        lastAttemptNanos = now;
        try {
            Class<?> api = Class.forName(API_CLASS, false, BlackboxBridge.class.getClassLoader());
            Binding resolved = new Binding(
                    find(api, "recordEvent", String.class, String.class, String.class),
                    find(api, "recordGauge", String.class, String.class, double.class),
                    find(api, "recordCount", String.class, String.class, long.class),
                    find(api, "registerGauge", String.class, String.class, DoubleSupplier.class));
            binding = resolved;
            return resolved;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean integrationEnabled() {
        try {
            return RefixesConfig.get().getValue(RefixesConfig.BLACKBOX_INTEGRATION);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method find(Class<?> api, String name, Class<?>... params) {
        try {
            return api.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    private record Binding(Method event, Method gauge, Method count, Method registerGauge) {}
}
