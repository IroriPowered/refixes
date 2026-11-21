package cc.irori.refixes.jfr;

import jdk.jfr.FlightRecorder;

final class Recorder {

    private static final String COUNTER = "counter";
    private static final String GAUGE = "gauge";

    private static Runnable metricHook;
    private static Runnable settingHook;

    private Recorder() {}

    static void start(String refixesVersion) {
        stopHooks();
        Runnable metrics = RefixesEvents::emitMetrics;
        Runnable settings = () -> SettingRows.emit(refixesVersion);
        FlightRecorder.addPeriodicEvent(MetricEvent.class, metrics);
        metricHook = metrics;
        FlightRecorder.addPeriodicEvent(SettingEvent.class, settings);
        settingHook = settings;
        if (new SettingEvent().isEnabled()) {
            settings.run();
        }
    }

    static void stop() {
        if (metricHook == null) {
            return;
        }
        if (new MetricEvent().isEnabled()) {
            RefixesEvents.emitMetrics();
        }
        stopHooks();
    }

    private static void stopHooks() {
        if (metricHook != null) {
            FlightRecorder.removePeriodicEvent(metricHook);
            metricHook = null;
        }
        if (settingHook != null) {
            FlightRecorder.removePeriodicEvent(settingHook);
            settingHook = null;
        }
    }

    static void action(String category, String message) {
        ActionEvent event = new ActionEvent();
        if (!event.shouldCommit()) {
            return;
        }
        event.category = category;
        event.message = RefixesEvents.redact(message);
        event.commit();
    }

    static void counter(String name, long delta) {
        metric(name, COUNTER, delta);
    }

    static void gauge(String name, double value) {
        metric(name, GAUGE, value);
    }

    private static void metric(String name, String kind, double value) {
        MetricEvent event = new MetricEvent();
        event.name = name;
        event.kind = kind;
        event.value = value;
        event.commit();
    }

    static void setting(String name, String value) {
        SettingEvent event = new SettingEvent();
        event.name = name;
        event.value = value;
        event.commit();
    }
}
