package cc.irori.refixes.service;

import static local.hyinit.verify.RegressionRunner.require;

import cc.irori.refixes.config.impl.WatchdogConfig;
import cc.irori.refixes.config.impl.WatchdogRecoveryConfig;

public final class WatchdogPolicyRegression {
    public static void main(String[] args) throws Exception {
        try {
            verify();
        } catch (Throwable failure) {
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            throw failure;
        }
    }

    private static void verify() {
        WatchdogConfig diagnostics = WatchdogConfig.get();
        WatchdogRecoveryConfig recovery = WatchdogRecoveryConfig.get();
        var actions = java.util.List.of(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH,
                WatchdogRecoveryConfig.AUTO_RESTART_WORLDS, WatchdogRecoveryConfig.HALT_ON_SHUTDOWN_TIMEOUT);
        diagnostics.setValue(WatchdogConfig.ENABLED, true);
        require(diagnostics.shouldRegisterService(), "Enabled diagnostics did not start the watchdog");
        diagnostics.setValue(WatchdogConfig.ENABLED, false);
        for (var action : actions) recovery.setValue(action, false);
        require(!diagnostics.shouldRegisterService(), "Disabled diagnostics started idle watchdog");
        for (var action : actions) {
            recovery.setValue(action, true);
            require(diagnostics.shouldRegisterService(), "Recovery requires diagnostics enabled: " + action.name());
            recovery.setValue(action, false);
        }
        System.out.println("WatchdogPolicyRegression passed");
    }
}
