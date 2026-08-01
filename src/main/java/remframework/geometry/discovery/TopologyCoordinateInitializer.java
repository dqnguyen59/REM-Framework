package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class TopologyCoordinateInitializer {
    public GeometryDiscoveryModel initialize(GeometryDiscoveryModel topology,
                                             TopologyInitialization mode,
                                             double spacing, long seed) {
        if (mode == TopologyInitialization.EXISTING) return topology.copy();
        List<DiscoveryNode> nodes = new ArrayList<>();
        SplittableRandom random = new SplittableRandom(seed);
        int side = Math.max(2, (int) Math.ceil(Math.cbrt(topology.nodes().size())));
        int index = 0;
        for (DiscoveryNode original : topology.sortedNodes()) {
            Vector3d position;
            if (mode == TopologyInitialization.CUBIC_GRID) {
                position = new Vector3d(index % side, (index / side) % side,
                        index / (side * side)).mul(spacing);
            } else {
                double z = random.nextDouble(-1, 1);
                double angle = random.nextDouble(0, Math.PI * 2);
                double radius = spacing * Math.cbrt(random.nextDouble());
                double radial = Math.sqrt(Math.max(0, 1 - z * z));
                position = new Vector3d(radial * Math.cos(angle), radial * Math.sin(angle), z)
                        .mul(radius);
            }
            nodes.add(new DiscoveryNode(original.id(), position, original.id() == 0));
            index++;
        }
        return new GeometryDiscoveryModel(topology.name(), topology.description(),
                nodes, topology.connections());
    }
}
