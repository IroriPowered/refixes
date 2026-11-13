package cc.irori.refixes.util;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/** Checks heap pressure after major GC or old-generation reclamation. */
public final class HeapPressureMonitor {

    private final List<Registration> registrations = new ArrayList<>();
    private volatile long heapMax = -1L;
    private volatile List<MemoryPoolMXBean> heapPools = List.of();

    private record Registration(NotificationEmitter emitter, NotificationListener listener) {}

    /** Live after-GC heap ratio from the latest collection of each heap pool, or 0 if unavailable. */
    public double currentAfterGcRatio() {
        long max = heapMax;
        if (max <= 0) {
            return 0.0;
        }
        long used = 0L;
        boolean any = false;
        for (MemoryPoolMXBean pool : heapPools) {
            MemoryUsage collectionUsage = pool.getCollectionUsage();
            if (collectionUsage != null) {
                used += collectionUsage.getUsed();
                any = true;
            }
        }
        return any ? (double) used / max : 0.0;
    }

    public void start(double thresholdRatio, Runnable onPressure) {
        long max = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        if (max <= 0) {
            return;
        }
        heapMax = max;
        heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .collect(Collectors.toList());
        Set<String> heapPoolNames =
                heapPools.stream().map(MemoryPoolMXBean::getName).collect(Collectors.toSet());
        Set<String> oldGenPoolNames = heapPoolNames.stream()
                .filter(name -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.contains("old") || lower.contains("tenured");
                })
                .collect(Collectors.toSet());

        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter emitter)) {
                continue;
            }
            NotificationListener listener = (Notification notification, Object handback) -> {
                if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                    return;
                }
                GarbageCollectionNotificationInfo info =
                        GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                String gcName = info.getGcName() == null ? "" : info.getGcName().toLowerCase(Locale.ROOT);
                String action =
                        info.getGcAction() == null ? "" : info.getGcAction().toLowerCase(Locale.ROOT);
                // Skip young/minor collections and concurrent sub-pauses; only major/whole-heap GCs.
                boolean youngOrPause = gcName.contains("minor")
                        || gcName.contains("young")
                        || action.contains("minor")
                        || gcName.contains("pause")
                        || action.contains("pause");
                if (youngOrPause && !reclaimedOldGen(info.getGcInfo(), oldGenPoolNames)) {
                    return;
                }
                long usedAfterGc = info.getGcInfo().getMemoryUsageAfterGc().entrySet().stream()
                        .filter(entry -> heapPoolNames.contains(entry.getKey()))
                        .mapToLong(entry -> entry.getValue().getUsed())
                        .sum();
                if ((double) usedAfterGc / max >= thresholdRatio) {
                    onPressure.run();
                }
            };
            emitter.addNotificationListener(listener, null, null);
            registrations.add(new Registration(emitter, listener));
        }
    }

    private static boolean reclaimedOldGen(GcInfo gcInfo, Set<String> oldGenPoolNames) {
        Map<String, MemoryUsage> before = gcInfo.getMemoryUsageBeforeGc();
        for (Map.Entry<String, MemoryUsage> after :
                gcInfo.getMemoryUsageAfterGc().entrySet()) {
            MemoryUsage prior = before.get(after.getKey());
            if (oldGenPoolNames.contains(after.getKey())
                    && prior != null
                    && after.getValue().getUsed() < prior.getUsed()) {
                return true;
            }
        }
        return false;
    }

    public void stop() {
        for (Registration registration : registrations) {
            try {
                registration.emitter().removeNotificationListener(registration.listener());
            } catch (Exception ignored) {
            }
        }
        registrations.clear();
        heapPools = List.of();
        heapMax = -1L;
    }
}
