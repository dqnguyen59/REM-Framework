package remframework.geometry.discovery;

import java.util.List;

public record GeometryConfigurationDiagnostic(
        GeometryConfigurationStage stage, GeometryConfigurationStatusCode code,
        GeometryConfigurationSeverity severity, String summary, String explanation,
        List<Integer> affectedNodeIds, List<Long> affectedEdgeIds,
        List<String> affectedFaceIds, boolean mayContinue) {
    public GeometryConfigurationDiagnostic {
        if (stage == null || code == null || severity == null)
            throw new IllegalArgumentException("Diagnostic stage, code, and severity are required.");
        summary = summary == null ? "" : summary;
        explanation = explanation == null ? "" : explanation;
        affectedNodeIds = affectedNodeIds == null ? List.of() : List.copyOf(affectedNodeIds);
        affectedEdgeIds = affectedEdgeIds == null ? List.of() : List.copyOf(affectedEdgeIds);
        affectedFaceIds = affectedFaceIds == null ? List.of() : List.copyOf(affectedFaceIds);
    }
    public static GeometryConfigurationDiagnostic info(GeometryConfigurationStage stage,
            GeometryConfigurationStatusCode code, String summary, String explanation) {
        return new GeometryConfigurationDiagnostic(stage, code, GeometryConfigurationSeverity.INFO,
                summary, explanation, List.of(), List.of(), List.of(), true);
    }
    public static GeometryConfigurationDiagnostic stop(GeometryConfigurationStage stage,
            GeometryConfigurationStatusCode code, String summary, String explanation) {
        return new GeometryConfigurationDiagnostic(stage, code, GeometryConfigurationSeverity.ERROR,
                summary, explanation, List.of(), List.of(), List.of(), false);
    }
}
