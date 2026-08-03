package remframework.geometry.discovery.realization.session;

import java.util.Set;

/** Public manual-relaxation controls; IDs remain stable across persisted experiments. */
public enum ManualConstraint {
    EDGE_LENGTH("Edge Length", Set.of()), FACE_ORIENTATION("Face Orientation", Set.of()),
    CONVEXITY("Convexity", Set.of()), FACE_PLANARITY("Face Planarity", Set.of()),
    FACE_ANGLE("Face Angle", Set.of()), FACE_DISTANCE("Face Distance", Set.of()),
    NORMAL_ALIGNMENT("Normal Alignment", Set.of()), FACE_AREA("Face Area", Set.of()),
    DIHEDRAL_ANGLE("Dihedral Angle", Set.of()), VERTEX_DISTANCE("Vertex Distance", Set.of()),
    FACE_CENTER_DISTANCE("Face-Center Distance", Set.of()), EDGE_CENTER_DISTANCE("Edge-Center Distance", Set.of());
    private final String displayName;private final Set<String> groups;
    ManualConstraint(String displayName,Set<String> groups){this.displayName=displayName;this.groups=groups;}
    public String displayName(){return displayName;}
    public String group(){return ordinal()<8?"Shape Construction":"Regularity";}
}
