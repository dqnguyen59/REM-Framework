package remframework.geometry.discovery;
import java.util.Map;
public record AreaResult(boolean available, double signedArea, double absoluteArea,
        String orientation, Map<String,Double> perFaceAreas, double tolerance,
        GeometryConfigurationStatus status) {
    public AreaResult { perFaceAreas=Map.copyOf(perFaceAreas); }
    public static AreaResult unavailable(GeometryConfigurationStatus status){return new AreaResult(false,Double.NaN,Double.NaN,"UNAVAILABLE",Map.of(),Double.NaN,status);}
}
