package cc.irori.refixes.early.duck;

import cc.irori.hyinit.api.IfHytale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@IfHytale("<0.8.0-pre.1")
public interface MapMarkerLockOwner {
    ReentrantReadWriteLock refixes$markerLock();
}
