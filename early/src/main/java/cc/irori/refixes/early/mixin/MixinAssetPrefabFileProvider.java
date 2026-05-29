package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.buildertools.prefablist.AssetPrefabFileProvider;
import com.hypixel.hytale.common.util.PathUtil;
import com.hypixel.hytale.common.util.StringCompareUtil;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.ui.browser.FileListProvider;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AssetPrefabFileProvider.class)
public abstract class MixinAssetPrefabFileProvider {

    @Unique
    private static final String K_WORLDGEN = "__worldgen";

    @Unique
    private static final String[] EXTRA_KEYS = {K_WORLDGEN};

    @Unique
    private static final int MAX_SEARCH_RESULTS = 50;

    @Unique
    private static final String PREFAB_EXT = ".prefab.json";

    @Unique
    @Nullable
    private static Path refixes$baseFor(String key) {
        PrefabStore s = PrefabStore.get();
        return switch (key) {
            case K_WORLDGEN -> s.getWorldGenPrefabsPath();
            default -> null;
        };
    }

    @Unique
    private static String refixes$displayFor(String key) {
        return switch (key) {
            case K_WORLDGEN -> "WorldGen";
            default -> key;
        };
    }

    @WrapMethod(method = "buildPackListings")
    private List<FileListProvider.FileEntry> refixes$wrapListings(Operation<List<FileListProvider.FileEntry>> orig) {
        List<FileListProvider.FileEntry> out = new ObjectArrayList<>(orig.call());
        for (String key : EXTRA_KEYS) {
            Path base = refixes$baseFor(key);
            if (base != null && Files.isDirectory(base, new LinkOption[0])) {
                out.add(new FileListProvider.FileEntry(key, refixes$displayFor(key), true));
            }
        }
        return out;
    }

    @WrapMethod(method = "resolveVirtualPath")
    private Path refixes$wrapResolve(String virtualPath, Operation<Path> orig) {
        if (virtualPath.isEmpty()) return orig.call(virtualPath);
        String[] parts = virtualPath.split("/", 2);
        Path base = refixes$baseFor(parts[0]);
        if (base == null) return orig.call(virtualPath);
        String sub = parts.length > 1 ? parts[1] : "";
        return sub.isEmpty() ? base : PathUtil.resolvePathWithinDir(base, sub);
    }

    @WrapMethod(method = "buildPackDirectoryListing")
    private List<FileListProvider.FileEntry> refixes$wrapList(
            String currentDirStr, Operation<List<FileListProvider.FileEntry>> orig) {
        String[] parts = currentDirStr.split("/", 2);
        Path base = refixes$baseFor(parts[0]);
        if (base == null) return orig.call(currentDirStr);
        String sub = parts.length > 1 ? parts[1] : "";
        Path target = sub.isEmpty() ? base : PathUtil.resolvePathWithinDir(base, sub);
        return refixes$walkDir(target);
    }

    @WrapMethod(method = "buildSearchResults")
    private List<FileListProvider.FileEntry> refixes$wrapSearch(
            String currentDirStr, String searchQuery, Operation<List<FileListProvider.FileEntry>> orig) {
        String[] parts = currentDirStr.split("/", 2);
        Path syntheticBase = currentDirStr.isEmpty() ? null : refixes$baseFor(parts[0]);
        if (syntheticBase != null) {
            String sub = parts.length > 1 ? parts[1] : "";
            Path root = sub.isEmpty() ? syntheticBase : PathUtil.resolvePathWithinDir(syntheticBase, sub);
            List<FileListProvider.FileEntry> results = new ObjectArrayList<>();
            if (root != null) refixes$searchInRoot(root, parts[0], sub, searchQuery.toLowerCase(), results);
            results.sort(Comparator.comparingInt(FileListProvider.FileEntry::matchScore)
                    .reversed());
            return results.size() > MAX_SEARCH_RESULTS
                    ? new ObjectArrayList<>(results.subList(0, MAX_SEARCH_RESULTS))
                    : results;
        }
        List<FileListProvider.FileEntry> out = new ObjectArrayList<>(orig.call(currentDirStr, searchQuery));
        if (currentDirStr.isEmpty()) {
            String lowerQuery = searchQuery.toLowerCase();
            for (String key : EXTRA_KEYS) {
                Path base = refixes$baseFor(key);
                if (base != null) refixes$searchInRoot(base, key, "", lowerQuery, out);
            }
            out.sort(Comparator.comparingInt(FileListProvider.FileEntry::matchScore)
                    .reversed());
            if (out.size() > MAX_SEARCH_RESULTS) out = new ObjectArrayList<>(out.subList(0, MAX_SEARCH_RESULTS));
        }
        return out;
    }

    @Unique
    private static List<FileListProvider.FileEntry> refixes$walkDir(@Nullable Path dir) {
        List<FileListProvider.FileEntry> out = new ObjectArrayList<>();
        if (dir == null || !Files.isDirectory(dir, new LinkOption[0])) return out;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.startsWith(".")) continue;
                boolean isDir = Files.isDirectory(file, new LinkOption[0]);
                if (!isDir && !name.endsWith(PREFAB_EXT)) continue;
                String display = isDir ? name : name.substring(0, name.length() - PREFAB_EXT.length());
                out.add(new FileListProvider.FileEntry(name, display, isDir));
            }
        } catch (IOException ignored) {
        }
        out.sort((a, b) -> a.isDirectory() == b.isDirectory()
                ? a.displayName().compareToIgnoreCase(b.displayName())
                : a.isDirectory() ? -1 : 1);
        return out;
    }

    @Unique
    private static void refixes$searchInRoot(
            Path root, String key, String basePath, String lowerQuery, List<FileListProvider.FileEntry> out) {
        if (!Files.isDirectory(root, new LinkOption[0])) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fn = file.getFileName().toString();
                    if (!fn.endsWith(PREFAB_EXT)) return FileVisitResult.CONTINUE;
                    String base = fn.substring(0, fn.length() - PREFAB_EXT.length());
                    int score = StringCompareUtil.getFuzzyDistance(base.toLowerCase(), lowerQuery, Locale.ENGLISH);
                    if (score > 0) {
                        Path rel = root.relativize(file);
                        String full = basePath.isEmpty()
                                ? key + "/" + rel.toString().replace('\\', '/')
                                : key + "/" + basePath + "/" + rel.toString().replace('\\', '/');
                        out.add(new FileListProvider.FileEntry(full, base, false, false, score));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }
}
