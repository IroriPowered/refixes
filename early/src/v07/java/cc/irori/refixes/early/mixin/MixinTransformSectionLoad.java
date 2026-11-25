package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.duck.EntitySectionLoad;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@IfHytale("<0.8.0-pre.1")
@Mixin(TransformComponent.class)
public class MixinTransformSectionLoad implements EntitySectionLoad {

    @Unique
    private boolean refixes$sectionLoadPending;

    @Override
    public boolean refixes$isSectionLoadPending() {
        return refixes$sectionLoadPending;
    }

    @Override
    public void refixes$setSectionLoadPending(boolean pending) {
        refixes$sectionLoadPending = pending;
    }
}
