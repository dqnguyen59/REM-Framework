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
public record AreaResult(boolean available, double signedArea, double absoluteArea,
        String orientation, Map<String,Double> perFaceAreas, double tolerance,
        GeometryConfigurationStatus status) {
    public AreaResult { perFaceAreas=Map.copyOf(perFaceAreas); }
    public static AreaResult unavailable(GeometryConfigurationStatus status){return new AreaResult(false,Double.NaN,Double.NaN,"UNAVAILABLE",Map.of(),Double.NaN,status);}
}
