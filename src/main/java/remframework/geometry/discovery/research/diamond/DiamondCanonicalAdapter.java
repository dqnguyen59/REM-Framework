package remframework.geometry.discovery.research.diamond;

import java.util.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
import remframework.opengl.model.entity.*;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.Settings.NodeFormulaVersion;

/** Direct Node v0.1 research adapter. Slot names are storage labels only; transport is not enabled. */
public final class DiamondCanonicalAdapter {
    public enum Normalization { EXISTING_SIX(6), RESEARCH_FOUR(4); final int divisor; Normalization(int divisor) {this.divisor=divisor;} }
    private final List<Node> nodes=new ArrayList<>();
    private final Node.FrameParameters parameters;
    private GeometryConfigurationStatus status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
        GeometryConfigurationStage.GEOMETRY_REALIZATION,GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,
        "Direct Node v0.1 adapter","Parameter mapping only; runtime propagation isotropy untested."));
    public GeometryConfigurationStatus status() { return status; }
    public DiamondCanonicalAdapter(GeometryDiscoveryModel geometry,double k,double gamma,Normalization normalization) {
        if(!Double.isFinite(k)||k<=0||k>1||!Double.isFinite(gamma)||gamma<=0) throw new IllegalArgumentException("Invalid mechanical parameters");
        Objects.requireNonNull(normalization);
        parameters=new Node.FrameParameters(NodeFormulaVersion.v_0_1,k*k/normalization.divisor,gamma,0,0,true);
        for(var n:geometry.sortedNodes()) {
            if(n.id()!=nodes.size() || n.neighborIds().isEmpty() || n.neighborIds().size()>4) throw new IllegalArgumentException("Dense diamond graph required");
            var node=new Node(null,null,n.position(),new Vector3d(),1);
            if(n.neighborIds().size()<4) node.setType(EntityType.FIXED);
            nodes.add(node);
        }
        for(var n:geometry.sortedNodes()) {int slot=0;for(int neighbor:n.neighborIds().stream().sorted().toList()) nodes.get(n.id()).setNeighbor(Neighbor.values()[slot++],nodes.get(neighbor));}
    }
    public void displace(int id,Vector3d displacement) {
        if(nodes.get(id).getType()==EntityType.FIXED || !displacement.isFinite()) throw new IllegalArgumentException("Finite interior displacement required");
        nodes.get(id).setPosition(new Vector3d(nodes.get(id).getPosition()).add(displacement));nodes.get(id).updatePosition();
    }
    /** Set initial research velocity; does not alter the Node update. */
    public void initializeVelocity(int id,Vector3d velocity) {
        if(nodes.get(id).getType()==EntityType.FIXED || !velocity.isFinite()) throw new IllegalArgumentException("Finite interior velocity required");
        nodes.get(id).getVelocity().set(velocity);
    }
    public Vector3d position(int id) {return new Vector3d(nodes.get(id).getPosition());}
    public Vector3d velocity(int id) {return new Vector3d(nodes.get(id).getVelocity());}
    public Vector3d storedAcceleration(int id) {return new Vector3d(nodes.get(id).getAcceleration());}
    public void step() {
        for(var node:nodes) node.updateNodeProperties(parameters);
        for(var node:nodes) node.updatePosition();
        if(nodes.stream().anyMatch(n->!n.getPosition().isFinite() || !n.getVelocity().isFinite())) {
            status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
                GeometryConfigurationStatusCode.NUMERICAL_FAILURE,"Direct Node v0.1 adapter","Nonfinite motion; research run stopped."));
            throw new IllegalStateException("NUMERICAL_FAILURE");
        }
    }
    public static void main(String[] args) {
        var geometry=DiamondReference.create(3,10);
        int source=geometry.nodes().stream().filter(n->n.neighborIds().size()==4).findFirst().orElseThrow().id();
        var delta=new Vector3d(.001,0,0);
        for(var mode:Normalization.values()) {
            var adapter=new DiamondCanonicalAdapter(geometry,.5,1,mode);adapter.displace(source,delta);var before=adapter.position(source);adapter.step();
            System.out.printf(Locale.ROOT,"%s: velocity change x=%.9g; position change x=%.9g; stored acceleration x=%.9g%n",mode,adapter.velocity(source).x,adapter.position(source).x-before.x,adapter.storedAcceleration(source).x);
        }
        var prototype=new DiamondMechanics(geometry,.5,1);prototype.displace(source,delta);var before=prototype.position(source);prototype.step();
        System.out.printf(Locale.ROOT,"Velocity Verlet dt1: position change x=%.9g%n",prototype.position(source).x-before.x);
        System.out.println("Parameter mapping verified; integrators differ. Canonical cubic code unchanged. No runtime isotropy claim.");
    }
}

