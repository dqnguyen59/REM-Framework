package remframework.geometry.discovery.realization.session;

import java.util.ArrayList;
import java.util.List;
import remframework.geometry.discovery.ThreeDConstraintDiagnostics;
import remframework.geometry.discovery.ClosedBoundaryResult;
import remframework.geometry.discovery.VolumeResult;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.measurement.regularity.Statistics;
import remframework.geometry.discovery.measurement.regularity.ThreeDRegularityMetrics;

/** Builds immutable diagnostics exclusively from a published scientific snapshot. */
public final class Stage2ConstraintPresentation {
    private Stage2ConstraintPresentation(){}
    public static ConstraintPresentationSummary from(IncrementalSession session){
        TestRequest r=session.request();ThreeDExperimentConditions c=r.threeDConditions();
        ThreeDConstraintStrengths s=r.threeDConstraintStrengths();
        ThreeDConstraintDiagnostics d=session.threeDConstraintDiagnostics();
        TestResult result=session.result();
        ThreeDRegularityMetrics finalMetrics=result==null?null:result.threeDRegularity();
        List<ConstraintPresentation> rows=new ArrayList<>();
        rows.add(continuous("edge-equality","Connected-edge equality",ConstraintPresentationMode.REQUIRED_ACTIVE_TARGET,
                session.maximumEdgeError(),r.edgeTolerance(),"length","length",
                r.relaxationSettings().springStrength(),"Maximum absolute deviation from target connected-edge length."));
        rows.add(continuousMode("face-planarity","Face planarity",c.facePlanarity()==FacePlanarityMode.MEASURE_ONLY,
                d.maximumFacePlanarityError(),c.facePlanarityTolerance(),"length","maximum vertex-to-face-plane deviation",s.facePlanarity()));
        rows.add(continuousMode("face-angle-equality","Face-angle equality",c.faceAngles(),
                d.maximumFaceAngleDeviation(),c.absoluteRegularityTolerance(),"radians","maximum absolute deviation from topology-derived target angle",s.faceAngles()));
        rows.add(continuousMode("dihedral-equality","Dihedral-angle equality",c.dihedrals(),
                d.maximumDihedralDeviation(),c.absoluteRegularityTolerance(),"radians","maximum absolute deviation from common interior dihedral angle",s.dihedrals()));
        rows.add(postMode("vertex-radius-equality","Vertex-radius equality",c.vertexRadii(),deviation(finalMetrics==null?null:finalMetrics.vertexRadii()),c.absoluteRegularityTolerance(),"length","maximum absolute deviation from mean vertex radius"));
        rows.add(postMode("face-centroid-radius-equality","Face-centroid-radius equality",c.faceCentroidRadii(),deviation(finalMetrics==null?null:finalMetrics.faceCenterRadii()),c.absoluteRegularityTolerance(),"length","maximum absolute deviation from mean face-centroid radius"));
        rows.add(postMode("edge-midpoint-radius-equality","Edge-midpoint-radius equality",c.edgeMidpointRadii(),deviation(finalMetrics==null?null:finalMetrics.edgeMidpointRadii()),c.absoluteRegularityTolerance(),"length","maximum absolute deviation from mean edge-midpoint radius"));
        rows.add(continuousMode("face-plane-radius-equality","Face-plane-radius equality",c.facePlaneRadii(),
                d.maximumFacePlaneRadiusDeviation(),c.absoluteRegularityTolerance(),"length","maximum absolute deviation from mean face-plane radius",s.facePlaneRadii()));
        rows.add(postMode("normal-center-alignment","Normal-center alignment",c.normalCenterAlignment(),finalMetrics==null?Double.NaN:finalMetrics.maximumNormalAlignmentDeviationFromOne(),c.absoluteRegularityTolerance(),"dimensionless","maximum |1 - normal/center alignment|"));
        rows.add(measured("equal-face-area","Equal face area",finalMetrics==null?Double.NaN:faceAreaDeviation(finalMetrics),"area","maximum absolute deviation from the mean face area"));
        ClosedBoundaryResult boundary=result==null?null:result.boundary();VolumeResult volume=result==null?null:result.volume();
        rows.add(discrete("closed-manifold","Closed-manifold boundary",true,boundary==null?ConstraintPresentationStatus.UNDETERMINED:boundary.closed()&&boundary.manifold()&&boundary.connected()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"Requires a connected, closed manifold geometric boundary."));
        rows.add(discrete("coherent-orientation","Coherent orientation",true,boundary==null?ConstraintPresentationStatus.UNDETERMINED:boundary.orientable()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"Requires coherent orientation of the final boundary."));
        rows.add(discrete("self-intersection","Self-intersection",true,boundary==null?ConstraintPresentationStatus.UNDETERMINED:!boundary.selfIntersecting()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"PASS means the final intersection count is zero."));
        rows.add(discrete("positive-volume","Positive volume",true,volume==null||!volume.available()?ConstraintPresentationStatus.UNDETERMINED:volume.absoluteVolume()>r.degeneracyTolerance()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"Measured absolute volume must exceed degeneracy threshold "+r.degeneracyTolerance()+"."));
        return ConstraintPresentationSummary.of(rows);
    }
    private static ConstraintPresentation continuousMode(String id,String name,Object mode,double error,double tolerance,String units,String metric,double strength){
        String text=String.valueOf(mode);boolean none=text.equals("NONE");boolean measured=text.equals("MEASURE_ONLY");
        if(none)return absent(id,name,metric);
        if(measured)return measured(id,name,error,units,metric);
        return continuous(id,name,ConstraintPresentationMode.REQUIRED_ACTIVE_TARGET,error,tolerance,units,units,strength,metric);
    }
    private static ConstraintPresentation postMode(String id,String name,RegularityMode mode,double error,double tolerance,String units,String metric){
        if(mode==RegularityMode.NONE)return absent(id,name,metric);
        if(mode==RegularityMode.MEASURE_ONLY)return measured(id,name,error,units,metric);
        return continuous(id,name,ConstraintPresentationMode.REQUIRED_POST_QUALIFICATION,error,tolerance,units,units,null,metric+"; this requirement has no corrective solver force.");
    }
    private static ConstraintPresentation continuous(String id,String name,ConstraintPresentationMode mode,double error,double tolerance,String errorUnits,String toleranceUnits,Double strength,String metric){
        NormalizedErrorRatio ratio=NormalizedErrorRatio.evaluate(error,tolerance,false);
        return new ConstraintPresentation(id,name,mode,true,true,mode==ConstraintPresentationMode.REQUIRED_ACTIVE_TARGET,error,errorUnits,metric,tolerance,toleranceUnits,ratio,strength,ratio.status(),metric+" "+ratio.explanation());
    }
    private static ConstraintPresentation measured(String id,String name,double error,String units,String metric){
        return new ConstraintPresentation(id,name,ConstraintPresentationMode.MEASURE_ONLY,true,false,false,error,units,metric,Double.NaN,"not used",null,null,ConstraintPresentationStatus.MEASURED,metric+"; measured only and contributes no corrective force.");
    }
    private static ConstraintPresentation absent(String id,String name,String metric){
        return new ConstraintPresentation(id,name,ConstraintPresentationMode.NONE,false,false,false,Double.NaN,"",metric,Double.NaN,"",null,null,ConstraintPresentationStatus.NOT_APPLICABLE,metric+"; mode NONE excludes it from solver and qualification.");
    }
    private static ConstraintPresentation discrete(String id,String name,boolean required,ConstraintPresentationStatus status,String explanation){
        return new ConstraintPresentation(id,name,required?ConstraintPresentationMode.REQUIRED_POST_QUALIFICATION:ConstraintPresentationMode.MEASURE_ONLY,true,required,false,Double.NaN,"",name,Double.NaN,"",null,null,status,explanation);
    }
    private static double deviation(Statistics values){
        if(values==null||values.values().isEmpty()||!Double.isFinite(values.mean()))return Double.NaN;
        return values.values().stream().mapToDouble(value->Math.abs(value-values.mean())).max().orElse(Double.NaN);
    }
    private static double faceAreaDeviation(ThreeDRegularityMetrics metrics){
        if(metrics.faces().isEmpty())return Double.NaN;
        double mean=metrics.faces().stream().mapToDouble(face->face.area()).average().orElse(Double.NaN);
        return metrics.faces().stream().mapToDouble(face->Math.abs(face.area()-mean)).max().orElse(Double.NaN);
    }
}
