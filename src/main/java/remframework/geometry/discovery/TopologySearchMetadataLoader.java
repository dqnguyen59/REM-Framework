package remframework.geometry.discovery;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;

public final class TopologySearchMetadataLoader {
    public TopologySearchMetadata load(Path path) throws IOException {
        TomlParseResult toml = Toml.parse(path);
        if (!toml.contains("topologySearch.totalScore")) return null;
        return new TopologySearchMetadata(decimal(toml, "topologySearch.totalScore"),
                string(toml, "topologySearch.searchMode"), integer(toml, "topologySearch.searchSeed"),
                string(toml, "topologySearch.parentMutation"),
                decimal(toml, "topologySearch.scoreComponents.edgeLengthError"),
                decimal(toml, "topologySearch.scoreComponents.directionalImbalancePenalty"),
                decimal(toml, "topologySearch.scoreComponents.tensorAnisotropyPenalty"),
                decimal(toml, "topologySearch.scoreComponents.degreeVariancePenalty"),
                (int) integer(toml, "topologySearch.metrics.minimumDegree"),
                (int) integer(toml, "topologySearch.metrics.maximumDegree"),
                decimal(toml, "topologySearch.metrics.meanDegree"),
                (int) integer(toml, "topologySearch.metrics.graphDiameter"),
                (int) integer(toml, "topologySearch.metrics.bridgeEdgeCount"),
                (int) integer(toml, "topologySearch.metrics.articulationPointCount"),
                decimal(toml, "topologySearch.directionalBalance.meanLocalImbalance"),
                decimal(toml, "topologySearch.directionalBalance.meanTensorAnisotropy"));
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
    private static String string(TomlParseResult t, String key) {
        String value = t.getString(key);
        return value == null ? "" : value;
    }
}
