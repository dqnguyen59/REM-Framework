package remframework.opengl.model.entity;

import org.joml.Vector3d;

import java.util.List;

/** Immutable diagnostic view of one Occupancy history state. */
public final class OccupancyHistorySnapshot {
    private final int capacity;
    private final List<Vector3d> samples;
    private final int populatedSlots;
    private final int phaseIndex;
    private final long completedCycleCount;
    private final long cumulativeSampleCount;
    private final int configuredSamplesPerCycle;

    public OccupancyHistorySnapshot(int capacity, List<Vector3d> samples, int populatedSlots,
                                    int phaseIndex, long completedCycleCount,
                                    long cumulativeSampleCount, int configuredSamplesPerCycle) {
        this.capacity = capacity;
        this.samples = samples.stream().map(Vector3d::new).toList();
        this.populatedSlots = populatedSlots;
        this.phaseIndex = phaseIndex;
        this.completedCycleCount = completedCycleCount;
        this.cumulativeSampleCount = cumulativeSampleCount;
        this.configuredSamplesPerCycle = configuredSamplesPerCycle;
    }

    public int capacity() { return capacity; }
    public List<Vector3d> samples() { return samples.stream().map(Vector3d::new).toList(); }
    public int populatedSlots() { return populatedSlots; }
    public int phaseIndex() { return phaseIndex; }
    public long completedCycleCount() { return completedCycleCount; }
    public long cumulativeSampleCount() { return cumulativeSampleCount; }
    public int configuredSamplesPerCycle() { return configuredSamplesPerCycle; }
}
