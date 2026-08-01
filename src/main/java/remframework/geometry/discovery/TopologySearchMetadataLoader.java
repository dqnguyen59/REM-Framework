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

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;

public final class TopologySearchMetadataLoader {
    public TopologySearchMetadata load(Path path) throws IOException {
        TomlParseResult toml = Toml.parse(path);
        if (!toml.contains("topologySearch.totalScore")) return null;
        boolean redesigned = toml.contains(
                "topologySearch.directionalBalance.meanNormalizedDirectionalBias");
        return new TopologySearchMetadata(
                string(toml, "topologySearch.metadataVersion", redesigned ? "0.2" : "0.1"),
                redesigned,
                decimal(toml, "topologySearch.totalScore"),
                string(toml, "topologySearch.searchMode", ""),
                string(toml, "topologySearch.scoringProfile", "UNAVAILABLE"),
                integer(toml, "topologySearch.searchSeed"),
                string(toml, "topologySearch.parentMutation", ""),
                decimal(toml, "topologySearch.scoreComponents.edgeLengthError"),
                decimal(toml, redesigned
                        ? "topologySearch.scoreComponents.directionalBias"
                        : "topologySearch.scoreComponents.directionalImbalancePenalty"),
                decimal(toml, redesigned
                        ? "topologySearch.scoreComponents.tensorIsotropy"
                        : "topologySearch.scoreComponents.tensorAnisotropyPenalty"),
                decimal(toml, "topologySearch.scoreComponents.degreeVariance"),
                (int) integer(toml, "topologySearch.metrics.minimumDegree"),
                (int) integer(toml, "topologySearch.metrics.maximumDegree"),
                decimal(toml, "topologySearch.metrics.meanDegree"),
                (int) integer(toml, "topologySearch.metrics.graphDiameter"),
                (int) integer(toml, "topologySearch.metrics.bridgeEdgeCount"),
                (int) integer(toml, "topologySearch.metrics.articulationPointCount"),
                decimal(toml, redesigned
                        ? "topologySearch.directionalBalance.meanNormalizedDirectionalBias"
                        : "topologySearch.directionalBalance.meanLocalImbalance"),
                decimal(toml, redesigned
                        ? "topologySearch.directionalBalance.meanTensorIsotropyError"
                        : "topologySearch.directionalBalance.meanTensorAnisotropy"),
                redesigned && integer(toml,
                        "topologySearch.directionalBalance.angularReferenceApplicableNodeCount") > 0,
                decimal(toml, "topologySearch.directionalBalance.meanKnownReferenceAngularError"),
                decimal(toml, "topologySearch.directionalBalance.maximumUncoveredAngle"),
                decimal(toml, "topologySearch.directionalBalance.meanOppositeDirectionMismatch"),
                (int) integer(toml, "topologySearch.directionalBalance.globalDimensionality"),
                (int) integer(toml,
                        "topologySearch.directionalBalance.localGlobalDimensionMismatchCount"));
    }

    private static double decimal(TomlParseResult t, String key) {
        Double value = t.getDouble(key);
        if (value != null) return value;
        Long integer = t.getLong(key);
        return integer == null ? 0 : integer;
    }
    private static long integer(TomlParseResult t, String key) {
        Long value = t.getLong(key);
        return value == null ? 0 : value;
    }
    private static String string(TomlParseResult t, String key, String fallback) {
        String value = t.getString(key);
        return value == null ? fallback : value;
    }
}
