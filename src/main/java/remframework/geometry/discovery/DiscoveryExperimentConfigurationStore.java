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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists complete, reproducible experiment inputs. Loading never starts a
 * worker operation.
 */
public final class DiscoveryExperimentConfigurationStore {
    public Path save(DiscoveryExperimentConfiguration c, Path requested) throws IOException {
        Path target = requested.toAbsolutePath().normalize();
        if (target.getFileName().toString().equalsIgnoreCase("cubic-lattice.toml"))
            throw new IllegalArgumentException("Experiment configuration cannot overwrite cubic-lattice.toml.");
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Properties p = new Properties();
        put(p, "formatVersion", "1.0");
        put(p, "generator", GeometryDiscoveryExporter.GENERATOR_NAME);
        put(p, "generatorVersion", GeometryDiscoveryExporter.GENERATOR_VERSION);
        put(p, "name", c.name()); put(p, "description", c.description());
        put(p, "dimension", c.embeddingDimension()); put(p, "nodes", c.nodeCount());
        put(p, "seed", c.randomSeed()); put(p, "profile", c.scoringProfile());
        put(p, "customWeights", c.customWeights()); put(p, "topology", c.initialTopology());
        put(p, "neighborCountPolicy", c.neighborCountPolicy());
        put(p, "minimumDegree", c.minimumDegree()); put(p, "maximumDegree", c.maximumDegree());
        put(p, "targetInitialDegree", c.targetInitialDegree());
        put(p, "edgeProbability", c.edgeProbability());
        put(p, "sourceCandidate", c.sourceCandidate() == null ? "" : c.sourceCandidate());
        put(p, "geometryInitialization", c.geometryInitialization());
        put(p, "initialExtent", c.initialExtent());
        put(p, "perturbation", c.perturbationMagnitude());
        put(p, "workerIterationsPerUpdate", c.workerIterationsPerUpdate());
        GeometryDiscoverySettings g = c.geometrySettings();
        put(p, "targetDistance", g.targetDistance()); put(p, "learningRate", g.learningRate());
        put(p, "maximumRelaxationIterations", g.maximumIterations());
        put(p, "convergenceTolerance", g.convergenceTolerance());
        put(p, "minimumSeparation", g.minimumNodeSeparation());
        put(p, "repulsionStrength", g.repulsionStrength());
        put(p, "maximumCorrection", g.maximumCorrection());
        put(p, "dimensionalityTolerance", g.dimensionalityTolerance());
        put(p, "progressInterval", g.progressInterval());
        TopologySearchSettings s = c.topologySearchSettings();
        put(p, "searchMode", s.searchMode()); put(p, "maximumSearchIterations",
                s.maximumSearchIterations()); put(p, "mutations", s.mutationsAttemptedPerIteration());
        put(p, "archiveSize", s.archiveSize()); put(p, "initialTemperature", s.initialTemperature());
        put(p, "coolingRate", s.coolingRate()); put(p, "minimumTemperature", s.minimumTemperature());
        put(p, "improvementTolerance", s.scoreImprovementTolerance());
        put(p, "checkpointInterval", s.checkpointInterval());
        put(p, "weights", weights(c.scoreWeights()));
        try (Writer writer = Files.newBufferedWriter(target)) {
            p.store(writer, "REM Framework Geometry Discovery experiment configuration");
        }
        return target;
    }

    public DiscoveryExperimentConfiguration load(Path path) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        long seed = lng(p, "seed");
        GeometryDiscoverySettings g = new GeometryDiscoverySettings(
                dbl(p, "targetDistance"), dbl(p, "learningRate"),
                integer(p, "maximumRelaxationIterations"), dbl(p, "convergenceTolerance"),
                dbl(p, "minimumSeparation"), dbl(p, "repulsionStrength"), seed,
                dbl(p, "maximumCorrection"), dbl(p, "dimensionalityTolerance"),
                integer(p, "progressInterval"));
        TopologyScoringProfile profile = TopologyScoringProfile.valueOf(p.getProperty("profile"));
        TopologyScoreWeights weights = parseWeights(p.getProperty("weights"));
        TopologySearchSettings s = new TopologySearchSettings(
                TopologySearchMode.valueOf(p.getProperty("searchMode")), seed,
                integer(p, "maximumSearchIterations"), integer(p, "mutations"),
                integer(p, "minimumDegree"), integer(p, "maximumDegree"), -1,
                integer(p, "archiveSize"), dbl(p, "initialTemperature"),
                dbl(p, "coolingRate"), dbl(p, "minimumTemperature"),
                dbl(p, "improvementTolerance"), g, weights, profile,
                TopologyDiagnosticSettings.defaults(), integer(p, "checkpointInterval"));
        String source = p.getProperty("sourceCandidate", "");
        return new DiscoveryExperimentConfiguration(
                p.getProperty("name"), p.getProperty("description", ""),
                integer(p, "dimension"), integer(p, "nodes"), seed, profile, weights,
                Boolean.parseBoolean(p.getProperty("customWeights")),
                DiscoveryInitialTopology.valueOf(p.getProperty("topology")),
                DiscoveryNeighborCountPolicy.valueOf(
                        p.getProperty("neighborCountPolicy", "BOUNDED")),
                integer(p, "minimumDegree"), integer(p, "maximumDegree"),
                dbl(p, "targetInitialDegree"), dbl(p, "edgeProbability"),
                source.isBlank() ? null : Path.of(source),
                DiscoveryGeometryInitialization.valueOf(p.getProperty("geometryInitialization")),
                dbl(p, "initialExtent"), dbl(p, "perturbation"), g,
                integer(p, "workerIterationsPerUpdate"), s);
    }

    private String weights(TopologyScoreWeights w) {
        return String.join(",", Double.toString(w.edgeLengthError()),
                Double.toString(w.edgeLengthVariance()), Double.toString(w.separationCollision()),
                Double.toString(w.nonConvergence()), Double.toString(w.directionalBias()),
                Double.toString(w.tensorIsotropy()), Double.toString(w.knownReferenceAngular()),
                Double.toString(w.sphericalCoverage()), Double.toString(w.oppositeDirection()),
                Double.toString(w.localDimensionality()), Double.toString(w.degreeVariance()),
                Double.toString(w.bridgePenalty()), Double.toString(w.articulationPenalty()),
                Double.toString(w.graphDiameterPreference()),
                Double.toString(w.runtimeCompatibility()), Double.toString(w.profileDegree()));
    }
    private TopologyScoreWeights parseWeights(String text) {
        String[] v = text.split(",");
        if (v.length != 16) throw new IllegalArgumentException("Expected 16 score weights.");
        double[] d = new double[16];
        for (int i = 0; i < d.length; i++) d[i] = Double.parseDouble(v[i]);
        return new TopologyScoreWeights(d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7],
                d[8], d[9], d[10], d[11], d[12], d[13], d[14], d[15]);
    }
    private void put(Properties p, String key, Object value) {
        p.setProperty(key, String.valueOf(value));
    }
    private int integer(Properties p, String key) {
        return Integer.parseInt(p.getProperty(key));
    }
    private long lng(Properties p, String key) { return Long.parseLong(p.getProperty(key)); }
    private double dbl(Properties p, String key) { return Double.parseDouble(p.getProperty(key)); }
}
