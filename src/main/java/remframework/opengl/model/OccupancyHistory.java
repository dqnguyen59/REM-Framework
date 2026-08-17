package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.OccupancyHistorySnapshot;

import java.util.ArrayList;
import java.util.List;

/** Read-only, presentation-oriented views of Occupancy gradient history. */
public final class OccupancyHistory {
    private OccupancyHistory() { }

    public record Series(String label, Vector3i coordinate, double weight,
                         List<Vector3d> samples) {
        public Series {
            coordinate = new Vector3i(coordinate);
            samples = samples.stream().map(Vector3d::new).toList();
        }

        @Override public Vector3i coordinate() { return new Vector3i(coordinate); }
        @Override public List<Vector3d> samples() {
            return samples.stream().map(Vector3d::new).toList();
        }
    }

    /** Computes one interpolated vector for every sample using the probe weights. */
    public static List<Vector3d> interpolate(List<List<Vector3d>> sourceSamples,
                                              List<Double> weights) {
        if (sourceSamples.size() != weights.size())
            throw new IllegalArgumentException("source sample and weight counts differ");
        int length = sourceSamples.isEmpty() ? 0 : sourceSamples.getFirst().size();
        for (List<Vector3d> source : sourceSamples)
            if (source.size() != length) throw new IllegalArgumentException("source history lengths differ");
        List<Vector3d> result = new ArrayList<>(length);
        for (int sample = 0; sample < length; sample++) {
            Vector3d value = new Vector3d();
            for (int source = 0; source < sourceSamples.size(); source++)
                value.fma(weights.get(source), sourceSamples.get(source).get(sample));
            result.add(value);
        }
        return List.copyOf(result);
    }

    /** Selects raw history or the four source histories plus a weighted estimate. */
    public static List<Series> forProbe(PassiveObserver probe) {
        if (probe instanceof Probe raw) {
            Occupancy occupancy = raw.getOccupancies().getFirst();
            OccupancyHistorySnapshot snapshot = occupancy.getHistorySnapshotForAudit();
            return List.of(new Series("Raw node", raw.getNodeCoordinate(), 1.0,
                    snapshot.samples()));
        }
        if (!(probe instanceof InterpolatedProbe interpolated)) return List.of();
        List<Series> sources = new ArrayList<>();
        List<List<Vector3d>> histories = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (int index = 0; index < interpolated.getOccupancies().size(); index++) {
            var source = interpolated.getDefinition().getInterpolationSources().get(index);
            OccupancyHistorySnapshot snapshot = interpolated.getOccupancies().get(index)
                    .getHistorySnapshotForAudit();
            List<Vector3d> history = snapshot.samples();
            sources.add(new Series("Source " + (index + 1), source.nodeIndex(), source.weight(), history));
            histories.add(history);
            weights.add(source.weight());
        }
        sources.add(new Series("Interpolated estimate", new Vector3i(), Double.NaN,
                interpolate(histories, weights)));
        return List.copyOf(sources);
    }

    public static boolean showPhaseReferenceMarkers(int historyLength) {
        return historyLength == 360;
    }
}
