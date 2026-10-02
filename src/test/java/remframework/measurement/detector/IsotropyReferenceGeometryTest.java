package remframework.measurement.detector;

import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IsotropyReferenceGeometryTest {
    @Test void canonicalGeometryPassesAllExactAssertions() {
        IsotropyReferenceGeometry.validate();
        assertEquals(15, IsotropyReferenceGeometry.RAYS.size());
        assertEquals(105, IsotropyReferenceGeometry.entries().size());
        assertEquals(297, IsotropyReferenceGeometry.minimumSymmetricDimension());
    }

    @Test void everyConfiguredRayIsPrimitiveAndEveryShellDividesExactly() {
        for (Vector3i ray : IsotropyReferenceGeometry.RAYS) {
            assertTrue(IsotropyReferenceGeometry.primitive(ray));
            for (int shell : IsotropyReferenceGeometry.SHELLS)
                assertEquals(0, shell % IsotropyReferenceGeometry.graphLength(ray));
        }
    }

    @Test void everyDetectorIsExactRayAndExactGraphDistanceWithoutDuplicates() {
        Set<String> nodes = new HashSet<>();
        for (var e : IsotropyReferenceGeometry.entries()) {
            assertEquals(new Vector3i(e.primitive()).mul(e.multiplier()), e.relative());
            assertEquals(e.graphShell(), IsotropyReferenceGeometry.graphLength(e.relative()));
            assertTrue(nodes.add(e.graphShell() + ":" + e.relative()));
        }
    }

    @Test void revisedWindowExcludesFiveWarmupCyclesAndIncludesSixCycles() {
        assertEquals(5, IsotropyReferenceGeometry.WARMUP_CYCLES);
        assertEquals(6, IsotropyReferenceGeometry.MEASUREMENT_FIRST_CYCLE);
        assertEquals(11, IsotropyReferenceGeometry.MEASUREMENT_LAST_CYCLE);
        assertEquals(198, IsotropyReferenceGeometry.MEASUREMENT_END_UPDATE);
    }
}
