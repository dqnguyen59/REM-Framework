package remframework.geometry.discovery;

public record GeometryConfigurationMetrics(long configurationsExamined,
        int uniqueConfigurationsRetained, long isomorphicDuplicatesRemoved,
        int symmetricCount, int asymmetricCount, long elapsedMilliseconds,
        boolean enumerationComplete) { }
