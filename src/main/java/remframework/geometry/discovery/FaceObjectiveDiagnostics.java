package remframework.geometry.discovery;

public record FaceObjectiveDiagnostics(double planarityMaximum,double planarityRms,
        double angleMaximum,double angleRms,double planarityContribution,
        double angleContribution,double dihedralContribution,
        double facePlaneRadiusContribution,double totalContribution) {
    public static FaceObjectiveDiagnostics inactive(){return new FaceObjectiveDiagnostics(0,0,0,0,0,0,0,0,0);}
}
