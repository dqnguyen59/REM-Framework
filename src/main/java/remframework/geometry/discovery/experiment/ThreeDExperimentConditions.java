package remframework.geometry.discovery.experiment;

public record ThreeDExperimentConditions(FacePlanarityMode facePlanarity,
        FaceAngleConditionMode faceAngles, DihedralConditionMode dihedrals,
        RegularityMode vertexRadii, RegularityMode faceCentroidRadii,
        RegularityMode edgeMidpointRadii, FacePlaneRadiusMode facePlaneRadii,
        RegularityMode normalCenterAlignment, GeometryCenterDefinition centerDefinition,
        double facePlanarityTolerance, double absoluteRegularityTolerance,
        double relativeRegularityTolerance) {
    public ThreeDExperimentConditions {
        facePlanarity=facePlanarity==null?FacePlanarityMode.REQUIRE_PLANAR_FACES:facePlanarity;
        faceAngles=faceAngles==null?FaceAngleConditionMode.MEASURE_ONLY:faceAngles;
        dihedrals=dihedrals==null?DihedralConditionMode.MEASURE_ONLY:dihedrals;
        vertexRadii=vertexRadii==null?RegularityMode.MEASURE_ONLY:vertexRadii;
        faceCentroidRadii=faceCentroidRadii==null?RegularityMode.MEASURE_ONLY:faceCentroidRadii;
        edgeMidpointRadii=edgeMidpointRadii==null?RegularityMode.MEASURE_ONLY:edgeMidpointRadii;
        facePlaneRadii=facePlaneRadii==null?FacePlaneRadiusMode.MEASURE_ONLY:facePlaneRadii;
        normalCenterAlignment=normalCenterAlignment==null?RegularityMode.MEASURE_ONLY:normalCenterAlignment;
        centerDefinition=centerDefinition==null?GeometryCenterDefinition.VERTEX_CENTROID:centerDefinition;
        facePlanarityTolerance=positive(facePlanarityTolerance,1e-7);
        absoluteRegularityTolerance=positive(absoluteRegularityTolerance,1e-6);
        relativeRegularityTolerance=positive(relativeRegularityTolerance,1e-6);
    }
    public static ThreeDExperimentConditions defaults(){return new ThreeDExperimentConditions(null,null,null,null,null,null,null,null,null,0,0,0);}
    private static double positive(double value,double fallback){return Double.isFinite(value)&&value>0?value:fallback;}
}
