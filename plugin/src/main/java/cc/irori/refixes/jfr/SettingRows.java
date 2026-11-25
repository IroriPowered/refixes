package cc.irori.refixes.jfr;

import cc.irori.refixes.config.impl.RefixesConfig;
import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.PatchCounters;
import cc.irori.refixes.util.Early;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import java.util.ArrayList;
import java.util.List;

final class SettingRows {

    private SettingRows() {}

    static void emit(String refixesVersion) {
        commit("Refixes version", refixesVersion);
        commit("Hytale server version", serverVersion());
        commit("Early patches", earlyState());
        if (EarlyMetrics.active()) {
            commit("Patch counters", PatchCounters.ENABLED ? "on" : PatchCounters.OFF_MESSAGE);
        }
        if (Early.isEnabled()) {
            commitAll(EarlyOptions.changedSwitches());
        }
        List<String> changed = new ArrayList<>();
        RefixesConfig.get().collectChangedValues("", changed);
        commitAll(changed);
    }

    private static void commitAll(List<String> entries) {
        for (String entry : entries) {
            int split = entry.indexOf('=');
            if (split < 0) {
                commit(entry, "");
            } else {
                commit(entry.substring(0, split), entry.substring(split + 1));
            }
        }
    }

    private static void commit(String name, String value) {
        Recorder.setting(name, value);
    }

    private static String serverVersion() {
        try {
            String version = ManifestUtil.getImplementationVersion();
            return version == null ? "unknown" : version;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String earlyState() {
        if (!Early.isEnabled() || !EarlyOptions.hasMixinSelection()) {
            return "not loaded. Refixes is not running through Hyinit";
        }
        String gate = EarlyOptions.versionGate();
        String gateState = gate == null ? "" : gate.substring(0, Math.max(gate.indexOf(':'), 0));
        String gateVersion = gate == null ? "unknown" : gate.substring(gate.indexOf(':') + 1);
        if (gateState.equals("disabled")) {
            return "disabled by the version gate for Hytale " + gateVersion;
        }
        if (gateState.equals("hyinit")) {
            return "disabled because Hyinit " + gateVersion + " is older than 0.5.0";
        }
        String active = "active, " + EarlyOptions.enabledMixinCount() + " Mixin classes selected";
        if (gateState.equals("overridden")) {
            return active + ". Version gate overridden by OtherServerVersions=AllPatches for Hytale " + gateVersion;
        }
        if (gateState.equals("unchanged")) {
            List<String> skipped = EarlyOptions.skippedSwitches();
            return active + ". Hytale " + gateVersion + " is newer than the version this Refixes build was made for,"
                    + " only switches with unchanged Hytale code are applied, " + skipped.size() + " skipped"
                    + (skipped.isEmpty() ? "" : ": " + String.join(", ", skipped));
        }
        return active;
    }
}
