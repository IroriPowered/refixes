package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.protocol.BoolParamValue;
import com.hypixel.hytale.protocol.DoubleParamValue;
import com.hypixel.hytale.protocol.IntParamValue;
import com.hypixel.hytale.protocol.LongParamValue;
import com.hypixel.hytale.protocol.StringParamValue;
import com.hypixel.hytale.server.core.util.MessageUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MessageUtil.class)
public abstract class MixinMessageSelect {

    @Redirect(
            method = "formatText(Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;)Ljava/lang/String;",
            at = @At(value = "INVOKE", target = "Ljava/lang/Object;toString()Ljava/lang/String;"),
            require = 1,
            allow = 1)
    private static String refixes$plainSelectKey(Object value) {
        return switch (value) {
            case StringParamValue s -> s.value;
            case BoolParamValue b -> Boolean.toString(b.value);
            case DoubleParamValue d -> Double.toString(d.value);
            case IntParamValue i -> Integer.toString(i.value);
            case LongParamValue l -> Long.toString(l.value);
            default -> "";
        };
    }
}
