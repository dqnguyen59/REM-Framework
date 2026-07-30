package remframework.geometry.discovery;

import java.util.ArrayList;
import java.util.List;

public final class DiscoveryExperimentValidator {
    public static final int MINIMUM_NODES = 4;
    public static final int MAXIMUM_NODES = 32;

    public DiscoveryExperimentValidation validate(DiscoveryExperimentConfiguration c) {
        List<String> errors = new ArrayList<>();
        if (c == null) return new DiscoveryExperimentValidation(List.of(
                "Experiment configuration is required."));
        if (c.name() == null || c.name().isBlank()) errors.add("Experiment name is required.");
        if (c.embeddingDimension() != 2 && c.embeddingDimension() != 3)
            errors.add("Embedding dimension must be 2D or 3D.");
        if (c.nodeCount() < MINIMUM_NODES || c.nodeCount() > MAXIMUM_NODES)
            errors.add("Node count must be between " + MINIMUM_NODES + " and "
                    + MAXIMUM_NODES + ".");
        if (c.minimumDegree() < 0) errors.add("Minimum neighbors must be at least 0.");
        if (c.targetInitialDegree() < c.minimumDegree())
            errors.add("Target neighbors must be at least the minimum neighbors.");
        if (c.maximumDegree() < c.targetInitialDegree())
            errors.add("Maximum neighbors must be at least the target neighbors.");
        if (c.maximumDegree() >= c.nodeCount())
            errors.add("Maximum neighbors must be smaller than node count; a node cannot connect to itself.");
        if (c.maximumDegree() < 1 && c.nodeCount() > 1)
            errors.add("A connected graph with multiple nodes requires at least one neighbor.");
        if (c.neighborCountPolicy() == DiscoveryNeighborCountPolicy.UNIFORM) {
            if (c.minimumDegree() != c.maximumDegree()
                    || c.targetInitialDegree() != c.minimumDegree())
                errors.add("Uniform policy requires minimum, target, and maximum neighbors to be equal.");
            int degree = c.minimumDegree();
            if ((long) c.nodeCount() * degree % 2 != 0)
                errors.add("Uniform degree is impossible because the sum of desired degrees is odd.");
            if (degree == 0 && c.nodeCount() > 1)
                errors.add("A uniform zero-neighbor graph cannot be connected.");
        }
        validatePreset(c, errors);
        if (!finitePositive(c.initialExtent())) errors.add("Initial radius or extent must be positive.");
        if (!Double.isFinite(c.perturbationMagnitude()) || c.perturbationMagnitude() < 0)
            errors.add("Perturbation magnitude must be finite and non-negative.");
        if (!Double.isFinite(c.edgeProbability()) || c.edgeProbability() < 0
                || c.edgeProbability() > 1) errors.add("Edge probability must be from 0 to 1.");
        if ((c.initialTopology() == DiscoveryInitialTopology.LOAD_CANDIDATE
                || c.initialTopology() == DiscoveryInitialTopology.LOAD_TOML)
                && c.sourceCandidate() == null)
            errors.add("A candidate file is required for the selected topology.");
        GeometryDiscoverySettings g = c.geometrySettings();
        if (g == null || !finitePositive(g.targetDistance()))
            errors.add("Target edge distance must be positive.");
        if (g == null || !finitePositive(g.minimumNodeSeparation()))
            errors.add("Minimum separation must be positive.");
        if (g == null || g.maximumIterations() < 1)
            errors.add("Maximum relaxation iterations must be positive.");
        if (g == null || !finitePositive(g.learningRate())
                || !finitePositive(g.repulsionStrength())
                || !finitePositive(g.maximumCorrection())
                || !finitePositive(g.convergenceTolerance()))
            errors.add("Relaxation numerical settings must be finite and positive.");
        TopologySearchSettings s = c.topologySearchSettings();
        if (s == null || s.maximumSearchIterations() < 1
                || s.mutationsAttemptedPerIteration() < 1 || s.archiveSize() < 1)
            errors.add("Topology search iterations, mutations, and archive size must be positive.");
        if (c.workerIterationsPerUpdate() < 1)
            errors.add("Worker iterations per update must be positive.");
        return new DiscoveryExperimentValidation(errors);
    }

    private void validatePreset(DiscoveryExperimentConfiguration c, List<String> errors) {
        int n = c.nodeCount();
        switch (c.initialTopology()) {
            case LINE -> {
                if (c.neighborCountPolicy() == DiscoveryNeighborCountPolicy.UNIFORM && n > 2)
                    errors.add("Line topology is not uniform: endpoints have one neighbor and interior nodes have two.");
                if (c.minimumDegree() > 1 || c.maximumDegree() < 2)
                    errors.add("Line topology requires bounds containing endpoint degree 1 and interior degree 2.");
            }
            case RING -> {
                if (c.minimumDegree() > 2 || c.maximumDegree() < 2)
                    errors.add("Ring topology requires two neighbors per node.");
                if (c.neighborCountPolicy() == DiscoveryNeighborCountPolicy.UNIFORM
                        && c.targetInitialDegree() != 2)
                    errors.add("Uniform ring topology requires exactly two neighbors per node.");
            }
            case COMPLETE_GRAPH -> {
                int degree = n - 1;
                if (c.minimumDegree() > degree || c.maximumDegree() < degree)
                    errors.add("Complete graph requires exactly " + degree + " neighbors per node.");
                if (c.neighborCountPolicy() == DiscoveryNeighborCountPolicy.UNIFORM
                        && c.targetInitialDegree() != degree)
                    errors.add("Uniform complete graph requires " + degree + " neighbors per node.");
            }
            case RANDOM_TREE -> {
                if (c.minimumDegree() > 1)
                    errors.add("A finite tree necessarily has leaf nodes with one neighbor.");
            }
            default -> { }
        }
    }

    private boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0;
    }
}
