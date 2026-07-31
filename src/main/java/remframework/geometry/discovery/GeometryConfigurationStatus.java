package remframework.geometry.discovery;

import java.util.List;

public record GeometryConfigurationStatus(GeometryConfigurationStage stage,
        GeometryConfigurationStatusCode code, GeometryConfigurationSeverity severity,
        String summary, String explanation, boolean mayContinue,
        List<GeometryConfigurationDiagnostic> diagnostics) {
    public GeometryConfigurationStatus {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (stage == null || code == null || severity == null)
            throw new IllegalArgumentException("Structured status fields are required.");
        summary = summary == null ? "" : summary;
        explanation = explanation == null ? "" : explanation;
        if (!mayContinue && diagnostics.stream().noneMatch(d -> !d.mayContinue()))
            throw new IllegalArgumentException("A stopped stage requires a stopping diagnostic.");
    }
    public static GeometryConfigurationStatus of(GeometryConfigurationDiagnostic diagnostic) {
        return new GeometryConfigurationStatus(diagnostic.stage(), diagnostic.code(),
                diagnostic.severity(), diagnostic.summary(), diagnostic.explanation(),
                diagnostic.mayContinue(), List.of(diagnostic));
    }
}
