package cc.irori.refixes.early.accessor;

public interface WorldPauseOwnerAccess {

    void refixes$setPaused(boolean paused, Object owner);

    boolean refixes$isPausedBy(Object owner);
}
