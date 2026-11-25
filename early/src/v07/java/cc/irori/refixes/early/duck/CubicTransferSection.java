package cc.irori.refixes.early.duck;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.CubicTransfer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@IfHytale("<0.8.0-pre.1")
public interface CubicTransferSection {

    List<CubicTransfer.Pending> refixes$peekTransfers();

    List<CubicTransfer.Pending> refixes$ensureTransfers();

    Set<UUID> refixes$peekFence();

    Set<UUID> refixes$ensureFence();
}
