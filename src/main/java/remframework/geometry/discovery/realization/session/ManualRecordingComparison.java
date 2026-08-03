package remframework.geometry.discovery.realization.session;

import java.util.*;

public final class ManualRecordingComparison {
    private ManualRecordingComparison() { }
    public record Row(String recordingName,long seed,int actions,int iterations,boolean qualified,
            double worstRatio,Double volume,long runtimeMillis,String configurationStatus) { }
    public static List<Row> order(List<Row> rows){return rows.stream().sorted(Comparator.comparing(Row::qualified).reversed().thenComparingDouble(Row::worstRatio).thenComparing(Row::recordingName).thenComparingLong(Row::seed)).toList();}
}
