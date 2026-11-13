package cc.irori.refixes.early.accessor;

public interface NotificationPacketAccess {

    long refixes$beginNotification();

    long refixes$getNotificationSequence();

    boolean refixes$isCurrentNotification(long sequence);
}
