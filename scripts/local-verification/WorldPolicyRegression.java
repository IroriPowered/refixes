package local.hyinit.verify;

import cc.irori.refixes.early.util.PathfindingBudget;
import cc.irori.refixes.early.util.WorldPolicySaveJoin;
import cc.irori.refixes.early.util.WorldPolicyTaskCoalescer;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldPolicyRegression {
    public static void main(String[] args) {
        try {
            testPathfindingBudgetAccessors();
            testSaveJoinTimeoutAndInterruption();
            testTaskCoalescingAndRejectionCleanup();
        } catch (Throwable failure) {
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            System.exit(1);
        }
        System.out.println("WorldPolicyRegression PASS");
    }

    private static void testPathfindingBudgetAccessors() throws Exception {
        Store<?> store = allocate(Store.class);
        Store<?> otherWorld = allocate(Store.class);
        CommandBuffer<?> buffer = allocate(CommandBuffer.class);
        Ref<?> requester = new Ref<>(store, 0);
        Ref<?> otherRequester = new Ref<>(otherWorld, 0);
        Field owner = CommandBuffer.class.getDeclaredField("store");
        owner.setAccessible(true);
        owner.set(buffer, store);
        PathfindingBudget.reset(store, 1, 60);
        PathfindingBudget.reset(otherWorld, 1, 60);
        long deferrals = PathfindingBudget.deferrals();
        require(PathfindingBudget.tryConsume(store, requester), "native Store accessor must consume the first search");
        require(!PathfindingBudget.tryConsume(store, requester), "native Store accessor must defer once the search budget is spent");
        require(!PathfindingBudget.tryConsume(buffer, requester), "CommandBuffer must share its owner Store budget");
        require(PathfindingBudget.deferrals() - deferrals == 2, "each denied search must be counted");
        require(PathfindingBudget.tryConsume(otherWorld, otherRequester), "another world Store must keep an independent budget");
        require(PathfindingBudget.tryConsumeNodes(store, requester, 50), "native Store accessor must consume node budget");
        require(PathfindingBudget.tryConsumeNodes(buffer, requester, 10), "CommandBuffer must consume remaining owner node budget");
        require(!PathfindingBudget.tryConsumeNodes(store, requester, 50), "exhausted node budget must deny native Store continuation");
        require(PathfindingBudget.tryConsumeNodes(otherWorld, otherRequester, 50), "node budget must stay per world Store");
        PathfindingBudget.reset(store, 1, 60);
        require(PathfindingBudget.tryConsume(store, new Ref<>(store, 1)) && PathfindingBudget.tryConsumeNodes(buffer, new Ref<>(store, 1), 50), "tick reset must restore the owner budget for a waiting requester");
        PathfindingBudget.remove(store);
        require(PathfindingBudget.tryConsume(store, requester) && PathfindingBudget.tryConsume(buffer, requester), "removed world must stop limiting");
        PathfindingBudget.remove(otherWorld);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    private static void testSaveJoinTimeoutAndInterruption() {
        Thread.currentThread().interrupt();
        long started = System.nanoTime();
        WorldPolicySaveJoin.await(new CompletableFuture<>(), 25, TimeUnit.MILLISECONDS, "test save");
        require(Thread.currentThread().isInterrupted(), "save timeout must restore interruption");
        require(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2), "save timeout must be bounded");
        Thread.interrupted();
        require("saved".equals(WorldPolicySaveJoin.await(CompletableFuture.completedFuture("saved"), 1, TimeUnit.SECONDS, "test save")), "completed result must pass through");
        try {
            WorldPolicySaveJoin.await(CompletableFuture.failedFuture(new IllegalStateException("expected")), 1, TimeUnit.SECONDS, "test save");
            throw new AssertionError("failed future must preserve native join failure");
        } catch (java.util.concurrent.CompletionException expected) {
            require(expected.getCause() instanceof IllegalStateException, "save failure cause must be preserved");
        }
    }

    private static void testTaskCoalescingAndRejectionCleanup() {
        Object firstWorld = new Object();
        Object sameWorld = firstWorld;
        Object secondWorld = new Object();
        AtomicInteger ran = new AtomicInteger();
        List<Runnable> queue = new ArrayList<>();
        require(WorldPolicyTaskCoalescer.submit(firstWorld, queue::add, ran::incrementAndGet), "first task must be accepted");
        require(!WorldPolicyTaskCoalescer.submit(sameWorld, queue::add, ran::incrementAndGet), "same world task must coalesce");
        require(WorldPolicyTaskCoalescer.submit(secondWorld, queue::add, ran::incrementAndGet), "distinct worlds must remain independent");
        queue.removeFirst().run();
        queue.removeFirst().run();
        require(ran.get() == 2, "each accepted world task runs once");
        require(WorldPolicyTaskCoalescer.submit(firstWorld, queue::add, ran::incrementAndGet), "completion releases key");
        queue.clear();
        Object rejectedWorld = new Object();
        try {
            WorldPolicyTaskCoalescer.submit(rejectedWorld, ignored -> { throw new IllegalStateException("rejected"); }, () -> {});
            throw new AssertionError("rejected owner task must fail");
        } catch (IllegalStateException expected) {
            require(WorldPolicyTaskCoalescer.submit(rejectedWorld, queue::add, () -> {}), "rejection releases key");
        }
        queue.clear();
        Object shutdownWorld = new Object();
        require(WorldPolicyTaskCoalescer.submit(shutdownWorld, queue::add, ran::incrementAndGet), "live world queues a recalculation");
        WorldPolicyTaskCoalescer.close(shutdownWorld);
        require(!WorldPolicyTaskCoalescer.submit(shutdownWorld, queue::add, ran::incrementAndGet), "shutdown must prevent new recalculations");
        queue.removeFirst().run();
        require(ran.get() == 2, "a queued recalculation must be suppressed after shutdown");
        Object failedWorld = new Object();
        require(WorldPolicyTaskCoalescer.submit(failedWorld, queue::add, () -> { throw new IllegalStateException("failed"); }), "failing task must enqueue");
        try {
            queue.removeFirst().run();
            throw new AssertionError("task failure must propagate");
        } catch (IllegalStateException expected) {
            require(WorldPolicyTaskCoalescer.submit(failedWorld, queue::add, () -> {}), "task failure releases key");
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
