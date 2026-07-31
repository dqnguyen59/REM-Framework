package remframework.geometry.discovery;
import java.util.Map;
public record VolumeResult(boolean available,double signedVolume,double absoluteVolume,
        String orientation,double tolerance,Map<String,Double> contributions,
        GeometryConfigurationStatus status) {
    public VolumeResult { contributions=Map.copyOf(contributions); }
    public static VolumeResult unavailable(GeometryConfigurationStatus status){return new VolumeResult(false,Double.NaN,Double.NaN,"UNAVAILABLE",Double.NaN,Map.of(),status);}
}
