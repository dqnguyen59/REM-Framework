package remframework.geometry.discovery;

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
        text.append("candidateId = ").append(candidate.candidateId()).append('\n');
        if (candidate.parentCandidateId() != null)
            text.append("parentCandidateId = ").append(candidate.parentCandidateId()).append('\n');
        quoted(text, "parentMutation", candidate.mutation() == null ? "initial"
                : candidate.mutation().description());
        quoted(text, "searchMode", search.settings().searchMode().name());
        text.append("searchSeed = ").append(search.settings().randomSeed()).append('\n');
        number(text, "totalScore", candidate.score().total());
        text.append("\n[topologySearch.scoreComponents]\n");
        for (Map.Entry<String, Double> entry : candidate.score().components().entrySet())
            number(text, entry.getKey(), entry.getValue());
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
        number(text, "meanLocalImbalance", directional.meanLocalImbalance());
        number(text, "maximumLocalImbalance", directional.maximumLocalImbalance());
        number(text, "standardDeviationLocalImbalance",
                directional.standardDeviationLocalImbalance());
        text.append("worstNodeId = ").append(directional.worstNodeId()).append('\n');
        number(text, "meanTensorAnisotropy", directional.meanTensorAnisotropy());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private void writeHistory(TopologySearchResult result, Path path) throws IOException {
        StringBuilder out = new StringBuilder(
                "iteration,currentScore,bestScore,accepted,mutationType,temperature,elapsedMilliseconds\n");
        for (TopologySearchHistoryEntry row : result.history())
            out.append(row.iteration()).append(',').append(format(row.currentScore())).append(',')
                    .append(format(row.bestScore())).append(',').append(row.accepted()).append(',')
                    .append(row.mutationType()).append(',').append(format(row.temperature())).append(',')
                    .append(row.elapsedMilliseconds()).append('\n');
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private void writeScores(TopologySearchResult result, Path path) throws IOException {
        StringBuilder out = new StringBuilder("rank,candidateId,total");
        result.bestCandidate().score().components().keySet().forEach(k -> out.append(',').append(k));
        out.append('\n');
        int rank = 0;
        for (TopologyCandidate candidate : result.topCandidates()) {
            out.append(++rank).append(',').append(candidate.candidateId()).append(',')
                    .append(format(candidate.score().total()));
            candidate.score().components().values().forEach(v -> out.append(',').append(format(v)));
            out.append('\n');
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private void writeSummary(TopologySearchResult result, Path best, Path path) throws IOException {
        TopologySearchMetrics m = result.metrics();
        String text = "REM Framework Geometry Discovery topology search\n"
                + "mode=" + result.settings().searchMode() + "\nseed=" + result.settings().randomSeed()
                + "\ninitialScore=" + format(result.initialCandidate().score().total())
                + "\nbestScore=" + format(result.bestCandidate().score().total())
                + "\nacceptedMutations=" + m.acceptedMutationCount()
                + "\nrejectedMutations=" + m.rejectedMutationCount()
                + "\ninvalidMutations=" + m.invalidMutationCount()
                + "\nduplicateTopologies=" + m.duplicateTopologyCount()
                + "\nrelaxationFailures=" + m.relaxationFailureCount()
                + "\nelapsedMilliseconds=" + m.elapsedMilliseconds()
                + "\nbestCandidate=" + best + "\n";
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private void writeCheckpoint(TopologySearchResult result, Path path) throws IOException {
        String text = "# Topology-search checkpoint (summary checkpoint; PRNG state is not restored)\n"
                + "formatVersion = \"0.1\"\niteration = " + result.metrics().completedIterations()
                + "\nseed = " + result.settings().randomSeed() + "\nsearchMode = \""
                + result.settings().searchMode() + "\"\ncurrentCandidateId = "
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
