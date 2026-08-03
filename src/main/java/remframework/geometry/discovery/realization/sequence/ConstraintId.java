package remframework.geometry.discovery.realization.sequence;

import java.util.Set;

/** Stable, persistence-facing identifiers for Stage 2 geometric constraints. */
public enum ConstraintId {
    EDGE_LENGTH("Edge Length", Set.of()),
    FACE_ORIENTATION("Face Orientation", Set.of()),
    CONVEXITY("Convexity", Set.of()),
    FACE_PLANARITY("Face Planarity", Set.of()),
    FACE_ANGLE("Face Angle", Set.of(Prerequisite.NONDEGENERATE_FACE_EDGES)),
    FACE_DISTANCE("Face Distance", Set.of(Prerequisite.VALID_FACE_PLANES)),
    NORMAL_ALIGNMENT("Normal Alignment", Set.of(Prerequisite.VALID_FACE_NORMALS)),
    FACE_AREA("Face Area", Set.of(Prerequisite.VALID_FACE_PLANES)),
    DIHEDRAL_ANGLE("Dihedral Angle", Set.of(Prerequisite.ADJACENT_VALID_FACE_NORMALS)),
    VERTEX_DISTANCE("Vertex Distance", Set.of()),
    FACE_CENTER_DISTANCE("Face-Center Distance", Set.of(Prerequisite.VALID_FACE_PLANES)),
    EDGE_CENTER_DISTANCE("Edge-Center Distance", Set.of()),
    CLOSED_BOUNDARY("Closed Boundary", Set.of()),
    SURFACE_ORIENTATION("Surface Orientation", Set.of(Prerequisite.CLOSED_COHERENT_BOUNDARY)),
    SELF_INTERSECTION("Self-Intersection", Set.of()),
    POSITIVE_VOLUME("Positive Volume", Set.of(Prerequisite.CLOSED_COHERENT_BOUNDARY)),
    FINAL_VALIDATION("Final Validation", Set.of(Prerequisite.MUST_BE_LAST));

    private final String displayName;
    private final Set<Prerequisite> prerequisites;
    ConstraintId(String displayName, Set<Prerequisite> prerequisites) {
        this.displayName=displayName;this.prerequisites=prerequisites;
    }
    public String displayName(){return displayName;}
    public Set<Prerequisite> prerequisites(){return prerequisites;}

    public enum Prerequisite {
        VALID_FACE_PLANES("requires valid face planes", false),
        VALID_FACE_NORMALS("requires valid face normals", false),
        ADJACENT_VALID_FACE_NORMALS("requires adjacent valid face normals", false),
        NONDEGENERATE_FACE_EDGES("requires nondegenerate face edges", false),
        CLOSED_COHERENT_BOUNDARY("requires a closed coherent boundary", false),
        MUST_BE_LAST("must be the last enabled stage", true);
        private final String explanation;private final boolean mathematicallyUndefined;
        Prerequisite(String explanation,boolean mathematicallyUndefined){this.explanation=explanation;this.mathematicallyUndefined=mathematicallyUndefined;}
        public String explanation(){return explanation;}
        public boolean mathematicallyUndefined(){return mathematicallyUndefined;}
    }
}
