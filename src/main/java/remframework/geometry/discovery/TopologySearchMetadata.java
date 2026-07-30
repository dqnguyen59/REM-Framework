package remframework.geometry.discovery;

public record TopologySearchMetadata(
        double totalScore, String searchMode, long searchSeed, String parentMutation,
        double edgeLengthComponent, double directionalComponent, double tensorComponent,
        double degreeVarianceComponent, int minimumDegree, int maximumDegree,
        double meanDegree, int graphDiameter, int bridgeCount, int articulationPointCount,
        double meanDirectionalImbalance, double tensorAnisotropy) {
}
