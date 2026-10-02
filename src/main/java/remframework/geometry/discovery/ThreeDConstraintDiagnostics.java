package remframework.geometry.discovery;

public record ThreeDConstraintDiagnostics(double maximumFacePlanarityError,
        double maximumFaceAngleDeviation, double maximumDihedralDeviation,
        double dihedralVariance, double maximumFacePlaneRadiusDeviation,
        double facePlaneRadiusVariance,double enclosedVolumeEstimate,boolean manifold,
        boolean finite) {
    public static ThreeDConstraintDiagnostics inactive(){return new ThreeDConstraintDiagnostics(0,0,0,0,0,0,0,false,true);}
}
