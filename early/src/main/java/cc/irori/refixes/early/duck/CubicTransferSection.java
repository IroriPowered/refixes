package cc.irori.refixes.early.duck;

import cc.irori.refixes.early.util.CubicTransfer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CubicTransferSection {

    List<CubicTransfer.Pending> refixes$peekTransfers();

    List<CubicTransfer.Pending> refixes$ensureTransfers();

    Set<UUID> refixes$peekFence();

    Set<UUID> refixes$ensureFence();
}
