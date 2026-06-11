package cc.irori.refixes.compat;

import cc.irori.refixes.config.impl.RefixesConfig;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Optional bridge to the Blackbox incident reporter (sh.harold.blackbox.hytale.BlackboxApi).
 *
 * Bound purely via reflection so Refixes has no compile-time dependency on Blackbox; every method
 * is a silent no-op when Blackbox is not installed or the top-level "BlackboxIntegration" config
 * toggle is off. Resolution is retried at most once a minute so plugin load order does not matter;
 * diagnostics registrations made before Blackbox resolves are flushed once it does.
 */
public final class BlackboxBridge {

    private static final String API_CLASS = "sh.harold.blackbox.hytale.BlackboxApi";
    private static final long RETRY_INTERVAL_NANOS = 60_000_000_000L;

    private static volatile Binding binding;
    private static volatile boolean attempted;
    private static long lastAttemptNanos;

    private static final List<Object[]> PENDING_DIAGNOSTICS = new CopyOnWriteArrayList<>();

    private BlackboxBridge() {}

    /** A discrete moment worth a marker on the incident report charts. */
    public static void event(String category, String message) {
        Binding b = available();
        if (b == null || b.event == null) {
            return;
        }
        try {
            b.event.invoke(null, category, message);
        } catch (Throwable ignored) {
        }
    }

    /** A sampled level, charted as a line (last value wins per interval). */
    public static void gauge(String name, double value) {
        Binding b = available();
        if (b == null || b.gauge == null) {
            return;
        }
        try {
            b.gauge.invoke(null, name, value);
        } catch (Throwable ignored) {
        }
    }

    /** An additive counter, charted as per-interval sums. */
    public static void count(String name, long delta) {
        Binding b = available();
        if (b == null || b.count == null) {
            return;
        }
        try {
            b.count.invoke(null, name, delta);
        } catch (Throwable ignored) {
        }
    }

    /** Registers a live key/value card for the incident report; queued until Blackbox resolves. */
    public static void registerDiagnostics(String title, Supplier<Map<String, String>> supplier) {
        if (title == null || supplier == null || !integrationEnabled()) {
            return;
        }
        PENDING_DIAGNOSTICS.add(new Object[] {title, supplier});
        Binding b = available();
        if (b != null) {
            flushDiagnostics(b);
        }
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
                    find(api, "recordEvent", String.class, String.class),
                    find(api, "recordGauge", String.class, double.class),
                    find(api, "recordCount", String.class, long.class),
                    find(api, "registerDiagnostics", String.class, Supplier.class));
            binding = resolved;
            flushDiagnostics(resolved);
            return resolved;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void flushDiagnostics(Binding b) {
        if (b.registerDiagnostics == null) {
            return;
        }
        for (Object[] registration : PENDING_DIAGNOSTICS) {
            try {
                b.registerDiagnostics.invoke(null, registration[0], registration[1]);
                PENDING_DIAGNOSTICS.remove(registration);
            } catch (Throwable ignored) {
            }
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

    private record Binding(Method event, Method gauge, Method count, Method registerDiagnostics) {}
}
