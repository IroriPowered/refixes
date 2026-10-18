package cc.irori.refixes.early.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class RecoveryPolicy {
    private RecoveryPolicy() {}

    public static Path quarantineThen(byte[] originalBytes, Path directory, Runnable recovery) throws IOException {
        Files.createDirectories(directory);
        Path temporary = null;
        Path quarantined = directory.resolve("block-section-" + UUID.randomUUID() + ".bin");
        try {
            temporary = Files.createTempFile(directory, "block-section-", ".tmp");
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(originalBytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, quarantined, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
        } catch (IOException | RuntimeException | Error failure) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        recovery.run();
        return quarantined;
    }

    public static Path blockSectionQuarantineDirectory() {
        return Path.of("mods", "IroriPowered_Refixes", "quarantine", "block-sections");
    }
}
