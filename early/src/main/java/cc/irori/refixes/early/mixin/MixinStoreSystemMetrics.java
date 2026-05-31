package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.metrics.metric.HistoricMetric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Store.class)
public class MixinStoreSystemMetrics {

    @Redirect(
            method = "tickInternal",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/metrics/metric/HistoricMetric;add(JJ)V"))
    private void refixes$skipSystemMetric(HistoricMetric metric, long timestampNanos, long value) {}
}
