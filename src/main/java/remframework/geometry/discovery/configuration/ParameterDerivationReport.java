package remframework.geometry.discovery.configuration;

import remframework.geometry.discovery.GeometryConfigurationStatusCode;

import java.util.List;
import java.util.Map;

/** Exact, pre-enumeration derivation and generation-capability report. */
public record ParameterDerivationReport(PrimaryParameterMode primaryParameterMode,
        SurfaceTopologyAssumption surfaceTopologyAssumption,
        Map<String,String> authoritativeInputs, Long derivedVertexCount,
        Long derivedEdgeCount, Long derivedFaceCount, int eulerCharacteristic,
        String degreeIncidenceCalculation, String faceIncidenceCalculation,
        List<String> divisibilityChecks, boolean arithmeticFeasible,
        boolean generationStrategyAvailable, String selectedGenerationStrategy,
        List<GeometryConfigurationStatusCode> reasons, List<String> formulas,
        String explanation) {
    public ParameterDerivationReport {
        authoritativeInputs=Map.copyOf(authoritativeInputs);
        divisibilityChecks=List.copyOf(divisibilityChecks);
        reasons=List.copyOf(reasons);
        formulas=List.copyOf(formulas);
    }

    public GeometryConfigurationStatusCode primaryStatus() {
        return reasons.isEmpty()?GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS:reasons.getFirst();
    }
}
