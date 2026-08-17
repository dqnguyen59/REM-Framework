package remframework.opengl.model;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.OccupancyHistorySnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OccupancyHistoryLifecycleTest {
    @Test
    void firstCycleFillsAllFixedPhaseSlots() {
        Occupancy occupancy = new Occupancy();
        for (int phase = 0; phase < 360; phase++)
            occupancy.saveGradientToHistory(new Vector3d(phase, 0, 0));

        OccupancyHistorySnapshot snapshot = occupancy.getHistorySnapshotForAudit();
        assertEquals(360, snapshot.capacity());
        assertEquals(360, snapshot.samples().size());
        assertEquals(360, snapshot.populatedSlots());
        assertEquals(0, snapshot.phaseIndex());
        assertEquals(360, snapshot.configuredSamplesPerCycle());
        assertEquals(360, snapshot.cumulativeSampleCount());
        assertEquals(359, snapshot.samples().get(359).x, 1e-12);
    }

    @Test
    void afterWrapHistoryRemainsFixedAndNewValuesUseMatchingPhaseSlots() {
        Occupancy occupancy = new Occupancy();
        for (int phase = 0; phase < 360 + 23; phase++)
            occupancy.saveGradientToHistory(new Vector3d(phase, 0, 0));

        OccupancyHistorySnapshot snapshot = occupancy.getHistorySnapshotForAudit();
        assertEquals(360, snapshot.capacity());
        assertEquals(360, snapshot.samples().size());
        assertEquals(360, snapshot.populatedSlots());
        assertEquals(23, snapshot.phaseIndex());
        assertEquals(360, snapshot.configuredSamplesPerCycle());
        assertEquals(383, snapshot.cumulativeSampleCount());
        assertEquals(360, snapshot.samples().get(0).x, 1e-12);
        assertEquals(359, snapshot.samples().get(359).x, 1e-12);
    }

    @Test
    void phaseIndexWrapsFromLastSlotToZeroWithoutChangingDomain() {
        Occupancy occupancy = new Occupancy();
        for (int phase = 0; phase < 359; phase++) occupancy.saveGradientToHistory(new Vector3d());
        assertEquals(359, occupancy.getHistoryPhaseIndexForAudit());
        occupancy.saveGradientToHistory(new Vector3d());
        assertEquals(0, occupancy.getHistoryPhaseIndexForAudit());
        assertEquals(360, occupancy.getHistorySnapshotForAudit().capacity());
    }
}
