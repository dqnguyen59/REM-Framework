package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;

/**
 * Opt-in soft-separation experiment using the existing Geometry Discovery solver.
 * <br><br>
 * Separation, edge targets and correction limits are layout search parameters, not REM forces.
 */
public final class FaceSharingTetrahedralRelaxation {
    private FaceSharingTetrahedralRelaxation() { }
    public record Assessment(double minimumSeparation, int separationViolations,
            int overlappingTetrahedronPairs, int degenerateTetrahedra) { }
    public record Trial(FaceSharingTetrahedralPreview.Preview baseline,
            GeometryDiscoveryResult result, Assessment before, Assessment after,
            List<GeometryConfigurationStatus> statuses) {
        public Trial { statuses=List.copyOf(statuses); }
    }

    public static Trial run(FaceSharingTetrahedralPreview.Preview preview,
            GeometryDiscoverySettings settings) {
        if(preview.model()==null || preview.topology()==null)
            throw new IllegalArgumentException("A retained preview topology is required.");
        var snapshot=preview.topology().generations().getLast();
        GeometryDiscoveryModel input=solverInput(preview.model());
        var before=assess(input,snapshot,settings.minimumNodeSeparation(),settings.targetDistance()*1e-9);
        var result=new GeometryDiscoveryEngine().relax(input,settings);
        var after=assess(result.model(),snapshot,settings.minimumNodeSeparation(),settings.targetDistance()*1e-9);
        List<GeometryConfigurationStatus> statuses=new ArrayList<>(List.of(preview.topology().status()));
        if(!result.validation().isValid())
            statuses.add(status(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                "Existing solver input validation failed: "+result.validation().messages(),false));
        else if(!result.metrics().allFinite())
            statuses.add(status(GeometryConfigurationStatusCode.NUMERICAL_FAILURE,"Non-finite layout result retained.",false));
        else if(!result.converged())
            statuses.add(status(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,
                "Soft-separation trial exhausted "+result.metrics().iterationCount()+" iterations; target edge="
                +settings.targetDistance()+", separation="+settings.minimumNodeSeparation()
                +", learning rate="+settings.learningRate()+", repulsion="+settings.repulsionStrength()
                +", correction cap="+settings.maximumCorrection()+". No convergence claimed.",false));
        else statuses.add(status(GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,
                "Existing solver converged on its local targets; isotropy and full realization remain unqualified.",true));
        String comparison=String.format(Locale.ROOT,
            "Minimum separation %.6f -> %.6f; pairs below %.6f: %d -> %d; overlapping tetrahedron pairs %d -> %d. "
            +"Edge lengths after trial %.6f..%.6f. Nodes and connections retained; this is not REM mechanics.",
            before.minimumSeparation(),after.minimumSeparation(),settings.minimumNodeSeparation(),
            before.separationViolations(),after.separationViolations(),
            before.overlappingTetrahedronPairs(),after.overlappingTetrahedronPairs(),
            result.metrics().minimumConnectedNeighborDistance(),result.metrics().maximumConnectedNeighborDistance());
        statuses.add(status(after.overlappingTetrahedronPairs()>0
            ? GeometryConfigurationStatusCode.SELF_INTERSECTING
            : GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED,comparison,after.overlappingTetrahedronPairs()==0));
        if(after.degenerateTetrahedra()>0)
            statuses.add(status(GeometryConfigurationStatusCode.DEGENERATE,
                after.degenerateTetrahedra()+" tetrahedra have near-zero volume in this trial.",false));
        var displayedModel=new GeometryDiscoveryModel(result.model().name(),
            "Soft-separation layout trial. "+comparison,result.model().nodes(),result.model().connections());
        var displayedResult=new GeometryDiscoveryResult(displayedModel,result.settings(),result.metrics(),
            result.validation(),result.runtimeCompatibility(),result.diagnostics());
        return new Trial(preview,displayedResult,before,after,statuses);
    }

    /** One rigid translation/rotation, then anchor 0; no topology or initial distance changes. */
    public static GeometryDiscoveryModel solverInput(GeometryDiscoveryModel model) {
        var nodes=model.sortedNodes();
        Vector3d origin=new Vector3d(nodes.get(0).position());
        Vector3d direction=new Vector3d(nodes.get(1).position()).sub(origin).normalize();
        Quaterniond rotation=new Quaterniond().rotationTo(direction,new Vector3d(1,0,0));
        List<DiscoveryNode> transformed=new ArrayList<>();
        for(var node:nodes)
            transformed.add(new DiscoveryNode(node.id(),new Vector3d(node.position()).sub(origin).rotate(rotation),node.id()==0));
        return new GeometryDiscoveryModel(model.name(),"Rigidly aligned display layout. "+model.description(),transformed,model.connections());
    }

    static Assessment assess(GeometryDiscoveryModel model,
            FaceSharingTetrahedralExperiment.Snapshot snapshot,double separation,double tolerance) {
        var positions=model.sortedNodes().stream().map(DiscoveryNode::position).toList();
        int violations=0,overlaps=0,degenerates=0;double min=Double.POSITIVE_INFINITY;
        for(int a=0;a<positions.size();a++) for(int b=a+1;b<positions.size();b++) {
            double distance=positions.get(a).distance(positions.get(b));min=Math.min(min,distance);
            if(distance<separation) violations++;
        }
        var tetrahedra=snapshot.tetrahedra();
        for(var t:tetrahedra) {
            var origin=positions.get(t.a());
            double volume6=Math.abs(new Vector3d(positions.get(t.b())).sub(origin)
                .cross(new Vector3d(positions.get(t.c())).sub(origin))
                .dot(new Vector3d(positions.get(t.d())).sub(origin)));
            double scale=positions.get(t.b()).distance(origin);
            if(volume6<=tolerance*Math.max(scale*scale,tolerance*tolerance)) degenerates++;
        }
        for(int a=0;a<tetrahedra.size();a++) for(int b=a+1;b<tetrahedra.size();b++)
            if(FaceSharingTetrahedralPreview.interiorsOverlap(tetrahedra.get(a),tetrahedra.get(b),positions,tolerance)) overlaps++;
        return new Assessment(min,violations,overlaps,degenerates);
    }
    private static GeometryConfigurationStatus status(GeometryConfigurationStatusCode code,String explanation,boolean proceed) {
        return GeometryConfigurationStatus.of(proceed
            ? GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Soft-separation layout trial",explanation)
            : GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Soft-separation layout trial failed qualification",explanation));
    }
    public static void main(String[] args) {
        int generation=args.length>0?Integer.parseInt(args[0]):3;
        int iterations=args.length>1?Integer.parseInt(args[1]):2000;
        var preview=FaceSharingTetrahedralPreview.create(generation,1000,10);
        var trial=run(preview,GeometryDiscoverySettings.defaults(10).withMaximumIterations(iterations));
        for(var status:trial.statuses()) System.out.println(status.code()+": "+status.explanation());
    }
}
