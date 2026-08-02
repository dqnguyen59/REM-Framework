package remframework.geometry.discovery.experiment;

/** Explicit dimensionless weights for normalized, active 3D constraint energies. */
public record ThreeDConstraintStrengths(double facePlanarity, double faceAngles,
        double dihedrals, double facePlaneRadii) {
    public ThreeDConstraintStrengths {
        facePlanarity=valid(facePlanarity,.12);faceAngles=valid(faceAngles,.08);
        dihedrals=valid(dihedrals,.06);facePlaneRadii=valid(facePlaneRadii,.08);
    }
    public static ThreeDConstraintStrengths defaults(){return new ThreeDConstraintStrengths(1.0,.6,.5,.6);}
    private static double valid(double value,double fallback){return Double.isFinite(value)&&value>=0?value:fallback;}
}
