package remframework.geometry.discovery;

import java.util.Map;

public record TopologyMetrics(
        int nodeCount, int edgeCount, int minimumDegree, int maximumDegree,
        double meanDegree, double degreeStandardDeviation, int connectedComponents,
        int graphDiameter, double meanShortestPathLength, double clusteringCoefficient,
        int triangleCount, int bridgeEdgeCount, int articulationPointCount,
        Map<Integer, Integer> degreeDistribution) {
}
