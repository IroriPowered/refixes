package local.hyinit.verify;

import static local.hyinit.verify.RegressionRunner.require;

import cc.irori.refixes.early.util.WorldPolicyTransfer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class WorldPolicyTransferRegression {
    private static final Class<?> UNSAFE_CLASS;
    private static final Object UNSAFE;

    static {
        try {
            UNSAFE_CLASS = Class.forName("sun.misc.Unsafe");
            Field field = UNSAFE_CLASS.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = field.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    public static void main(String[] args) {
        try {
            runTests();
        } catch (Throwable failure) {
            failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err)));
            System.exit(1);
        }
    }

    private static void runTests() throws Exception {
        testInvalidReferenceFails();
        testCancellationBeforeAttachment();
        testSourceBarrierAndSingleDestinationTask();
        testTimeoutAndLateTasks();
        testShutdownWithoutSchedulerProgress();
        testDuplicateRefusalAndReferenceChange();
        testRejectedQueuesAndScheduler();
        testNativeHandoffAndFailure();
        require(pendingCount() == 0, "all transfer registry entries must be released");
        System.out.println("WorldPolicyTransferRegression PASS");
    }

    private static void testInvalidReferenceFails() throws Exception {
        Fixture fixture = new Fixture();
        set(Ref.class, fixture.reference, "index", Integer.MIN_VALUE);
        require(fixture.player.getReference() == null && fixture.player.getHolder() == null, "a null native reference alone does not prove a detached holder exists");
        CompletableFuture<PlayerRef> result = fixture.start(fixture.attach());
        fixture.scheduler.poll();
        fixture.drain();
        failed(result);
    }

    private static void testCancellationBeforeAttachment() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<PlayerRef> result = fixture.start(fixture.attach());
        require(result.cancel(false), "waiting transfer must be cancellable");
        fixture.detach();
        fixture.scheduler.poll();
        fixture.drain();
        require(fixture.attaches.get() == 0, "cancellation must suppress queued native attach");
        require(fixture.scheduler.active() == 0, "cancellation must cancel timers");
    }

    private static void testSourceBarrierAndSingleDestinationTask() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<PlayerRef> result = fixture.start(fixture.attach());
        fixture.scheduler.poll();
        require(!result.isDone() && fixture.player.getReference() == fixture.reference, "waiting must preserve source ownership");
        require(fixture.sourceQueue.size() == 1 && fixture.destinationQueue.isEmpty(), "only a source-owner barrier may be queued first");
        fixture.scheduler.poll();
        require(fixture.sourceQueue.size() == 1, "stalled source must have at most one barrier");
        fixture.detach();
        require(fixture.attaches.get() == 0, "a detached reference must not attach before the source-owner barrier");
        fixture.sourceQueue.removeFirst().run();
        fixture.scheduler.poll();
        require(fixture.sourceQueue.isEmpty() && fixture.destinationQueue.size() == 1, "pending destination work must not create more barriers");
        fixture.destinationQueue.removeFirst().run();
        require(result.join() == fixture.player && fixture.attaches.get() == 1, "native attach must run exactly once on the destination owner");
        require(fixture.scheduler.active() == 0, "handoff must cancel retry timers");
    }

    private static void testTimeoutAndLateTasks() throws Exception {
        Fixture expired = new Fixture();
        CompletableFuture<PlayerRef> overdue = WorldPolicyTransfer.awaitRemovalAndAttach(
                expired.destination, expired.player, expired.reference, expired.attach(), expired.scheduler, 0);
        expired.scheduler.poll();
        expired.drain();
        failed(overdue);
        require(expired.sourceQueue.isEmpty() && expired.attaches.get() == 0, "overdue retry must fail even if the timeout scheduler has not progressed");
        Fixture attached = new Fixture();
        CompletableFuture<PlayerRef> stillAttached = attached.start(attached.attach(), 30);
        attached.scheduler.poll();
        Thread.sleep(60);
        attached.drain();
        require(!stillAttached.isDone(), "a barrier queued before the deadline must not fail the transfer");
        attached.scheduler.poll();
        attached.drain();
        failed(stillAttached);
        require(attached.player.getReference() == attached.reference && attached.attaches.get() == 0, "overdue source barrier must fail only while the player is still attached");
        require(attached.scheduler.active() == 0 && pendingCount() == 0, "timeout must cancel recurring work and release the transfer");
        Fixture delayedSource = new Fixture();
        CompletableFuture<PlayerRef> sourceDelayed = delayedSource.start(delayedSource.attach(), 30);
        delayedSource.scheduler.poll();
        delayedSource.sourceQueue.addLast(() -> {
            try {
                delayedSource.detach();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
        Thread.sleep(60);
        delayedSource.drain();
        delayedSource.scheduler.poll();
        delayedSource.drain();
        require(sourceDelayed.join() == delayedSource.player && delayedSource.attaches.get() == 1, "removal queued before the deadline must transfer despite source queue delay");
        Fixture delayedDestination = new Fixture();
        CompletableFuture<PlayerRef> destinationDelayed = delayedDestination.start(delayedDestination.attach(), 30);
        delayedDestination.detach();
        delayedDestination.scheduler.poll();
        delayedDestination.sourceQueue.removeFirst().run();
        Thread.sleep(60);
        delayedDestination.destinationQueue.removeFirst().run();
        require(destinationDelayed.join() == delayedDestination.player && delayedDestination.attaches.get() == 1, "observed removal must not be failed by destination queue delay");
        for (String outcome : List.of("cancel", "shutdown", "ownership")) {
            Fixture fixture = new Fixture();
            CompletableFuture<PlayerRef> result = fixture.start(fixture.attach(), 0);
            fixture.scheduler.poll();
            if (outcome.equals("cancel")) require(result.cancel(false), "overdue waiting transfer must remain cancellable");
            if (outcome.equals("shutdown")) WorldPolicyTransfer.closeWorld(fixture.destination);
            if (outcome.equals("ownership")) {
                Ref<EntityStore> replacement = new Ref<>(fixture.store);
                set(Ref.class, replacement, "index", 1);
                set(PlayerRef.class, fixture.player, "entity", replacement);
            } else {
                fixture.detach();
            }
            fixture.drain();
            failed(result);
            require(fixture.attaches.get() == 0 && fixture.scheduler.active() == 0, "overdue transfer must keep " + outcome + " protection");
        }
    }

    private static void testDuplicateRefusalAndReferenceChange() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<PlayerRef> first = fixture.start(fixture.attach());
        failed(fixture.start(fixture.attach()));
        require(!first.isDone(), "duplicate refusal must not cancel the original operation");
        Ref<EntityStore> replacement = new Ref<>(fixture.store);
        set(Ref.class, replacement, "index", 1);
        set(PlayerRef.class, fixture.player, "entity", replacement);
        fixture.scheduler.poll();
        fixture.sourceQueue.removeFirst().run();
        failed(first);
        require(fixture.attaches.get() == 0 && fixture.player.getReference() == replacement, "changed ownership must be preserved");
    }

    private static void testShutdownWithoutSchedulerProgress() throws Exception {
        for (boolean source : List.of(false, true)) {
            Fixture fixture = new Fixture();
            CompletableFuture<PlayerRef> result = fixture.start(fixture.attach());
            fixture.scheduler.poll();
            WorldPolicyTransfer.closeWorld(source ? fixture.source : fixture.destination);
            failed(result);
            failed(fixture.start(fixture.attach()));
            fixture.detach();
            fixture.drain();
            require(fixture.attaches.get() == 0 && fixture.scheduler.active() == 0, "shutdown must cancel queued transfer without scheduler progress");
            Fixture replacement = new Fixture();
            require(replacement.source.equals(fixture.source), "fixture must reproduce native World equality by name");
            CompletableFuture<PlayerRef> restarted = replacement.start(replacement.attach());
            require(!restarted.isDone(), "a new world instance with the same name must remain usable");
            restarted.cancel(false);
        }
    }

    private static void testRejectedQueuesAndScheduler() throws Exception {
        for (String failure : List.of("source", "destination", "stopped", "scheduler", "holder")) {
            Fixture fixture = new Fixture();
            if (failure.equals("source")) set(World.class, fixture.source, "acceptingTasks", new AtomicBoolean(false));
            if (failure.equals("destination")) set(World.class, fixture.destination, "acceptingTasks", new AtomicBoolean(false));
            if (failure.equals("scheduler")) fixture.scheduler.reject = true;
            CompletableFuture<PlayerRef> result = fixture.start(fixture.attach());
            if (failure.equals("stopped")) set(World.class, fixture.destination, "alive", new AtomicBoolean(false));
            fixture.detach();
            if (failure.equals("holder")) set(PlayerRef.class, fixture.player, "holder", null);
            fixture.scheduler.poll();
            fixture.drain();
            failed(result);
            require(fixture.attaches.get() == 0 && fixture.scheduler.active() == 0, "rejection or invalid lifecycle must clean up without attach: " + failure);
        }
    }

    private static void testNativeHandoffAndFailure() throws Exception {
        for (String outcome : List.of("pending", "null", "exception", "throw")) {
            Fixture fixture = new Fixture();
            CompletableFuture<PlayerRef> nativeResult = new CompletableFuture<>();
            CompletableFuture<PlayerRef> result = fixture.start(() -> {
                fixture.attaches.incrementAndGet();
                if (outcome.equals("null")) return null;
                if (outcome.equals("throw")) throw new IllegalArgumentException("native failure");
                if (outcome.equals("exception")) nativeResult.completeExceptionally(new IllegalArgumentException("native failure"));
                return nativeResult;
            });
            fixture.detach();
            fixture.scheduler.poll();
            fixture.drain();
            require(fixture.attaches.get() == 1 && fixture.scheduler.active() == 0 && pendingCount() == 0, "native handoff must release retry state even when native future remains pending");
            if (outcome.equals("pending")) {
                require(!result.isDone(), "native async completion must be preserved");
                nativeResult.complete(fixture.player);
                require(result.join() == fixture.player, "native result must pass through");
            } else if (outcome.equals("null")) {
                require(result.join() == null, "native inactive-packet null return must complete without a synthetic failure");
            } else {
                failed(result);
            }
        }
    }

    private static final class Fixture {
        final World source = allocate(World.class);
        final World destination = allocate(World.class);
        final ArrayDeque<Runnable> sourceQueue = new ArrayDeque<>();
        final ArrayDeque<Runnable> destinationQueue = new ArrayDeque<>();
        final Store<EntityStore> store = allocate(Store.class);
        final PlayerRef player = allocate(PlayerRef.class);
        final Ref<EntityStore> reference = new Ref<>(store);
        final ManualScheduler scheduler = new ManualScheduler();
        final AtomicInteger attaches = new AtomicInteger();

        Fixture() throws Exception {
            prepareWorld(source, sourceQueue);
            prepareWorld(destination, destinationQueue);
            EntityStore external = allocate(EntityStore.class);
            set(EntityStore.class, external, "world", source);
            set(Store.class, store, "externalData", external);
            set(Ref.class, reference, "index", 0);
            set(PlayerRef.class, player, "entity", reference);
        }

        CompletableFuture<PlayerRef> start(Supplier<CompletableFuture<PlayerRef>> attach) {
            return start(attach, TimeUnit.HOURS.toMillis(1));
        }

        CompletableFuture<PlayerRef> start(Supplier<CompletableFuture<PlayerRef>> attach, long timeoutMillis) {
            return WorldPolicyTransfer.awaitRemovalAndAttach(destination, player, reference, attach, scheduler, timeoutMillis);
        }

        Supplier<CompletableFuture<PlayerRef>> attach() {
            return () -> {
                attaches.incrementAndGet();
                return CompletableFuture.completedFuture(player);
            };
        }

        void detach() throws Exception {
            set(PlayerRef.class, player, "entity", null);
            set(PlayerRef.class, player, "holder", allocate(Holder.class));
        }

        void drain() {
            while (!sourceQueue.isEmpty()) sourceQueue.removeFirst().run();
            while (!destinationQueue.isEmpty()) destinationQueue.removeFirst().run();
        }
    }

    private static final class ManualScheduler extends ScheduledThreadPoolExecutor {
        final List<Task> tasks = new ArrayList<>();
        boolean reject;

        ManualScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return add(command, false);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long delay, long period, TimeUnit unit) {
            return add(command, true);
        }

        Task add(Runnable command, boolean recurring) {
            if (reject) throw new RejectedExecutionException("fixture scheduler shutdown");
            Task task = new Task(command, recurring);
            tasks.add(task);
            return task;
        }

        void poll() {
            List.copyOf(tasks).stream().filter(task -> task.recurring && !task.isDone()).forEach(task -> task.command.run());
        }

        long active() {
            return tasks.stream().filter(task -> !task.isDone()).count();
        }
    }

    private static final class Task extends CompletableFuture<Void> implements ScheduledFuture<Void> {
        final Runnable command;
        final boolean recurring;

        Task(Runnable command, boolean recurring) {
            this.command = command;
            this.recurring = recurring;
        }

        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
    }

    private static void prepareWorld(World world, ArrayDeque<Runnable> queue) throws Exception {
        set(World.class, world, "name", "fixture-world");
        set(World.class, world, "alive", new AtomicBoolean(true));
        set(World.class, world, "acceptingTasks", new AtomicBoolean(true));
        set(World.class, world, "taskQueue", queue);
    }

    private static <T> T allocate(Class<T> type) {
        try {
            return type.cast(UNSAFE_CLASS.getMethod("allocateInstance", Class.class).invoke(UNSAFE, type));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int pendingCount() throws Exception {
        Field field = WorldPolicyTransfer.class.getDeclaredField("PENDING");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).size();
    }

    private static void failed(CompletableFuture<?> result) {
        require(result.isCompletedExceptionally(), "operation must fail without waiting");
    }
}
