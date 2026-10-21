package remframework.geometry.discovery.research.diamond;

import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;

/** Isolated stated-equation experiment. Velocity Verlet is explicit, not claimed canonical-runtime parity. */
public final class DiamondMechanics {
    private final List<DynamicElasticNode> nodes=new ArrayList<>();
    private final List<List<Integer>> adjacency=new ArrayList<>();
    private final List<Vector3d> reference=new ArrayList<>();
    private final double k,dt;
    public DiamondMechanics(GeometryDiscoveryModel geometry,double k,double dt) {
        if(!Double.isFinite(k)||k<=0||k>1||!Double.isFinite(dt)||dt<=0||dt>1) throw new IllegalArgumentException("k and dt in (0,1]");
        this.k=k;this.dt=dt;
        for(var n:geometry.sortedNodes()) {
            if(n.id()!=nodes.size() || n.neighborIds().isEmpty() || n.neighborIds().size()>4)
                throw new IllegalArgumentException("Dense node IDs and diamond degree 1..4 required");
            adjacency.add(n.neighborIds().stream().sorted().toList());reference.add(new Vector3d(n.position()));
            nodes.add(new DynamicElasticNode(n.id(),n.position(),new Vector3d(),n.neighborIds().size()!=4));
        }
    }
    private GeometryConfigurationStatus status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
        GeometryConfigurationStage.GRAPH_GENERATION,GeometryConfigurationStatusCode.GENERATED,
        "Diamond equation experiment","Stated equation with velocity Verlet; canonical runtime parity and propagation isotropy not established."));
    public GeometryConfigurationStatus status() { return status; }
    public int nodeCount() { return nodes.size(); }
    public boolean fixed(int id) { return nodes.get(id).fixed(); }
    public Vector3d position(int id) { return new Vector3d(nodes.get(id).position()); }
    public Vector3d velocity(int id) { return new Vector3d(nodes.get(id).velocity()); }
    public void displace(int id,Vector3d delta) {
        if(nodes.get(id).fixed() || !delta.isFinite()) throw new IllegalArgumentException("Finite displacement of an interior node required");
        nodes.get(id).position().add(delta);
    }
    public Vector3d acceleration(int id) {
        var neighbors=adjacency.get(id);if(neighbors.isEmpty()) throw new IllegalStateException("Isolated node");
        var sum=new Vector3d();var p=nodes.get(id).position();
        for(int neighbor:neighbors) sum.add(new Vector3d(nodes.get(neighbor).position()).sub(p));
        return sum.mul(k*k/neighbors.size());
    }
    public void step() {
        // Synchronous velocity Verlet: all accelerations sampled before each global position/velocity pass.
        var a=new ArrayList<Vector3d>();for(int id=0;id<nodes.size();id++) a.add(acceleration(id));
        for(int id=0;id<nodes.size();id++) {var n=nodes.get(id);if(!n.fixed()) {
            n.position().add(new Vector3d(n.velocity()).mul(dt)).add(new Vector3d(a.get(id)).mul(.5*dt*dt));
            n.velocity().add(new Vector3d(a.get(id)).mul(.5*dt));
        }}
        var next=new ArrayList<Vector3d>();for(int id=0;id<nodes.size();id++) next.add(acceleration(id));
        for(int id=0;id<nodes.size();id++) {var n=nodes.get(id);if(!n.fixed()) n.velocity().add(new Vector3d(next.get(id)).mul(.5*dt));
            if(!n.position().isFinite()||!n.velocity().isFinite()) {
                status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
                    GeometryConfigurationStatusCode.NUMERICAL_FAILURE,"Diamond equation experiment","Nonfinite motion; experiment stopped."));
                throw new IllegalStateException("NUMERICAL_FAILURE");
            }
        }
    }
}
