package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.WatchdogConfig;
import cc.irori.refixes.config.impl.WatchdogRecoveryConfig;
import java.lang.reflect.Method;

public final class WatchdogPolicyRegression {
    public static void main(String[] args) throws Exception {
        try {
            verify();
        } catch (Throwable failure) {
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            throw failure;
        }
    }

    private static void verify() throws Exception {
        WatchdogConfig diagnostics = WatchdogConfig.get();
        require(diagnostics.shouldRegisterService(), "Default diagnostics did not enable watchdog");
        diagnostics.setValue(WatchdogConfig.ENABLED, false);
        require(diagnostics.shouldRegisterService(), "Default crash recovery requires the watchdog");
        WatchdogRecoveryConfig.get().setValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH, false);
        require(!diagnostics.shouldRegisterService(), "Disabled diagnostics started idle watchdog");
        for (var action : java.util.List.of(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH,
                WatchdogRecoveryConfig.AUTO_RESTART_WORLDS, WatchdogRecoveryConfig.HALT_ON_SHUTDOWN_TIMEOUT)) {
            WatchdogRecoveryConfig.get().setValue(action, true);
            require(diagnostics.shouldRegisterService(), "Recovery requires diagnostics enabled: " + action.name());
            WatchdogRecoveryConfig.get().setValue(action, false);
        }
        diagnostics.setValue(WatchdogConfig.ENABLED, true);
        WatchdogRecoveryConfig.get().setValue(WatchdogRecoveryConfig.AUTO_RESTART_WORLDS, true);
        WatchdogService service;
        try {
            service = new WatchdogService();
        } finally {
            WatchdogRecoveryConfig.get().setValue(WatchdogRecoveryConfig.AUTO_RESTART_WORLDS, false);
        }
        java.lang.reflect.Field restart = WatchdogService.class.getDeclaredField("canRestartWorlds");
        restart.setAccessible(true);
        require(!restart.getBoolean(service), "Auto-restart enabled without the early thread accessor");
        Method trigger = WatchdogService.class.getDeclaredMethod("triggerWatchdog", String.class);
        trigger.setAccessible(true);
        long start = System.nanoTime();
        trigger.invoke(null, "diagnostics-only regression");
        require(System.nanoTime() - start < 1_000_000_000L, "Diagnostics waited for destructive recovery");
        Method timeout = WatchdogService.class.getDeclaredMethod("handleShutdownTimeout");
        timeout.setAccessible(true);
        timeout.invoke(null);
        System.out.println("WatchdogPolicyRegression passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
