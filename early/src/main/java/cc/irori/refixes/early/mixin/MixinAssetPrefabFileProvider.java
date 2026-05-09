package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.buildertools.prefablist.AssetPrefabFileProvider;
import com.hypixel.hytale.common.util.PathUtil;
import com.hypixel.hytale.common.util.StringCompareUtil;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.ui.browser.FileListProvider;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AssetPrefabFileProvider.class)
public abstract class MixinAssetPrefabFileProvider {

    @Unique
    private static final String K_SERVER = "__server";

    @Unique
    private static final String K_WORLDGEN = "__worldgen";

    @Unique
    private static final String K_ASSETROOT = "__assets";

    @Unique
    private static final int MAX_SEARCH_RESULTS = 50;

    @Unique
    private static final String PREFAB_EXT = ".prefab.json";

    @Unique
    @Nullable
    private static Path refixes$baseFor(String key) {
        PrefabStore s = PrefabStore.get();
        return switch (key) {
            case K_SERVER -> s.getServerPrefabsPath();
            case K_WORLDGEN -> s.getWorldGenPrefabsPath();
            case K_ASSETROOT -> s.getAssetRootPath();
            default -> null;
        };
    }

    @Unique
    private static String refixes$displayFor(String key) {
        return switch (key) {
            case K_SERVER -> "Server";
            case K_WORLDGEN -> "WorldGen";
            case K_ASSETROOT -> "AssetRoot";
            default -> key;
        };
    }

    @ModifyReturnValue(method = "buildPackListings", at = @At("RETURN"))
    private List<FileListProvider.FileEntry> refixes$addExtraRoots(List<FileListProvider.FileEntry> orig) {
        List<FileListProvider.FileEntry> out = new ObjectArrayList<>(orig);
        for (String key : new String[] {K_SERVER, K_WORLDGEN, K_ASSETROOT}) {
            Path base = refixes$baseFor(key);
            if (base != null && Files.isDirectory(base, new LinkOption[0])) {
                out.add(new FileListProvider.FileEntry(key, refixes$displayFor(key), true));
            }
        }
        return out;
    }

    @Inject(method = "resolveVirtualPath", at = @At("HEAD"), cancellable = true)
    private void refixes$resolveExtra(String virtualPath, CallbackInfoReturnable<Path> cir) {
        if (virtualPath.isEmpty()) return;
        String[] parts = virtualPath.split("/", 2);
        Path base = refixes$baseFor(parts[0]);
        if (base == null) return;
        String sub = parts.length > 1 ? parts[1] : "";
        cir.setReturnValue(sub.isEmpty() ? base : PathUtil.resolvePathWithinDir(base, sub));
    }

    @Inject(method = "buildPackDirectoryListing", at = @At("HEAD"), cancellable = true)
    private void refixes$listExtra(String currentDirStr, CallbackInfoReturnable<List<FileListProvider.FileEntry>> cir) {
        String[] parts = currentDirStr.split("/", 2);
        Path base = refixes$baseFor(parts[0]);
        if (base == null) return;
        String sub = parts.length > 1 ? parts[1] : "";
        Path target = sub.isEmpty() ? base : PathUtil.resolvePathWithinDir(base, sub);
        cir.setReturnValue(refixes$walkDir(target));
    }

    @Inject(method = "buildSearchResults", at = @At("HEAD"), cancellable = true)
    private void refixes$searchExtra(
            String currentDirStr,
            String searchQuery,
            CallbackInfoReturnable<List<FileListProvider.FileEntry>> cir) {
        String lowerQuery = searchQuery.toLowerCase();
        List<FileListProvider.FileEntry> all = new ObjectArrayList<>();

        if (currentDirStr.isEmpty()) {
            for (PrefabStore.AssetPackPrefabPath p : PrefabStore.get().getAllAssetPrefabPaths()) {
                refixes$searchInRoot(p.prefabsPath(), p.getDisplayName(), "", lowerQuery, all);
            }
            for (String key : new String[] {K_SERVER, K_WORLDGEN, K_ASSETROOT}) {
                Path base = refixes$baseFor(key);
                if (base != null) refixes$searchInRoot(base, key, "", lowerQuery, all);
            }
        } else {
            String[] parts = currentDirStr.split("/", 2);
            String sub = parts.length > 1 ? parts[1] : "";
            Path base = refixes$baseFor(parts[0]);
            if (base == null) {
                for (PrefabStore.AssetPackPrefabPath p : PrefabStore.get().getAllAssetPrefabPaths()) {
                    if (parts[0].equals(p.getDisplayName())) {
                        base = p.prefabsPath();
                        break;
                    }
                }
            }
            if (base != null) {
                Path root = sub.isEmpty() ? base : PathUtil.resolvePathWithinDir(base, sub);
                if (root != null) refixes$searchInRoot(root, parts[0], sub, lowerQuery, all);
            }
        }

        all.sort(Comparator.comparingInt(FileListProvider.FileEntry::matchScore).reversed());
        cir.setReturnValue(
                all.size() > MAX_SEARCH_RESULTS
                        ? new ObjectArrayList<>(all.subList(0, MAX_SEARCH_RESULTS))
                        : all);
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
            Path root,
            String key,
            String basePath,
            String lowerQuery,
            List<FileListProvider.FileEntry> out) {
        if (!Files.isDirectory(root, new LinkOption[0])) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fn = file.getFileName().toString();
                    if (!fn.endsWith(PREFAB_EXT)) return FileVisitResult.CONTINUE;
                    String base = fn.substring(0, fn.length() - PREFAB_EXT.length());
                    int score =
                            StringCompareUtil.getFuzzyDistance(base.toLowerCase(), lowerQuery, Locale.ENGLISH);
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
