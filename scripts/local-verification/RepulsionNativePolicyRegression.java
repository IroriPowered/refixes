package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.RepulsionPolicy;

import com.hypixel.hytale.component.spatial.KDTree;
import com.hypixel.hytale.component.spatial.SpatialData;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;

public final class RepulsionNativePolicyRegression {
    public static void main(String[] args) {
        SpatialData<String> spatialData = new SpatialData<>();
        spatialData.addCapacity(4);
        spatialData.append(new Vector3d(3, 0, 0), "east");
        spatialData.append(new Vector3d(0, 0, 1), "near");
        spatialData.append(new Vector3d(-2, 0, 0), "west");
        spatialData.append(new Vector3d(0, 0, -4), "far");
        KDTree<String> tree = new KDTree<>(value -> true);
        tree.rebuild(spatialData);
        List<String> expected = new ArrayList<>();
        tree.ordered(new Vector3d(), 8, expected);
        for (int pass = 0; pass < 3; pass++) {
            @SuppressWarnings("unchecked")
            java.util.List<String> results = (java.util.List<String>) (java.util.List<?>) RepulsionPolicy.borrow();
            tree.ordered(new Vector3d(), 8, results);
            check(expected.equals(results), "native ordered spatial query changed on reuse pass " + pass);
            RepulsionPolicy.release((it.unimi.dsi.fastutil.objects.ReferenceArrayList<Object>) (Object) results);
        }
        System.out.println("RepulsionNativePolicyRegression PASS native KDTree ordering through reused result lists");
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
