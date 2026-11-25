package cc.irori.refixes.early.duck;

import cc.irori.hyinit.api.IfHytale;

@IfHytale("<0.8.0-pre.1")
public interface EntitySectionLoad {

    boolean refixes$isSectionLoadPending();

    void refixes$setSectionLoadPending(boolean pending);
}
