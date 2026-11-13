package cc.irori.refixes.early.duck;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface MapMarkerLockOwner {
    ReentrantReadWriteLock refixes$markerLock();
}
