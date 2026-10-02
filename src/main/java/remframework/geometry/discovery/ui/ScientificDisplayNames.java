package remframework.geometry.discovery.ui;

import remframework.geometry.discovery.realization.session.ContinuationConstraint;

import java.util.Map;

/** Central user-facing vocabulary for scientific constraints. */
public final class ScientificDisplayNames {
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("edge-equality", "Edge Length"),
            Map.entry("outward-face-orientation", "Face Orientation"),
            Map.entry("face-planarity", "Face Planarity"),
            Map.entry("face-angle-equality", "Face Angle"),
            Map.entry("face-plane-radius-equality", "Face Distance"),
            Map.entry("dihedral-equality", "Dihedral Angle"),
            Map.entry("vertex-radius-equality", "Vertex Distance"),
            Map.entry("face-centroid-radius-equality", "Face-Center Distance"),
            Map.entry("edge-midpoint-radius-equality", "Edge-Center Distance"),
            Map.entry("normal-center-alignment", "Normal Alignment"),
            Map.entry("equal-face-area", "Face Area"),
            Map.entry("closed-manifold", "Closed Boundary"),
            Map.entry("coherent-orientation", "Surface Orientation"),
            Map.entry("self-intersection", "Self-Intersection"),
            Map.entry("positive-volume", "Positive Volume"),
            Map.entry("joint-qualification", "Final Validation"),
            Map.entry("convexity", "Convexity"));
    private static final Map<String, String> TOOLTIPS = Map.ofEntries(
            Map.entry("edge-equality", "Difference between each connected edge length and the configured target length."),
            Map.entry("outward-face-orientation", "Whether the coherently ordered face normals point away from the selected geometry center."),
            Map.entry("face-planarity", "How far the vertices of each face lie from one plane."),
            Map.entry("face-angle-equality", "Difference between measured interior angles and the regular polygon angle derived from the face vertex count."),
            Map.entry("face-plane-radius-equality", "Perpendicular distance from the selected center to each face plane."),
            Map.entry("face-centroid-radius-equality", "Distance from the selected center to each face centroid."),
            Map.entry("vertex-radius-equality", "Distance from the selected center to each vertex."),
            Map.entry("edge-midpoint-radius-equality", "Distance from the selected center to each edge midpoint."),
            Map.entry("normal-center-alignment", "How closely a face normal aligns with the direction from the center to that face."),
            Map.entry("equal-face-area", "Area of each accepted face and its deviation from the selected common-area metric."));

    private ScientificDisplayNames() { }

    public static String constraint(String id) { return NAMES.getOrDefault(id, humanize(id)); }
    public static String tooltip(String id) { return TOOLTIPS.getOrDefault(id, "Zero error is ideal; see Discovery Details for the exact metric definition."); }
    public static String continuation(ContinuationConstraint value) {
        return switch (value) {
            case EDGE_EQUALITY -> "Edge Length";
            case OUTWARD_ORIENTATION -> "Face Orientation";
            case FACE_PLANARITY -> "Face Planarity";
            case FACE_ANGLE_EQUALITY -> "Face Angle";
            case FACE_PLANE_RADII -> "Face Distance";
            case JOINT_QUALIFICATION -> "Final Validation";
        };
    }
    public static String stage(String id) {
        if (id == null) return "Not started";
        String key = id.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (key.contains("edge")) return "Edge Length";
        if (key.contains("orientation")) return "Face Orientation";
        if (key.contains("planarity")) return "Face Planarity";
        if (key.contains("angle")) return "Face Angle";
        if (key.contains("plane") || key.contains("radius")) return "Face Distance";
        if (key.contains("qualification")) return "Final Validation";
        return humanize(id);
    }
    private static String humanize(String value) {
        String text = value.replace('-', ' ').replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
