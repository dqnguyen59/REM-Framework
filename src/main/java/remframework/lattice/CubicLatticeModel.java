package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CubicLatticeModel implements LatticeModel {
    public static final List<Vector3i> SYSTEM_OFFSETS = List.of(
            new Vector3i(1, 0, 0), new Vector3i(0, 1, 0), new Vector3i(0, 0, 1),
            new Vector3i(-1, 0, 0), new Vector3i(0, -1, 0), new Vector3i(0, 0, -1));

    private final double spacing;
    private final List<Vector3i> offsets;
    private final boolean periodic;
    private final Vector3i fixedDimensions;

    public CubicLatticeModel(double spacing, List<Vector3i> offsets,
                             boolean periodic, Vector3i fixedDimensions) {
        this.spacing = spacing;
        this.offsets = offsets.stream().map(Vector3i::new).toList();
        this.periodic = periodic;
        this.fixedDimensions = fixedDimensions == null ? null : new Vector3i(fixedDimensions);
    }

    @Override public String type() { return "cubic"; }
    @Override public double equilibriumDistance() { return spacing; }

    @Override
    public Vector3i dimensions(Vector3i projectDimensions) {
        return fixedDimensions == null ? new Vector3i(projectDimensions) : new Vector3i(fixedDimensions);
    }

    @Override
    public List<NodeDefinition> nodes(Vector3i projectDimensions) {
        Vector3i d = dimensions(projectDimensions);
        List<NodeDefinition> result = new ArrayList<>(d.x * d.y * d.z);
        for (int z = 0; z < d.z; z++) {
            for (int y = 0; y < d.y; y++) {
                for (int x = 0; x < d.x; x++) {
                    Vector3i index = new Vector3i(x, y, z);
                    Map<Integer, String> neighbors = new LinkedHashMap<>();
                    for (int slot = 0; slot < offsets.size(); slot++) {
                        Vector3i n = new Vector3i(index).add(offsets.get(slot));
                        if (periodic) {
                            n.set(Math.floorMod(n.x, d.x), Math.floorMod(n.y, d.y),
                                  Math.floorMod(n.z, d.z));
                        }
                        if (n.x >= 0 && n.x < d.x && n.y >= 0 && n.y < d.y
                                && n.z >= 0 && n.z < d.z) {
                            neighbors.put(slot, id(n.x, n.y, n.z));
                        }
                    }
                    result.add(new NodeDefinition(id(x, y, z), index,
                            new Vector3d((x - d.x / 2.0) * spacing,
                                         (y - d.y / 2.0) * spacing,
                                         (z - d.z / 2.0) * spacing), neighbors));
                }
            }
        }
        return result;
    }

    private static String id(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
