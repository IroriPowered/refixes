package cc.irori.refixes.early.copychunks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ChunkBundle {

    private static final int MAGIC = 0x52465843; // "RFXC"
    private static final int VERSION = 1;
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Path CLIPBOARD_ROOT = Paths.get("refixes-clipboards");

    public final String sourceWorldName;
    public final int minChunkX;
    public final int minChunkZ;
    public final int maxChunkX;
    public final int maxChunkZ;
    public final int blockIdVersion;
    public final List<Entry> chunks;

    public static final class Entry {
        public final int chunkX;
        public final int chunkZ;
        public final ByteBuffer blob;

        public Entry(int chunkX, int chunkZ, ByteBuffer blob) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blob = blob;
        }
    }

    public ChunkBundle(
            String sourceWorldName,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ,
            int blockIdVersion,
            List<Entry> chunks) {
        this.sourceWorldName = sourceWorldName;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.maxChunkX = maxChunkX;
        this.maxChunkZ = maxChunkZ;
        this.blockIdVersion = blockIdVersion;
        this.chunks = chunks;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static Path pathFor(String name) {
        return CLIPBOARD_ROOT.resolve(name + ".bundle");
    }

    public void write(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            byte[] worldBytes = sourceWorldName.getBytes(StandardCharsets.UTF_8);
            ByteBuffer header = ByteBuffer.allocate(10 + worldBytes.length + 22).order(ByteOrder.BIG_ENDIAN);
            header.putInt(MAGIC);
            header.putInt(VERSION);
            header.putShort((short) worldBytes.length);
            header.put(worldBytes);
            header.putInt(minChunkX);
            header.putInt(minChunkZ);
            header.putInt(maxChunkX);
            header.putInt(maxChunkZ);
            header.putShort((short) blockIdVersion);
            header.putInt(chunks.size());
            out.write(header.array());

            ByteBuffer entryHeader = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
            for (Entry entry : chunks) {
                entryHeader.clear();
                ByteBuffer dup = entry.blob.duplicate();
                int blobLen = dup.remaining();
                entryHeader.putInt(entry.chunkX);
                entryHeader.putInt(entry.chunkZ);
                entryHeader.putInt(blobLen);
                out.write(entryHeader.array());
                if (blobLen > 0) {
                    if (dup.hasArray()) {
                        out.write(dup.array(), dup.arrayOffset() + dup.position(), blobLen);
                    } else {
                        byte[] tmp = new byte[blobLen];
                        dup.get(tmp);
                        out.write(tmp);
                    }
                }
            }
        }
    }

    public static ChunkBundle read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(10);
            if (head.length < 10) {
                throw new IOException("Bundle truncated in header");
            }
            ByteBuffer headBuf = ByteBuffer.wrap(head).order(ByteOrder.BIG_ENDIAN);
            int magic = headBuf.getInt();
            if (magic != MAGIC) {
                throw new IOException("Bad magic: expected RFXC, got 0x" + Integer.toHexString(magic));
            }
            int version = headBuf.getInt();
            if (version != VERSION) {
                throw new IOException("Unsupported version: " + version);
            }
            int nameLen = Short.toUnsignedInt(headBuf.getShort());
            byte[] nameBytes = in.readNBytes(nameLen);
            if (nameBytes.length < nameLen) {
                throw new IOException("Bundle truncated in source world name");
            }
            String worldName = new String(nameBytes, StandardCharsets.UTF_8);

            byte[] body = in.readNBytes(22);
            if (body.length < 22) {
                throw new IOException("Bundle truncated in range header");
            }
            ByteBuffer bodyBuf = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
            int minCx = bodyBuf.getInt();
            int minCz = bodyBuf.getInt();
            int maxCx = bodyBuf.getInt();
            int maxCz = bodyBuf.getInt();
            int blockIdVersion = Short.toUnsignedInt(bodyBuf.getShort());
            int count = bodyBuf.getInt();
            if (count < 0 || count > 1_048_576) {
                throw new IOException("Implausible chunk count: " + count);
            }

            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte[] entryHead = in.readNBytes(12);
                if (entryHead.length < 12) {
                    throw new IOException("Bundle truncated in chunk #" + i + " header");
                }
                ByteBuffer eb = ByteBuffer.wrap(entryHead).order(ByteOrder.BIG_ENDIAN);
                int cx = eb.getInt();
                int cz = eb.getInt();
                int blobLen = eb.getInt();
                if (blobLen < 0 || blobLen > 16_777_216) {
                    throw new IOException("Implausible blob length: " + blobLen + " at chunk #" + i);
                }
                byte[] blob = in.readNBytes(blobLen);
                if (blob.length < blobLen) {
                    throw new IOException("Bundle truncated in chunk #" + i + " blob");
                }
                entries.add(new Entry(cx, cz, ByteBuffer.wrap(blob)));
            }
            return new ChunkBundle(worldName, minCx, minCz, maxCx, maxCz, blockIdVersion, entries);
        }
    }
}
