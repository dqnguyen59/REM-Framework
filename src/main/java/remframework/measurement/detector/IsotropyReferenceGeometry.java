package remframework.measurement.detector;

import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact geometry and boundary-safety rules for Step 1 / Task 1. */
public final class IsotropyReferenceGeometry {
    public static final List<Vector3i> RAYS = List.of(
            v(1,0,0), v(0,0,1), v(1,0,1), v(2,0,1), v(1,0,2),
            v(3,0,1), v(1,0,3), v(1,1,0), v(1,1,1), v(2,1,0),
            v(2,1,1), v(3,2,1), v(3,1,2), v(2,1,3), v(1,1,4));
    public static final List<Integer> SHELLS = List.of(24,36,48,60,72,84,96);
    public static final int WARMUP_CYCLES = 5;
    public static final int MEASUREMENT_FIRST_CYCLE = 6;
    public static final int MEASUREMENT_LAST_CYCLE = 11;
    public static final int SOURCE_PERIOD_UPDATES = 18;
    public static final int MEASUREMENT_END_UPDATE = MEASUREMENT_LAST_CYCLE * SOURCE_PERIOD_UPDATES;
    public static final int ABSORPTION_LAYERS = 30;

    private IsotropyReferenceGeometry() { }
    private static Vector3i v(int x, int y, int z) { return new Vector3i(x,y,z); }

    public record DetectorEntry(int directionId, Vector3i primitive, int graphShell,
                                int multiplier, Vector3i relative) {
        public DetectorEntry { primitive = new Vector3i(primitive); relative = new Vector3i(relative); }
        public String primitiveDirection() { return "("+primitive.x+","+primitive.y+","+primitive.z+")"; }
        public String directionIdLabel() { return "R" + String.format("%02d", directionId); }
    }

    public static List<DetectorEntry> entries() {
        List<DetectorEntry> result = new ArrayList<>();
        for (int i=0; i<RAYS.size(); i++) {
            Vector3i ray = RAYS.get(i); int g = graphLength(ray);
            for (int shell : SHELLS) {
                if (shell % g != 0) throw new IllegalStateException("Shell is not divisible by ray length");
                int m = shell / g;
                result.add(new DetectorEntry(i + 1, ray, shell, m,
                        new Vector3i(ray).mul(m)));
            }
        }
        return List.copyOf(result);
    }

    public static int graphLength(Vector3i v) { return Math.abs(v.x)+Math.abs(v.y)+Math.abs(v.z); }
    public static boolean primitive(Vector3i v) {
        return gcd(gcd(Math.abs(v.x), Math.abs(v.y)), Math.abs(v.z)) == 1;
    }
    private static int gcd(int a, int b) { while (b != 0) { int t=a%b; a=b; b=t; } return a; }

    public static int minimumSymmetricHalfSize() {
        int maxOffset = entries().stream().mapToInt(e -> Math.max(Math.abs(e.relative().x),
                Math.max(Math.abs(e.relative().y), Math.abs(e.relative().z)))).max().orElseThrow();
        // A reflected wave can return to the furthest detector in
        // source-to-boundary plus boundary-to-detector updates.
        int halfForReturn = (MEASUREMENT_END_UPDATE + maxOffset) / 2 + 1;
        int halfForAbsorption = maxOffset + ABSORPTION_LAYERS;
        return Math.max(halfForReturn, halfForAbsorption);
    }

    public static int minimumSymmetricDimension() { return 2 * minimumSymmetricHalfSize() + 1; }
    public static int maxOffset() { return entries().stream().mapToInt(e -> Math.max(Math.abs(e.relative().x), Math.max(Math.abs(e.relative().y), Math.abs(e.relative().z)))).max().orElseThrow(); }

    public static void validate() {
        if (RAYS.size()!=15 || SHELLS.size()!=7 || entries().size()!=105) throw new AssertionError("Step 1 detector count mismatch");
        Set<String> unique = new HashSet<>();
        for (Vector3i ray : RAYS) {
            if (!primitive(ray)) throw new AssertionError("Non-primitive ray: "+ray);
            int g=graphLength(ray);
            for (int shell:SHELLS) if (shell%g!=0) throw new AssertionError("Non-divisible shell");
        }
        for (DetectorEntry e:entries()) {
            if (!new Vector3i(e.primitive()).mul(e.multiplier()).equals(e.relative())) throw new AssertionError("Ray mapping mismatch");
            if (graphLength(e.relative())!=e.graphShell()) throw new AssertionError("Graph distance mismatch");
            if (!unique.add(e.graphShell()+":"+e.relative())) throw new AssertionError("Duplicate detector node");
        }
    }
}
