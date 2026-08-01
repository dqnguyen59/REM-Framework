package remframework.geometry.discovery.optimization;

import org.joml.Vector3d;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.Configuration;
import remframework.geometry.discovery.realization.session.Realization;
import remframework.geometry.discovery.realization.session.RealizationStatus;

import java.util.List;

/** Bounded volume objective with edge penalties and mandatory post-search requalification. */
public final class VolumeOptimizer {
    public GeometryOptimizationResult maximize3d(Configuration graph, Realization baseline,
            ClosedBoundaryResult boundary, double target, int iterations) {
        if (!boundary.closed() || baseline.dimension() != GeometryEmbeddingDimension.THREE_D)
            return GeometryOptimizationResult.notAttempted();
        List<Vector3d> points = baseline.finalCoordinates().stream().map(Vector3d::new).toList();
        double baselineVolume = volume(boundary, points), penalty = 100;
        for (int iteration=0; iteration<iterations; iteration++) {
            double step=.005/Math.sqrt(iteration+1);
            for(int id=2;id<points.size();id++) for(int axis=0;axis<3;axis++) {
                Vector3d p=points.get(id); double old=component(p,axis),h=1e-6;
                component(p,axis,old+h); double plus=objective(graph,boundary,points,target,penalty);
                component(p,axis,old-h); double minus=objective(graph,boundary,points,target,penalty);
                component(p,axis,old); component(p,axis,old+Math.max(-.01,Math.min(.01,step*(plus-minus)/(2*h))));
            }
        }
        var diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.OPTIMIZATION,
                GeometryConfigurationStatusCode.VALID_CANDIDATE,"Volume optimization measured",
                "Volume was optimized with connected-edge penalties and no regularity force.");
        Realization optimized=new Realization(baseline.seed(),baseline.dimension(),baseline.initialCoordinates(),
                points,baseline.meanEdgeAccuracy(),0,true,baseline.collisionFree(),baseline.edgeIntersectionFree(),3,
                RealizationStatus.REALIZED,List.of(diagnostic));
        FaceDerivationResult faces=new FaceDerivationPipeline().derive(graph,optimized);
        ClosedBoundaryResult qualified=new ClosedBoundaryValidator().validate(graph,optimized,faces);
        VolumeResult measured=new GeometryMeasureCalculator().volume(optimized,qualified);
        boolean edges=graph.edges().stream().allMatch(e->Math.abs(points.get(e.firstNodeId()).distance(points.get(e.secondNodeId()))-target)<=.02*target);
        boolean accepted=edges&&qualified.closed()&&measured.available()&&measured.absoluteVolume()>1e-9;
        double optimizedVolume=volume(boundary,points);
        GeometricSymmetryResult symmetry=new GeometricSymmetryAnalyzer().analyze(graph,optimized);
        return new GeometryOptimizationResult(true,baseline,optimized,baselineVolume,optimizedVolume,
                (optimizedVolume-baselineVolume)/Math.max(baselineVolume,1e-12),accepted,
                GeometrySymmetryPolicy.NO_REQUIREMENT,symmetry.geometricallySymmetric(),iterations,
                accepted?"BEST_VOLUME_FOUND_WITHIN_OPTIMIZATION_LIMITS; complete geometric qualification rerun passed."
                        :"OPTIMIZED_GEOMETRY_REJECTED; complete geometric qualification rerun failed.");
    }
    private static double objective(Configuration graph,ClosedBoundaryResult boundary,List<Vector3d> p,double target,double penalty){double error=0;for(DiscoveryConnection e:graph.edges()){double d=p.get(e.firstNodeId()).distance(p.get(e.secondNodeId()))-target;error+=d*d;}return volume(boundary,p)-penalty*error;}
    private static double volume(ClosedBoundaryResult boundary,List<Vector3d> p){Vector3d center=new Vector3d();p.forEach(center::add);center.div(p.size());double total=0;for(DerivedFace face:boundary.orientedFaces()){List<Integer> ids=new java.util.ArrayList<>(face.nodeIds());Vector3d a=p.get(ids.get(0)),b=p.get(ids.get(1)),c=p.get(ids.get(2));Vector3d normal=new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a));Vector3d fc=new Vector3d();ids.forEach(i->fc.add(p.get(i)));fc.div(ids.size());if(normal.dot(new Vector3d(fc).sub(center))<0)java.util.Collections.reverse(ids);Vector3d origin=p.get(ids.get(0));for(int i=1;i+1<ids.size();i++)total+=origin.dot(new Vector3d(p.get(ids.get(i))).cross(p.get(ids.get(i+1))))/6;}return Math.abs(total);}
    private static double component(Vector3d p,int axis){return axis==0?p.x:axis==1?p.y:p.z;}
    private static void component(Vector3d p,int axis,double value){if(axis==0)p.x=value;else if(axis==1)p.y=value;else p.z=value;}
}
