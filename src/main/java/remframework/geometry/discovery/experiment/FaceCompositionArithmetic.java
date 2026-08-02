package remframework.geometry.discovery.experiment;

import remframework.geometry.discovery.GeometryConfigurationStatusCode;

/** Euler/incidence arithmetic; a pass is necessary and never proves topology existence. */
public record FaceCompositionArithmetic(int nodeCount, int exactDegree, int commonFaceEdgeCount,
        Long expectedEdgeCount, Long expectedFaceCount, Long expectedFaceEdgeIncidences,
        Long requiredEdgeFaceIncidences, boolean passed, GeometryConfigurationStatusCode status,
        String explanation, String matchingTopology) {

    public static FaceCompositionArithmetic evaluate(int n, int k, int m,
            ExperimentProfile profile) {
        long degreeSum = (long) n * k;
        Long edges = degreeSum % 2 == 0 ? degreeSum / 2 : null;
        Long faces = edges == null ? null : 2L - n + edges;
        Long faceIncidences = faces == null ? null : (long) m * faces;
        Long edgeIncidences = edges == null ? null : 2L * edges;
        String failure = null;
        if (m < 3) failure = "Common face size must be an integer >= 3.";
        else if (!profile.planarTopologyRequired())
            failure = "The selected profile does not require a closed sphere-like combinatorial boundary.";
        else if (degreeSum % 2 != 0) failure = "N * K is odd, so E = N * K / 2 is not an integer.";
        else if (edges == null || edges <= 0) failure = "Expected edge count E is not positive.";
        else if (faces == null || faces <= 0) failure = "Expected face count F = 2 - N + E is not positive.";
        else if (!faceIncidences.equals(edgeIncidences))
            failure = "m * F does not equal the required two face incidences for every edge (2 * E).";
        boolean pass = failure == null;
        return new FaceCompositionArithmetic(n, k, m, edges, faces, faceIncidences,
                edgeIncidences, pass, pass ? GeometryConfigurationStatusCode.GENERATED
                        : GeometryConfigurationStatusCode.FACE_COMPOSITION_ARITHMETICALLY_IMPOSSIBLE,
                pass ? "Arithmetic feasibility: PASS. This is necessary only; it does not prove that a matching topology exists."
                        : "Arithmetic feasibility: FAIL. " + failure,
                pass ? "NOT_YET_DETERMINED" : "NOT_APPLICABLE");
    }
}
