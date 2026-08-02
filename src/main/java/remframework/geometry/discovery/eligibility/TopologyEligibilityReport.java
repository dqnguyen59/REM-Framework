package remframework.geometry.discovery.eligibility;

import remframework.geometry.discovery.GraphSymmetryClassification;
import remframework.geometry.discovery.experiment.ExperimentProfile;
import remframework.geometry.discovery.experiment.FaceCompositionReport;

import java.util.List;

public record TopologyEligibilityReport(ExperimentProfile profile, EligibilityStatus status,
        String conclusion, List<ConditionResult> conditions, int bridgeCount,
        int articulationVertexCount, Integer vertexConnectivity, Integer edgeConnectivity,
        int cycleRank, Boolean planar, long automorphismGroupSize,
        GraphSymmetryClassification graphSymmetry, boolean vertexTransitive,
        List<List<Integer>> topologicalFaces, FaceCompositionReport faceComposition) {
    public TopologyEligibilityReport {
        conditions = List.copyOf(conditions);
        topologicalFaces = topologicalFaces.stream().map(List::copyOf).toList();
    }
    public TopologyEligibilityReport(ExperimentProfile profile, EligibilityStatus status,
            String conclusion, List<ConditionResult> conditions, int bridgeCount,
            int articulationVertexCount, Integer vertexConnectivity, Integer edgeConnectivity,
            int cycleRank, Boolean planar, long automorphismGroupSize,
            GraphSymmetryClassification graphSymmetry, boolean vertexTransitive,
            List<List<Integer>> topologicalFaces) {
        this(profile,status,conclusion,conditions,bridgeCount,articulationVertexCount,
                vertexConnectivity,edgeConnectivity,cycleRank,planar,automorphismGroupSize,
                graphSymmetry,vertexTransitive,topologicalFaces,null);
    }
    public boolean eligible() { return status != EligibilityStatus.INELIGIBLE; }
    public List<ConditionResult> failedConditions() {
        return conditions.stream().filter(c -> c.outcome() == ConditionOutcome.FAIL).toList();
    }
}
