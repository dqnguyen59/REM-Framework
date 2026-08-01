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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class TopologySearchExporter {
    private final GeometryDiscoveryExporter geometryExporter = new GeometryDiscoveryExporter();

    public Path export(TopologySearchResult result, Path outputDirectory) throws IOException {
        Path directory = outputDirectory.toAbsolutePath().normalize();
        rejectTemplate(directory);
        Files.createDirectories(directory);
        Path best = writeCandidate(result.bestCandidate(), result, directory.resolve("best-candidate.toml"));
        Path top = directory.resolve("top-candidates");
        Files.createDirectories(top);
        for (int i = 0; i < result.topCandidates().size(); i++)
            writeCandidate(result.topCandidates().get(i), result,
                    top.resolve(String.format(Locale.ROOT, "candidate-%03d.toml", i + 1)));
        writeHistory(result, directory.resolve("search-history.csv"));
        writeScores(result, directory.resolve("score-components.csv"));
        writeSummary(result, best, directory.resolve("summary.txt"));
        writeCheckpoint(result, directory.resolve("checkpoint.toml"));
        return best;
    }

    private Path writeCandidate(TopologyCandidate candidate, TopologySearchResult search, Path path)
            throws IOException {
        rejectTemplate(path);
        StringBuilder text = new StringBuilder(geometryExporter.serialize(candidate.geometryResult()));
        text.append("\n[topologySearch]\n");
        quoted(text, "metadataVersion", "0.2");
        text.append("candidateId = ").append(candidate.candidateId()).append('\n');
        if (candidate.parentCandidateId() != null)
            text.append("parentCandidateId = ").append(candidate.parentCandidateId()).append('\n');
        quoted(text, "parentMutation", candidate.mutation() == null ? "initial"
                : candidate.mutation().description());
        quoted(text, "searchMode", search.settings().searchMode().name());
        quoted(text, "scoringProfile", search.settings().scoringProfile().name());
        text.append("searchSeed = ").append(search.settings().randomSeed()).append('\n');
        number(text, "totalScore", candidate.score().total());
        text.append("\n[topologySearch.scoreComponents]\n");
        for (Map.Entry<String, Double> entry : candidate.score().components().entrySet())
            number(text, entry.getKey(), entry.getValue());
        for (TopologyScoreComponent component : candidate.score().details().values()) {
            text.append("\n[[topologySearch.scoreExplanation]]\n");
            quoted(text, "name", component.name());
            text.append("applicable = ").append(component.applicable()).append('\n');
            number(text, "raw", component.rawValue());
            number(text, "normalized", component.normalizedValue());
            number(text, "weight", component.weight());
            number(text, "contribution", component.weightedContribution());
        }
        TopologyMetrics metrics = candidate.topologyMetrics();
        text.append("\n[topologySearch.metrics]\n");
        text.append("nodeCount = ").append(metrics.nodeCount()).append('\n');
        text.append("edgeCount = ").append(metrics.edgeCount()).append('\n');
        text.append("minimumDegree = ").append(metrics.minimumDegree()).append('\n');
        text.append("maximumDegree = ").append(metrics.maximumDegree()).append('\n');
        number(text, "meanDegree", metrics.meanDegree());
        number(text, "degreeStandardDeviation", metrics.degreeStandardDeviation());
        text.append("graphDiameter = ").append(metrics.graphDiameter()).append('\n');
        number(text, "meanShortestPathLength", metrics.meanShortestPathLength());
        number(text, "clusteringCoefficient", metrics.clusteringCoefficient());
        text.append("triangleCount = ").append(metrics.triangleCount()).append('\n');
        text.append("bridgeEdgeCount = ").append(metrics.bridgeEdgeCount()).append('\n');
        text.append("articulationPointCount = ").append(metrics.articulationPointCount()).append('\n');
        DirectionalBalanceMetrics directional = candidate.directionalMetrics();
        text.append("\n[topologySearch.directionalBalance]\n");
        number(text, "meanNormalizedDirectionalBias",
                directional.meanNormalizedDirectionalBias());
        number(text, "maximumNormalizedDirectionalBias",
                directional.maximumNormalizedDirectionalBias());
        number(text, "standardDeviationNormalizedDirectionalBias",
                directional.standardDeviationNormalizedDirectionalBias());
        text.append("worstBiasNodeId = ").append(directional.worstBiasNodeId()).append('\n');
        number(text, "meanTensorIsotropyError", directional.meanTensorIsotropyError());
        number(text, "meanKnownReferenceAngularError",
                directional.meanKnownReferenceAngularError());
        text.append("angularReferenceApplicableNodeCount = ")
                .append(directional.angularReferenceApplicableNodeCount()).append('\n');
        number(text, "meanNearestDirectionAngle", directional.meanNearestDirectionAngle());
        number(text, "maximumUncoveredAngle", directional.maximumUncoveredAngle());
        number(text, "meanAngularCoverageVariance",
                directional.meanAngularCoverageVariance());
        number(text, "meanOppositeDirectionMismatch",
                directional.meanOppositeDirectionMismatch());
        number(text, "maximumOppositeDirectionMismatch",
                directional.maximumOppositeDirectionMismatch());
        text.append("missingOppositeDirectionCount = ")
                .append(directional.missingOppositeDirectionCount()).append('\n');
        text.append("dimensionDeficientNodeCount = ")
                .append(directional.dimensionDeficientNodeCount()).append('\n');
        text.append("localGlobalDimensionMismatchCount = ")
                .append(directional.localGlobalDimensionMismatchCount()).append('\n');
        text.append("globalDimensionality = ").append(directional.globalDimensionality()).append('\n');
        // Legacy aliases retained for version 0.1 readers.
        number(text, "meanLocalImbalance", directional.meanNormalizedDirectionalBias());
        number(text, "meanTensorAnisotropy", directional.meanTensorIsotropyError());
        for (LocalDirectionalDiagnostics local : directional.localDiagnostics().values()) {
            text.append("\n[[topologySearch.localDiagnostics]]\n");
            text.append("nodeId = ").append(local.nodeId()).append('\n');
            text.append("degree = ").append(local.degree()).append('\n');
            text.append("localDimensionality = ").append(local.localDimensionality()).append('\n');
            text.append("eigenvalues = [");
            for (int i = 0; i < local.eigenvalues().size(); i++) {
                if (i > 0) text.append(", ");
                text.append(format(local.eigenvalues().get(i)));
            }
            text.append("]\n");
            number(text, "normalizedDirectionalBias", local.normalizedDirectionalBias());
            number(text, "tensorIsotropyError", local.tensorIsotropyError());
            quoted(text, "angularReferenceType", local.angularReferenceType().name());
            text.append("angularReferenceApplicable = ")
                    .append(local.angularReferenceApplicable()).append('\n');
            number(text, "knownReferenceAngularError", local.knownReferenceAngularError());
            number(text, "meanOppositeDirectionMismatch",
                    local.meanOppositeDirectionMismatch());
        }
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private void writeHistory(TopologySearchResult result, Path path) throws IOException {
        StringBuilder out = new StringBuilder(
                "iteration,currentScore,bestScore,scoringProfile,accepted,mutationType,temperature,elapsedMilliseconds");
        for (String name : result.bestCandidate().score().details().keySet())
            out.append(',').append(name).append("_applicable")
                    .append(',').append(name).append("_raw")
                    .append(',').append(name).append("_normalized")
                    .append(',').append(name).append("_weight")
                    .append(',').append(name).append("_contribution");
        out.append('\n');
        for (TopologySearchHistoryEntry row : result.history()) {
            out.append(row.iteration()).append(',').append(format(row.currentScore())).append(',')
                    .append(format(row.bestScore())).append(',')
                    .append(result.settings().scoringProfile()).append(',').append(row.accepted()).append(',')
                    .append(row.mutationType()).append(',').append(format(row.temperature())).append(',')
                    .append(row.elapsedMilliseconds());
            for (String name : result.bestCandidate().score().details().keySet()) {
                TopologyScoreComponent component = row.currentScoreComponents().get(name);
                if (component == null) out.append(",false,0,0,0,0");
                else out.append(',').append(component.applicable()).append(',')
                        .append(format(component.rawValue())).append(',')
                        .append(format(component.normalizedValue())).append(',')
                        .append(format(component.weight())).append(',')
                        .append(format(component.weightedContribution()));
            }
            out.append('\n');
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private void writeScores(TopologySearchResult result, Path path) throws IOException {
        StringBuilder out = new StringBuilder(
                "rank,candidateId,scoringProfile,total,metric,applicable,raw,normalized,weight,weightedContribution,percentOfTotal\n");
        int rank = 0;
        for (TopologyCandidate candidate : result.topCandidates()) {
            rank++;
            for (TopologyScoreComponent component : candidate.score().details().values()) {
                double percent = candidate.score().total() == 0 ? 0
                        : component.weightedContribution() / candidate.score().total() * 100;
                out.append(rank).append(',').append(candidate.candidateId()).append(',')
                        .append(result.settings().scoringProfile()).append(',')
                        .append(format(candidate.score().total())).append(',')
                        .append(component.name()).append(',').append(component.applicable()).append(',')
                        .append(format(component.rawValue())).append(',')
                        .append(format(component.normalizedValue())).append(',')
                        .append(format(component.weight())).append(',')
                        .append(format(component.weightedContribution())).append(',')
                        .append(format(percent)).append('\n');
            }
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private void writeSummary(TopologySearchResult result, Path best, Path path) throws IOException {
        TopologySearchMetrics m = result.metrics();
        String text = "REM Framework Geometry Discovery topology search\n"
                + "mode=" + result.settings().searchMode() + "\nseed=" + result.settings().randomSeed()
                + "\nscoringProfile=" + result.settings().scoringProfile()
                + "\ninitialScore=" + format(result.initialCandidate().score().total())
                + "\nbestScore=" + format(result.bestCandidate().score().total())
                + "\nacceptedMutations=" + m.acceptedMutationCount()
                + "\nrejectedMutations=" + m.rejectedMutationCount()
                + "\ninvalidMutations=" + m.invalidMutationCount()
                + "\nduplicateTopologies=" + m.duplicateTopologyCount()
                + "\nrelaxationFailures=" + m.relaxationFailureCount()
                + "\nelapsedMilliseconds=" + m.elapsedMilliseconds()
                + "\nbestCandidate=" + best + "\n";
        StringBuilder summary = new StringBuilder(text);
        summary.append("\nscoreExplanation:\n");
        summary.append(new GeometryDiagnosticReport().format(
                result.bestCandidate(), result.settings().scoringProfile()));
        Files.writeString(path, summary, StandardCharsets.UTF_8);
    }

    private void writeCheckpoint(TopologySearchResult result, Path path) throws IOException {
        String text = "# Topology-search checkpoint (summary checkpoint; PRNG state is not restored)\n"
                + "formatVersion = \"0.2\"\niteration = " + result.metrics().completedIterations()
                + "\nseed = " + result.settings().randomSeed() + "\nsearchMode = \""
                + result.settings().searchMode() + "\"\nscoringProfile = \""
                + result.settings().scoringProfile() + "\"\ncurrentCandidateId = "
                + result.currentCandidate().candidateId() + "\nbestCandidateId = "
                + result.bestCandidate().candidateId() + "\nbestScore = "
                + format(result.bestCandidate().score().total()) + "\n";
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void rejectTemplate(Path path) {
        if (path.getFileName() != null
                && path.getFileName().toString().equalsIgnoreCase("cubic-lattice.toml"))
            throw new IllegalArgumentException("Topology search never overwrites cubic-lattice.toml.");
    }
    private static void quoted(StringBuilder out, String key, String value) {
        out.append(key).append(" = \"").append(value.replace("\\", "\\\\")
                .replace("\"", "\\\"")).append("\"\n");
    }
    private static void number(StringBuilder out, String key, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite topology value.");
        out.append(key).append(" = ").append(format(value)).append('\n');
    }
    private static String format(double value) {
        return String.format(Locale.ROOT, "%.17g", value)
                .replaceFirst("e([+-])0+([0-9])", "e$1$2");
    }
}
