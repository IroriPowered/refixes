package cc.irori.refixes.early.util;

public final class ParallelFailure {
    private ParallelFailure() {}

    public interface Discardable {
        void clear();
    }

    public interface Managed {
        boolean refixes$isManagedInvocation();
    }

    public static final class Unrecoverable extends Error {
        public Unrecoverable(Throwable cause) {
            super("Parallel task infrastructure failed before worker completion could be established", cause);
        }
    }

    public interface Access {
        void refixes$recordFailure(Throwable failure);

        Throwable refixes$takeFailure();
    }

    public static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        if (next == null || first == next) return first;
        if (next instanceof Error && !(first instanceof Error)) {
            next.addSuppressed(first);
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void rethrow(Throwable failure) throws E {
        if (failure != null) throw (E) failure;
    }
}
