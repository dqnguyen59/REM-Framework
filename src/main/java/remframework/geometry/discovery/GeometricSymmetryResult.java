package remframework.geometry.discovery;
public record GeometricSymmetryResult(boolean complete, boolean geometricallySymmetric,
        double centroidRadiusVariance,double edgeLengthVariance,String summary,
        GeometryConfigurationStatus status) { }
