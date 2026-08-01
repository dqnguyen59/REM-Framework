package remframework.geometry.discovery.optimization;
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
import java.util.*;
import remframework.geometry.discovery.experiment.AngleConditionMode;
import remframework.geometry.discovery.measurement.regularity.TwoDRegularityCalculator;
/** Area-objective experiment; it imposes no angle or symmetry target. */
public final class AreaOptimizer {
    public GeometryOptimizationResult maximize2d(Configuration graph,Realization baseline,ClosedBoundaryResult boundary,double target,int iterations){if(!boundary.closed()||baseline.dimension()!=GeometryEmbeddingDimension.TWO_D)return GeometryOptimizationResult.notAttempted();List<Vector3d> p=baseline.finalCoordinates().stream().map(Vector3d::new).toList();List<Integer> order=boundary.orientedFaces().get(0).nodeIds();double base=area(order,p);double penalty=100;for(int iteration=0;iteration<iterations;iteration++){double step=.01/Math.sqrt(iteration+1);for(int id=2;id<p.size();id++)for(int axis=0;axis<2;axis++){double h=1e-6;Vector3d point=p.get(id);double old=axis==0?point.x:point.y;if(axis==0)point.x=old+h;else point.y=old+h;double plus=objective(graph,order,p,target,penalty);if(axis==0)point.x=old-h;else point.y=old-h;double minus=objective(graph,order,p,target,penalty);if(axis==0)point.x=old;else point.y=old;double gradient=(plus-minus)/(2*h);double update=Math.max(-.02,Math.min(.02,step*gradient));if(axis==0)point.x+=update;else point.y+=update;}}double optimizedArea=area(order,p);boolean edgesPreserved=graph.edges().stream().allMatch(e->Math.abs(p.get(e.firstNodeId()).distance(p.get(e.secondNodeId()))-target)<=.02*target);var d=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.OPTIMIZATION,GeometryConfigurationStatusCode.VALID_CANDIDATE,"Area optimization measured","Area was maximized without an angle or symmetry constraint; edge lengths remain a penalty constraint.");Realization optimized=new Realization(baseline.seed(),baseline.dimension(),baseline.initialCoordinates(),p,baseline.meanEdgeAccuracy(),0,true,baseline.collisionFree(),baseline.edgeIntersectionFree(),2,RealizationStatus.REALIZED,List.of(d));FaceDerivationResult rederived=new FaceDerivationPipeline().derive(graph,optimized);ClosedBoundaryResult revalidatedBoundary=new ClosedBoundaryValidator().validate(graph,optimized,rederived);boolean fullyRevalidated=edgesPreserved&&revalidatedBoundary.closed()&&!rederived.faces().isEmpty()&&new TwoDRegularityCalculator().calculate(graph,p,rederived.faces().get(0).nodeIds(),target,.02*target,1e-7,1e-9,1e-6,AngleConditionMode.MEASURE_ONLY).valid();GeometricSymmetryResult symmetry=new GeometricSymmetryAnalyzer().analyze(graph,optimized);String summary=fullyRevalidated?"BEST_AREA_FOUND_WITHIN_OPTIMIZATION_LIMITS; complete geometric qualification rerun passed.":"OPTIMIZED_GEOMETRY_REJECTED; complete geometric qualification rerun failed.";return new GeometryOptimizationResult(true,baseline,optimized,base,optimizedArea,(optimizedArea-base)/Math.max(base,1e-12),fullyRevalidated,GeometrySymmetryPolicy.NO_REQUIREMENT,symmetry.geometricallySymmetric(),iterations,summary);}
    private double objective(Configuration g,List<Integer> order,List<Vector3d> p,double target,double penalty){double error=0;for(DiscoveryConnection e:g.edges()){double d=p.get(e.firstNodeId()).distance(p.get(e.secondNodeId()))-target;error+=d*d;}return area(order,p)-penalty*error;}
    private double area(List<Integer> ids,List<Vector3d> p){double value=0;for(int i=0;i<ids.size();i++){Vector3d a=p.get(ids.get(i)),b=p.get(ids.get((i+1)%ids.size()));value+=a.x*b.y-b.x*a.y;}return Math.abs(value)/2;}
}
