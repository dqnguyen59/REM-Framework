package remframework.geometry.discovery.measurement.regularity;

import remframework.geometry.discovery.experiment.*;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ThreeDConditionEvaluator {
    public ThreeDConditionReport evaluate(boolean basicValid,ThreeDRegularityMetrics metrics,ThreeDExperimentConditions conditions){
        Map<String,ConditionConclusion> result=new LinkedHashMap<>();
        if(!metrics.applicable()){
            for(String name:new String[]{"facePlanarity","faceAngles","dihedrals","vertexRadii","faceCentroidRadii","edgeMidpointRadii","facePlaneRadii","normalCenterAlignment"})result.put(name,ConditionConclusion.NOT_APPLICABLE);
            return new ThreeDConditionReport(basicValid,false,conditions,result,metrics.applicabilityReason());
        }
        result.put("facePlanarity",conditions.facePlanarity()==FacePlanarityMode.MEASURE_ONLY?ConditionConclusion.MEASURE_ONLY:
                conclusion(metrics.facePlanes().stream().allMatch(FacePlaneDiagnostic::planar)));
        boolean faceAngles=metrics.faces().stream().allMatch(face->face.maximumInteriorTargetDeviation()<=conditions.absoluteRegularityTolerance());
        result.put("faceAngles",switch(conditions.faceAngles()){case NONE->ConditionConclusion.NOT_APPLICABLE;case MEASURE_ONLY->ConditionConclusion.MEASURE_ONLY;case REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE->conclusion(faceAngles);});
        result.put("dihedrals",switch(conditions.dihedrals()){case NONE->ConditionConclusion.NOT_APPLICABLE;case MEASURE_ONLY->ConditionConclusion.MEASURE_ONLY;case REQUIRE_EQUAL_DIHEDRAL_ANGLES->conclusion(equal(metrics.interiorDihedralAngles(),conditions.absoluteRegularityTolerance()));});
        result.put("vertexRadii",regularity(conditions.vertexRadii(),metrics.vertexRadii(),conditions.absoluteRegularityTolerance()));
        result.put("faceCentroidRadii",regularity(conditions.faceCentroidRadii(),metrics.faceCenterRadii(),conditions.absoluteRegularityTolerance()));
        result.put("edgeMidpointRadii",regularity(conditions.edgeMidpointRadii(),metrics.edgeMidpointRadii(),conditions.absoluteRegularityTolerance()));
        result.put("facePlaneRadii",switch(conditions.facePlaneRadii()){case NONE->ConditionConclusion.NOT_APPLICABLE;case MEASURE_ONLY->ConditionConclusion.MEASURE_ONLY;case REQUIRE_EQUAL_FACE_PLANE_RADII->conclusion(metrics.maximumFacePlaneRadiusDeviationFromMean()<=conditions.absoluteRegularityTolerance()||metrics.relativeMaximumFacePlaneRadiusDeviation()!=null&&metrics.relativeMaximumFacePlaneRadiusDeviation()<=conditions.relativeRegularityTolerance());});
        result.put("normalCenterAlignment",conditions.normalCenterAlignment()==RegularityMode.NONE?ConditionConclusion.NOT_APPLICABLE:conditions.normalCenterAlignment()==RegularityMode.MEASURE_ONLY?ConditionConclusion.MEASURE_ONLY:conclusion(metrics.maximumNormalAlignmentDeviationFromOne()<=conditions.absoluteRegularityTolerance()));
        boolean constrainedPassed=basicValid&&result.values().stream().noneMatch(value->value==ConditionConclusion.FAIL||value==ConditionConclusion.NOT_DETERMINABLE);
        return new ThreeDConditionReport(basicValid,constrainedPassed,conditions,result,
                constrainedPassed?"Basic validity and all explicitly required 3D conditions passed.":"Basic validity is reported separately; one or more selected constrained conditions failed.");
    }
    private static ConditionConclusion regularity(RegularityMode mode,Statistics values,double tolerance){return mode==RegularityMode.NONE?ConditionConclusion.NOT_APPLICABLE:mode==RegularityMode.MEASURE_ONLY?ConditionConclusion.MEASURE_ONLY:conclusion(equal(values,tolerance));}
    private static boolean equal(Statistics values,double tolerance){return !values.values().isEmpty()&&values.values().stream().allMatch(value->Math.abs(value-values.mean())<=tolerance);}
    private static ConditionConclusion conclusion(boolean pass){return pass?ConditionConclusion.PASS:ConditionConclusion.FAIL;}
}
