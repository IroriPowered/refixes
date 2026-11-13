package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.NotificationPacketAccess;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EnvironmentSection.class)
public abstract class MixinEnvironmentNotificationPacket implements NotificationPacketAccess {

    @Unique
    private volatile long refixes$notificationSequence;

    @Override
    public long refixes$beginNotification() {
        return ++refixes$notificationSequence;
    }

    @Override
    public long refixes$getNotificationSequence() {
        return refixes$notificationSequence;
    }

    @Override
    public boolean refixes$isCurrentNotification(long sequence) {
        return refixes$notificationSequence == sequence;
    }
}
