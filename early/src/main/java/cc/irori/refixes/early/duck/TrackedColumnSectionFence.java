package cc.irori.refixes.early.duck;

public interface TrackedColumnSectionFence {

    int WRITE = 0;
    int SKIP = 1;
    int RELOAD = 2;
    int DIRTY = 3;

    void refixes$markLoadStart(int y, long sequence);

    void refixes$forget(int y);

    boolean refixes$noteDelta(int y, long sequence, boolean fluid);

    int refixes$decideSnapshot(int y, long ticket, boolean fluid);

    void refixes$commitSnapshot(int y, long ticket, boolean fluid);

    boolean refixes$isLoading(int y);

    boolean refixes$markDirtyIfLoading(int y);

    boolean refixes$takeDirty(int y);
}
