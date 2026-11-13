package cc.irori.refixes.compat;

import cc.irori.refixes.config.impl.DiagnosticsConfig;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

public final class HyboxBridge {

    private static final String API_CLASS = "io.github.xytronix.hybox.hytale.HyboxApi";
    private static final PluginIdentifier PLUGIN_ID = new PluginIdentifier("Xytronix", "Hybox");
    private static final String OWNER = "refixes";
    private static final long RETRY_INTERVAL_NANOS = 60_000_000_000L;

    private static final Set<Gauge> gauges = ConcurrentHashMap.newKeySet();

    private static volatile Binding binding;
    private static volatile boolean attempted;
    private static long lastAttemptNanos;

    private HyboxBridge() {}

    public static void event(String category, String message) {
        Binding b = available();
        if (b == null) {
            return;
        }
        try {
            b.event.invoke(null, OWNER, category, message);
        } catch (Throwable ignored) {
        }
    }

    public static void count(String name, long delta) {
        Binding b = available();
        if (b == null) {
            return;
        }
        try {
            b.count.invoke(null, OWNER, name, delta);
        } catch (Throwable ignored) {
        }
    }

    public static AutoCloseable registerGauge(String name, DoubleSupplier supplier) {
        Gauge gauge = new Gauge(name, supplier);
        gauges.add(gauge);
        Binding b = available();
        if (b != null) {
            gauge.bind(b);
        }
        return gauge;
    }

    private static Binding available() {
        if (!integrationEnabled()) {
            return null;
        }
        Binding b = binding;
        if (b != null && b.plugin.isEnabled()) {
            return b;
        }
        return resolve();
    }

    private static synchronized Binding resolve() {
        if (binding != null && binding.plugin.isEnabled()) {
            return binding;
        }
        binding = null;
        long now = System.nanoTime();
        if (attempted && now - lastAttemptNanos < RETRY_INTERVAL_NANOS) {
            return null;
        }
        attempted = true;
        lastAttemptNanos = now;
        try {
            if (!(PluginManager.get().getPlugin(PLUGIN_ID) instanceof JavaPlugin plugin) || !plugin.isEnabled()) {
                return null;
            }
            Class<?> api = Class.forName(API_CLASS, false, plugin.getClassLoader());
            Binding resolved = new Binding(
                    plugin,
                    api.getMethod("recordEvent", String.class, String.class, String.class),
                    api.getMethod("recordCount", String.class, String.class, long.class),
                    api.getMethod("registerGauge", String.class, String.class, DoubleSupplier.class));
            binding = resolved;
            for (Gauge gauge : gauges) {
                gauge.bind(resolved);
            }
            return resolved;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean integrationEnabled() {
        try {
            return DiagnosticsConfig.get().getValue(DiagnosticsConfig.HYBOX_INTEGRATION);
        } catch (Throwable t) {
            return false;
        }
    }

    private record Binding(JavaPlugin plugin, Method event, Method count, Method registerGauge) {}

    private static final class Gauge implements AutoCloseable {
        private final String name;
        private final DoubleSupplier supplier;
        private Binding boundTo;
        private AutoCloseable registration;
        private boolean closed;

        private Gauge(String name, DoubleSupplier supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        private synchronized void bind(Binding b) {
            if (closed || boundTo == b) {
                return;
            }
            unregister();
            boundTo = b;
            try {
                if (b.registerGauge.invoke(null, OWNER, name, supplier) instanceof AutoCloseable closeable) {
                    registration = closeable;
                }
            } catch (Throwable ignored) {
            }
        }

        private void unregister() {
            AutoCloseable current = registration;
            registration = null;
            if (current != null) {
                try {
                    current.close();
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public void close() {
            synchronized (this) {
                closed = true;
                unregister();
            }
            gauges.remove(this);
        }
    }
}
