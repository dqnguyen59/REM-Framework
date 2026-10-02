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

public enum GeometryConfigurationStage {
    REQUEST_VALIDATION, GRAPH_GENERATION, GRAPH_VALIDATION,
    ISOMORPHISM_CLASSIFICATION, SYMMETRY_CLASSIFICATION,
    GEOMETRY_INITIALIZATION, GEOMETRY_REALIZATION, FACE_DERIVATION,
    BOUNDARY_VALIDATION, GEOMETRIC_SYMMETRY, AREA_CALCULATION,
    VOLUME_CALCULATION, OPTIMIZATION, COMPLETE
}
