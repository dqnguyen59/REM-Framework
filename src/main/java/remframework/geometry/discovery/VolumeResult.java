package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
import java.util.Map;
public record VolumeResult(boolean available,double signedVolume,double absoluteVolume,
        String orientation,double tolerance,Map<String,Double> contributions,
        GeometryConfigurationStatus status) {
    public VolumeResult { contributions=Map.copyOf(contributions); }
    public static VolumeResult unavailable(GeometryConfigurationStatus status){return new VolumeResult(false,Double.NaN,Double.NaN,"UNAVAILABLE",Double.NaN,Map.of(),status);}
}
