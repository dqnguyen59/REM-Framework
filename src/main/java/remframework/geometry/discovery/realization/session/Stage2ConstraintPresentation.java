package remframework.geometry.discovery.realization.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import remframework.geometry.discovery.ClosedBoundaryResult;
import remframework.geometry.discovery.VolumeResult;
import remframework.geometry.discovery.experiment.*;

/** Builds immutable presentation rows from one published Stage 2 measurement snapshot. */
public final class Stage2ConstraintPresentation {
    private Stage2ConstraintPresentation() { }

    public static ConstraintPresentationSummary from(IncrementalSession session) {
        TestRequest r=session.request(); ThreeDExperimentConditions c=r.threeDConditions();
        Map<String,ConstraintMeasurement> m=session.constraintMeasurements(); List<ConstraintPresentation> rows=new ArrayList<>();
        rows.add(row("edge-equality",ConstraintPresentationMode.REQUIRED_ACTIVE_TARGET,true,true,m.get("edge-equality"),r.edgeTolerance(),"length","Maximum absolute deviation from target connected-edge length",r.relaxationSettings().springStrength()));
        rows.add(row("outward-face-orientation",ConstraintPresentationMode.MEASURE_ONLY,true,false,m.get("outward-face-orientation"),null,"","Coherent orientation of the ordered face cycles",null));
        rows.add(modeRow("face-planarity",c.facePlanarity(),m.get("face-planarity"),c.facePlanarityTolerance(),"length","Maximum vertex-to-face-plane deviation",r.threeDConstraintStrengths().facePlanarity()));
        rows.add(modeRow("face-angle-equality",c.faceAngles(),m.get("face-angle-equality"),c.absoluteRegularityTolerance(),"radians","Maximum absolute deviation from the topology-derived target angle",r.threeDConstraintStrengths().faceAngles()));
        rows.add(modeRow("dihedral-equality",c.dihedrals(),m.get("dihedral-equality"),c.absoluteRegularityTolerance(),"radians","Maximum absolute deviation from the mean interior dihedral angle",r.threeDConstraintStrengths().dihedrals()));
        rows.add(regularityRow("vertex-radius-equality",c.vertexRadii(),m.get("vertex-radius-equality"),c.absoluteRegularityTolerance(),"length","Maximum absolute deviation from the mean vertex distance"));
        rows.add(regularityRow("face-centroid-radius-equality",c.faceCentroidRadii(),m.get("face-centroid-radius-equality"),c.absoluteRegularityTolerance(),"length","Center-to-face-centroid distance; maximum deviation from its mean"));
        rows.add(regularityRow("edge-midpoint-radius-equality",c.edgeMidpointRadii(),m.get("edge-midpoint-radius-equality"),c.absoluteRegularityTolerance(),"length","Center-to-edge-midpoint distance; maximum deviation from its mean"));
        rows.add(modeRow("face-plane-radius-equality",c.facePlaneRadii(),m.get("face-plane-radius-equality"),c.absoluteRegularityTolerance(),"length","Center-to-face-plane distance",r.threeDConstraintStrengths().facePlaneRadii()));
        rows.add(regularityRow("normal-center-alignment",c.normalCenterAlignment(),m.get("normal-center-alignment"),c.absoluteRegularityTolerance(),"dimensionless","Maximum absolute value of 1 minus normal/radial alignment"));
        rows.add(row("equal-face-area",ConstraintPresentationMode.MEASURE_ONLY,true,false,m.get("equal-face-area"),null,"area","Maximum absolute deviation from the mean face area",null));
        ClosedBoundaryResult boundary=session.result()==null?null:session.result().boundary(); VolumeResult volume=session.result()==null?null:session.result().volume();
        rows.add(discrete("closed-manifold",true,boundary==null?ConstraintPresentationStatus.UNDETERMINED:boundary.closed()&&boundary.manifold()&&boundary.connected()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"Requires a connected, closed manifold geometric boundary."));
        rows.add(discrete("coherent-orientation",true,boundary==null?ConstraintPresentationStatus.UNDETERMINED:boundary.orientable()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"Requires coherent orientation of the final boundary."));
        rows.add(discrete("self-intersection",true,boundary==null?ConstraintPresentationStatus.UNDETERMINED:!boundary.selfIntersecting()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"PASS means the final intersection count is zero."));
        rows.add(discrete("positive-volume",true,volume==null||!volume.available()?ConstraintPresentationStatus.UNDETERMINED:volume.absoluteVolume()>r.degeneracyTolerance()?ConstraintPresentationStatus.PASS:ConstraintPresentationStatus.FAIL,"Measured absolute volume must exceed the degeneracy threshold."));
        return ConstraintPresentationSummary.of(rows);
    }

    private static ConstraintPresentation modeRow(String id,Object mode,ConstraintMeasurement measurement,double tolerance,String unit,String formula,double strength) {
        boolean none=String.valueOf(mode).equals("NONE"); boolean measured=String.valueOf(mode).equals("MEASURE_ONLY");
        return row(id,none?ConstraintPresentationMode.NONE:measured?ConstraintPresentationMode.MEASURE_ONLY:ConstraintPresentationMode.REQUIRED_ACTIVE_TARGET,
                !none,!none&&!measured,measurement,tolerance,unit,formula,measured?null:strength);
    }
    private static ConstraintPresentation regularityRow(String id,RegularityMode mode,ConstraintMeasurement measurement,double tolerance,String unit,String formula) {
        boolean none=mode==RegularityMode.NONE, measured=mode==RegularityMode.MEASURE_ONLY;
        return row(id,none?ConstraintPresentationMode.NONE:measured?ConstraintPresentationMode.MEASURE_ONLY:ConstraintPresentationMode.REQUIRED_POST_QUALIFICATION,
                !none,!none&&!measured,measurement,tolerance,unit,formula,null);
    }
    private static ConstraintPresentation row(String id,ConstraintPresentationMode mode,boolean applicable,boolean required,
            ConstraintMeasurement source,Double tolerance,String unit,String formula,Double strength) {
        ConstraintMeasurement measurement=source==null?ConstraintMeasurement.unavailable(id,MeasurementState.NUMERICALLY_INVALID,
                "MISSING_MEASUREMENT","The immutable measurement snapshot did not contain this constraint.",unit,0,0,List.of(),0):source;
        if(!applicable) measurement=ConstraintMeasurement.unavailable(id,MeasurementState.NOT_REQUESTED,"NOT_REQUESTED",
                "This measurement is not requested by the active profile.",unit,0,0,List.of(),measurement.coordinateRevision());
        if(tolerance!=null) measurement=measurement.withTolerance(tolerance);
        double raw=measurement.rawValue()==null?Double.NaN:measurement.rawValue();double tol=measurement.tolerance()==null?Double.NaN:measurement.tolerance();
        NormalizedErrorRatio ratio=measurement.rawValue()!=null&&tolerance!=null?NormalizedErrorRatio.evaluate(raw,tolerance,false):null;
        ConstraintPresentationStatus status=status(measurement,ratio);
        boolean continuous=measurement.rawValue()!=null&&ratio!=null;
        return new ConstraintPresentation(id,id,mode,applicable,required,mode==ConstraintPresentationMode.REQUIRED_ACTIVE_TARGET,
                raw,unit,formula,tol,unit,continuous?ratio:null,strength,status,measurement.reasonText(),measurement);
    }
    private static ConstraintPresentation discrete(String id,boolean required,ConstraintPresentationStatus status,String explanation) {
        ConstraintMeasurement m=new ConstraintMeasurement(id,MeasurementState.MEASURED,null,null,null,"","DISCRETE",explanation,List.of(),1,0,0,status==ConstraintPresentationStatus.PASS?"PASS":"FAIL");
        return new ConstraintPresentation(id,id,ConstraintPresentationMode.REQUIRED_POST_QUALIFICATION,true,required,false,Double.NaN,"",id,Double.NaN,"",null,null,status,explanation,m);
    }
    private static ConstraintPresentationStatus status(ConstraintMeasurement m,NormalizedErrorRatio ratio) {
        if(m.state()==MeasurementState.NOT_REQUESTED)return ConstraintPresentationStatus.NOT_APPLICABLE;
        if(m.state()==MeasurementState.NUMERICALLY_INVALID||m.state()==MeasurementState.NUMERICALLY_INVALID_LEGACY_VALUE)return ConstraintPresentationStatus.NUMERICALLY_INVALID;
        if(m.qualitativeState().equals("COHERENT_OUTWARD"))return ConstraintPresentationStatus.PASS;
        if(m.qualitativeState().equals("COHERENT_INWARD")||m.qualitativeState().equals("INCOHERENT"))return ConstraintPresentationStatus.FAIL;
        if(ratio!=null)return ratio.status();
        if(m.state()==MeasurementState.MEASURED||m.state()==MeasurementState.PARTIAL_MEASUREMENT)return ConstraintPresentationStatus.MEASURED;
        return ConstraintPresentationStatus.UNDETERMINED;
    }
}
