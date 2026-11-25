package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.function.Consumer;

public final class NPCActivationRecovery {
    private static final ThreadLocal<EntitySection> SECTION = new ThreadLocal<>();
    private static final ThreadLocal<Attempt> ATTEMPT = new ThreadLocal<>();

    private NPCActivationRecovery() {}

    public static EntitySection section() {
        return SECTION.get();
    }

    public static EntitySection section(EntitySection section) {
        EntitySection previous = SECTION.get();
        if (section == null) SECTION.remove();
        else SECTION.set(section);
        return previous;
    }

    public static Attempt attempt(Attempt attempt) {
        Attempt previous = ATTEMPT.get();
        if (attempt == null) ATTEMPT.remove();
        else ATTEMPT.set(attempt);
        return previous;
    }

    public static void capture(Holder<EntityStore> holder) {
        capture(holder, saved -> {});
    }

    public static void capture(Holder<EntityStore> holder, Consumer<Holder<EntityStore>> prepare) {
        Attempt attempt = ATTEMPT.get();
        if (attempt != null && attempt.holder == holder) {
            Holder<EntityStore> saved = holder.cloneSerializable(EntityStore.REGISTRY.getData());
            prepare.accept(saved);
            attempt.saved = saved;
        }
    }

    public static final class Attempt {
        private final Holder<EntityStore> holder;
        public Holder<EntityStore> saved;

        public Attempt(Holder<EntityStore> holder) {
            this.holder = holder;
        }
    }
}
