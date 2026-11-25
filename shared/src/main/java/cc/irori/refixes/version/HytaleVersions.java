package cc.irori.refixes.version;

import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.util.Map;
import java.util.jar.Attributes;

public final class HytaleVersions {

    private static final String DIAGNOSTICS = "cc.irori.hyinit.shared.HyinitDiagnostics";

    private HytaleVersions() {}

    public static String running() {
        String reported = (String) diagnostics("hytaleVersion", new Class<?>[0]);
        return reported != null ? reported : serverManifestVersion();
    }

    public static int hyinitApi() {
        return diagnostics("apiVersion", new Class<?>[0]) instanceof Integer version ? version : -1;
    }

    public static String hyinitVersion() {
        return diagnostics("snapshot", new Class<?>[0]) instanceof Map<?, ?> snapshot
                        && snapshot.get("hyinitVersion") instanceof String version
                ? version
                : null;
    }

    public static boolean matches(String range, String version) {
        if (range == null || version == null) return false;
        if (version.equals(diagnostics("hytaleVersion", new Class<?>[0])))
            return Boolean.TRUE.equals(diagnostics("hytaleVersionMatches", new Class<?>[] {String.class}, range));
        try {
            return Hytale.matches(range, version);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static Object diagnostics(String method, Class<?>[] types, Object... args) {
        try {
            Class<?> api = Class.forName(DIAGNOSTICS, false, HytaleVersions.class.getClassLoader());
            Method call = api.getMethod(method, types);
            return call.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }

    private static String serverManifestVersion() {
        try {
            var resource = HytaleVersions.class.getClassLoader().getResource("com/hypixel/hytale/Main.class");
            if (resource == null || !(resource.openConnection() instanceof JarURLConnection connection)) return null;
            connection.setUseCaches(false);
            try (var jar = connection.getJarFile()) {
                var manifest = jar.getManifest();
                if (manifest == null) return null;
                String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                return version == null ? null : version.trim();
            }
        } catch (IOException | SecurityException failure) {
            return null;
        }
    }

    private static final class Hytale {

        private Hytale() {}

        static boolean matches(String range, String version) {
            return SemverRange.fromString(range).satisfies(Semver.fromString(version));
        }
    }
}
