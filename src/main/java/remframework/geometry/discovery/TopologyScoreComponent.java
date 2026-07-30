package remframework.geometry.discovery;

public record TopologyScoreComponent(
        String name, double rawValue, double normalizedValue, double weight,
        double weightedContribution, boolean applicable) {
}
