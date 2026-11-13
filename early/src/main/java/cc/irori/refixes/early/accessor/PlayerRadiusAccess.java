package cc.irori.refixes.early.accessor;

public interface PlayerRadiusAccess {

    long refixes$getHotRadiusRevision();

    void refixes$restoreHotRadiusRevision(long revision);

    long refixes$getMinRadiusRevision();

    long refixes$getViewRadiusRevision();

    void refixes$recordViewRadiusWrite();
}
