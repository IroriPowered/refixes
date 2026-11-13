package cc.irori.refixes.service;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChunkLoaderLifecycleRegression {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("chunkloader-regression");
        ChunkLoaderService service = new ChunkLoaderService(directory);
        World world = new World("first");
        ChunkStore chunks = world.getChunkStore();
        service.addChunk(world, 3, -2, "kept", -9);
        world.drain();
        require(chunks.columns.size() == 1, "column request duplicated");
        WorldChunk column = new WorldChunk();
        Ref<ChunkStore> columnRef = new Ref<ChunkStore>().put(WorldChunk.getComponentType(), column);
        chunks.columns.getFirst().complete(columnRef);
        world.drain();
        require(column.keeps == 1 && chunks.sections.size() == 1, "column not acquired before section load");
        ChunkSection section = new ChunkSection(3, -9, -2);
        Ref<ChunkStore> ref = sectionRef(world, section);
        chunks.sections.getFirst().complete(ref);
        world.drain();
        require(section.keeps == 1, "section not natively retained");
        int scans = chunks.getStore().scans;
        world.onOwner(() -> {
            service.retainSections(chunks.getStore());
            service.retainSections(chunks.getStore());
        });
        require(chunks.getStore().scans == scans, "whole-store scan repeated every tick");
        require(section.acquired == 1 && section.active >= 3, "residency confused with ticking");
        require(chunks.getStore().tickingRemovals >= 2, "NonTicking never removed");
        ChunkSection discovered = new ChunkSection(3, 200, -2);
        Ref<ChunkStore> discoveredRef = sectionRef(world, discovered);
        world.onOwner(() -> service.sectionAdded(chunks.getStore(), discoveredRef, discovered));
        require(discovered.keeps == 1, "new section Y not retained");
        ChunkSection replacement = new ChunkSection(3, -9, -2);
        ref.put(ChunkSection.getComponentType(), replacement);
        world.onOwner(() -> service.retainSections(chunks.getStore()));
        require(section.keeps == 0 && replacement.keeps == 1, "component replacement leaked hold");
        long index = ChunkUtil.indexChunk(3, -2);
        service.removeChunk(world, 3, -2);
        service.addChunk(world, 3, -2, "pending", 0);
        world.drain();
        service.removeChunk(world, 3, -2);
        service.addChunk(world, 3, -2, "readded", 0);
        world.drain();
        require(replacement.keeps == 1, "remove/re-add released new registration");
        WorldChunk staleColumn = new WorldChunk();
        chunks.columns.get(1).complete(new Ref<ChunkStore>().put(WorldChunk.getComponentType(), staleColumn));
        world.drain();
        require(staleColumn.keeps == 0, "stale column completion acquired a hold");
        WorldChunk currentColumn = new WorldChunk();
        chunks.columns.get(2).complete(new Ref<ChunkStore>().put(WorldChunk.getComponentType(), currentColumn));
        world.drain();
        require(currentColumn.keeps == 1, "new registration did not acquire column");
        var pendingSection = chunks.sections.getLast();
        service.unloadWorld(world);
        world.drain();
        require(currentColumn.keeps == 0 && replacement.keeps == 0 && discovered.keeps == 0, "world unload leaked native holds");
        ChunkSection late = new ChunkSection(3, 0, -2);
        pendingSection.complete(sectionRef(world, late));
        world.drain();
        require(late.keeps == 0, "late completion after unload retained section");
        World next = new World("first");
        service.loadWorld(next);
        next.drain();
        WorldChunk lastColumn = new WorldChunk();
        next.getChunkStore().columns.getFirst().complete(new Ref<ChunkStore>().put(WorldChunk.getComponentType(), lastColumn));
        next.drain();
        require(lastColumn.keeps == 1, "world replacement failed");
        World replacementWorld = new World("first");
        service.loadWorld(replacementWorld);
        require(lastColumn.keeps == 0, "live world replacement did not release old world");
        replacementWorld.drain();
        WorldChunk finalColumn = new WorldChunk();
        replacementWorld.getChunkStore().columns.getFirst().complete(new Ref<ChunkStore>().put(WorldChunk.getComponentType(), finalColumn));
        replacementWorld.drain();
        require(finalColumn.keeps == 1, "replacement world did not acquire fresh hold");
        World failing = new World("failure");
        service.addChunk(failing, 1, 1, "retry", 0);
        failing.drain();
        failing.getChunkStore().columns.getFirst().completeExceptionally(new IllegalStateException("expected"));
        failing.drain();
        failing.onOwner(() -> service.retainSections(failing.getChunkStore().getStore()));
        require(failing.getChunkStore().columns.size() == 1, "failure retry was not throttled");
        Thread.sleep(1100);
        failing.onOwner(() -> service.retainSections(failing.getChunkStore().getStore()));
        require(failing.getChunkStore().columns.size() == 2, "failed request stayed permanently pending");
        service.shutdown(true);
        next.drain();
        require(finalColumn.keeps == 0, "shutdown leaked hold");
        String saved = Files.readString(directory.resolve("chunkloaders/first.json"));
        require(saved.contains("200") && saved.contains("readded"), "shutdown did not flush latest discoveries");
        require(!saved.contains("kept"), "stale snapshot won");
        ChunkLoaderService restarted = new ChunkLoaderService(directory);
        require("readded".equals(restarted.getKeptChunks("first").get(index)), "persisted registration not readable");
        restarted.shutdown(false);
        System.out.println("ChunkLoaderLifecycleRegression passed");
    }

    private static Ref<ChunkStore> sectionRef(World world, ChunkSection section) {
        Ref<ChunkStore> ref = new Ref<ChunkStore>().put(ChunkSection.getComponentType(), section);
        world.getChunkStore().getStore().refs.add(ref);
        return ref;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
