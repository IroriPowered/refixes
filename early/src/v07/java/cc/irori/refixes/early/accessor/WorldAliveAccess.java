package cc.irori.refixes.early.accessor;

import cc.irori.hyinit.api.IfHytale;
import java.util.concurrent.atomic.AtomicBoolean;

@IfHytale("<0.8.0-pre.1")
public interface WorldAliveAccess {

    AtomicBoolean refixes$getAlive();
}
