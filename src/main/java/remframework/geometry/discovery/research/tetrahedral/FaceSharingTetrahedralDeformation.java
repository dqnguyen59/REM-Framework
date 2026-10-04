package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;

/** Reuses Geometry Discovery updates for a bounded soft-target deformation of the entire retained graph. */
public final class FaceSharingTetrahedralDeformation {
    private FaceSharingTetrahedralDeformation() { }
    public record Result(GeometryDiscoveryModel model, List<GeometryConfigurationStatus> statuses,
            boolean stationary,int iterations,double maximumRelativeEdgeError,
            FaceSharingTetrahedralRelaxation.Assessment assessment) {
        public Result { statuses=List.copyOf(statuses); }
    }
    public static Result run(GeometryDiscoveryModel input,FaceSharingTetrahedralExperiment.Snapshot topology,
            double target,int budget) {
        var settings=GeometryDiscoverySettings.defaults(target).withMaximumIterations(budget);
        var session=new GeometryDiscoverySession(FaceSharingTetrahedralRelaxation.solverInput(input),settings);
        if(!session.result().validation().isValid())
            return new Result(input.copy(),List.of(status(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                session.result().validation().messages().toString(),false)),false,0,Double.NaN,null);
        int stable=0,iterations=0;boolean stationary=false;
        session.start();
        while(!session.isFinished() && iterations<budget) {
            var before=session.model().sortedNodes().stream().map(n -> new Vector3d(n.position())).toList();
            session.step();iterations++;
            var after=session.model().sortedNodes();double movement=0;
            for(int id=0;id<before.size();id++) movement=Math.max(movement,before.get(id).distance(after.get(id).position()));
            stable=movement<=target*1e-7?stable+1:0;
            if(stable>=20) { stationary=true;session.pause();break; }
        }
        var result=session.result();stationary |= result.converged();
        var assessment=FaceSharingTetrahedralRelaxation.assess(result.model(),topology,
            settings.minimumNodeSeparation(),target*1e-9);
        double error=Math.max(Math.abs(result.metrics().minimumConnectedNeighborDistance()/target-1),
            Math.abs(result.metrics().maximumConnectedNeighborDistance()/target-1));
        String report=String.format(Locale.ROOT,
            "All %d node positions recalculated toward target %.6g: %d updates, settled=%s. "
            +"Connected lengths %.6g..%.6g; max target error %.4f%%; closest nodes %.6g; overlaps %d; degeneracies %d. "
            +"Layout settings: learning 0.08, repulsion 0.25, separation target 0.35*base, correction cap 0.2*base. "
            +"Settling uses exact solver convergence or movement <= base*1e-7 for 20 updates. Not REM mechanics.",
            topology.nodeCount(),target,iterations,stationary,result.metrics().minimumConnectedNeighborDistance(),
            result.metrics().maximumConnectedNeighborDistance(),100*error,assessment.minimumSeparation(),
            assessment.overlappingTetrahedronPairs(),assessment.degenerateTetrahedra());
        List<GeometryConfigurationStatus> statuses=new ArrayList<>();
        if(!result.metrics().allFinite()) statuses.add(status(GeometryConfigurationStatusCode.NUMERICAL_FAILURE,report,false));
        else statuses.add(status(stationary?GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN
            :GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,report,stationary));
        if(assessment.overlappingTetrahedronPairs()>0)
            statuses.add(status(GeometryConfigurationStatusCode.SELF_INTERSECTING,"Interior intersections remain after deformation.",false));
        if(assessment.degenerateTetrahedra()>0)
            statuses.add(status(GeometryConfigurationStatusCode.DEGENERATE,"Degenerate tetrahedra remain after deformation.",false));
        return new Result(result.model(),statuses,stationary,iterations,error,assessment);
    }
    private static GeometryConfigurationStatus status(GeometryConfigurationStatusCode code,String explanation,boolean proceed) {
        return GeometryConfigurationStatus.of(proceed
            ? GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Shared-node target layout",explanation)
            : GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Shared-node layout limit",explanation));
    }
}
