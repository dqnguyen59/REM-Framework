package remframework.geometry.discovery.experiment;

import remframework.geometry.discovery.GeometryConfigurationStatusCode;

import java.util.List;
import java.util.Map;

/** Stage 1 topology-only result for a selected face-composition experiment. */
public record FaceCompositionReport(FaceComposition composition,
        FaceCompositionArithmetic arithmetic, FaceCompositionMatch match,
        int actualFaceCount, Map<Integer, Integer> faceEdgeCountDistribution,
        List<List<Integer>> faces, List<GeometryConfigurationStatusCode> reasons,
        String explanation) {
    public FaceCompositionReport {
        faceEdgeCountDistribution = Map.copyOf(faceEdgeCountDistribution);
        faces = faces.stream().map(List::copyOf).toList();
        reasons = List.copyOf(reasons);
    }
}
