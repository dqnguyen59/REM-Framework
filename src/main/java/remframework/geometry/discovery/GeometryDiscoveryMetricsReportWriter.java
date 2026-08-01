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

public final class GeometryDiscoveryMetricsReportWriter {
    public Path write(GeometryDiscoveryResult result, Path requested) throws IOException {
        Path target = safeUniquePath(requested.toAbsolutePath().normalize());
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        GeometryDiscoveryMetrics m = result.metrics();
        String report = """
                REM Framework Geometry Discovery Metrics Report
                model=%s
                targetDistance=%s
                learningRate=%s
                repulsionStrength=%s
                maximumCorrection=%s
                randomSeed=%d
                converged=%s
                iterations=%d
                totalEdgeLengthError=%s
                meanConnectedNeighborDistance=%s
                minimumConnectedNeighborDistance=%s
                maximumConnectedNeighborDistance=%s
                standardDeviationConnectedNeighborDistance=%s
                minimumDistinctNodeDistance=%s
                collisionViolationCount=%d
                connectedComponentCount=%d
                extentX=%s
                extentY=%s
                extentZ=%s
                effectiveDimensionality=%s
                runtimeCompatible=%s
                runtimeCompatibilityReason=%s
                """.formatted(result.model().name(), result.settings().targetDistance(),
                result.settings().learningRate(), result.settings().repulsionStrength(),
                result.settings().maximumCorrection(), result.settings().randomSeed(),
                m.converged(), m.iterationCount(), m.totalEdgeLengthError(),
                m.meanConnectedNeighborDistance(), m.minimumConnectedNeighborDistance(),
                m.maximumConnectedNeighborDistance(),
                m.standardDeviationConnectedNeighborDistance(),
                m.minimumDistinctNodeDistance(), m.collisionViolationCount(),
                m.connectedComponentCount(), m.extentX(), m.extentY(), m.extentZ(),
                m.effectiveDimensionality(), result.runtimeCompatibility().compatible(),
                result.runtimeCompatibility().reason());
        Files.writeString(target, report, StandardCharsets.UTF_8);
        return target;
    }

    static Path safeUniquePath(Path requested) {
        if (requested.getFileName() != null
                && requested.getFileName().toString().equalsIgnoreCase("cubic-lattice.toml"))
            throw new IllegalArgumentException("Reports must never overwrite cubic-lattice.toml.");
        if (Files.notExists(requested)) return requested;
        String name = requested.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        for (int suffix = 2; ; suffix++) {
            Path candidate = requested.resolveSibling(base + "-" + suffix + extension);
            if (Files.notExists(candidate)) return candidate;
        }
    }
}
