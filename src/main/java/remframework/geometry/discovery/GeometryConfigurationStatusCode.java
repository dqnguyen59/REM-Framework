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

public enum GeometryConfigurationStatusCode {
    GENERATED, INVALID_PARAMETERS, INVALID_NODE_COUNT, INVALID_DEGREE,
    DEGREE_SUM_ODD, CONNECTED_REGULAR_GRAPH_IMPOSSIBLE, GENERATION_FAILED,
    DISCONNECTED, DUPLICATE_ISOMORPH, SYMMETRY_FILTERED,
    ENUMERATION_LIMIT_REACHED, RESOURCE_LIMIT_REACHED, ENUMERATION_COMPLETE,
    GEOMETRY_TEST_NOT_STARTED, GEOMETRY_INITIALIZED, GEOMETRY_RELAXING,
    GEOMETRICALLY_REALIZED, NOT_EMBEDDABLE, NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,
    NUMERICAL_FAILURE, DEGENERATE, FLEXIBLE, RIGID, SELF_INTERSECTING,
    FACES_NOT_DERIVABLE, AMBIGUOUS_FACE_SET, OPEN_BOUNDARY,
    NON_MANIFOLD_BOUNDARY, CLOSED_BOUNDARY, GEOMETRIC_SYMMETRY_UNKNOWN,
    SYMMETRY_MISMATCH, AREA_AVAILABLE, VOLUME_AVAILABLE, VALID_CANDIDATE,
    FACE_COMPOSITION_ARITHMETICALLY_IMPOSSIBLE,
    NO_COMBINATORIAL_FACE_SET, AMBIGUOUS_COMBINATORIAL_FACE_SET,
    OPEN_COMBINATORIAL_BOUNDARY, NON_MANIFOLD_COMBINATORIAL_BOUNDARY,
    NON_UNIFORM_FACE_SIZE, FACE_SIZE_MISMATCH, ELIGIBLE_FOR_GEOMETRY_TEST
}
