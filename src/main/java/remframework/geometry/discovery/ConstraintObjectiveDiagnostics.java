package remframework.geometry.discovery;

public record ConstraintObjectiveDiagnostics(double edgeMaximum,double edgeRms,
        double planarityMaximum,double planarityRms,double angleMaximum,double angleRms,
        double edgeContribution,double planarityContribution,double angleContribution,
        double dihedralContribution,double facePlaneRadiusContribution,double totalObjective,
        double maximumVelocity,double kineticMeasure) { }
