package remframework.geometry.discovery.realization.session;
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

import java.time.Instant;
import org.joml.Vector3d;
import java.util.List;

/** Persisted summary that can be reopened without rerunning the experiment. */
public record StoredResult(ConfigurationId configurationId,
        GeometryConfigurationStatusCode status, String explanation,
        boolean realizationFound, boolean uniqueFaces, boolean closedBoundary,
        boolean areaAvailable, boolean volumeAvailable, Instant savedAt,
        List<Vector3d> finalCoordinates,List<ConstraintMeasurement> constraintMeasurements) {
    public StoredResult {
        finalCoordinates = finalCoordinates == null ? List.of()
                : finalCoordinates.stream().map(Vector3d::new).toList();
        constraintMeasurements=constraintMeasurements==null?List.of():List.copyOf(constraintMeasurements);
    }
    public StoredResult(ConfigurationId configurationId,GeometryConfigurationStatusCode status,String explanation,
            boolean realizationFound,boolean uniqueFaces,boolean closedBoundary,boolean areaAvailable,
            boolean volumeAvailable,Instant savedAt,List<Vector3d> finalCoordinates){
        this(configurationId,status,explanation,realizationFound,uniqueFaces,closedBoundary,areaAvailable,volumeAvailable,savedAt,finalCoordinates,List.of());
    }
}
