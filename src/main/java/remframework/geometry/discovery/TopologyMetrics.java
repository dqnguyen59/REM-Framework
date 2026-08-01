package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import java.util.Map;

public record TopologyMetrics(
        int nodeCount, int edgeCount, int minimumDegree, int maximumDegree,
        double meanDegree, double degreeStandardDeviation, int connectedComponents,
        int graphDiameter, double meanShortestPathLength, double clusteringCoefficient,
        int triangleCount, int bridgeEdgeCount, int articulationPointCount,
        Map<Integer, Integer> degreeDistribution) {
}
