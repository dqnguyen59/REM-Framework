package remframework.measurement.detector;

import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtendedIdentityShapeTest {
    @Test void membershipIsInvariantUnderSignsAndPermutations() {
        Vector3i value = new Vector3i(7, -4, 2);
        boolean expected = ExtendedIdentityShape.contains(value, 96, .75, 1.25);
        assertEquals(expected, ExtendedIdentityShape.contains(new Vector3i(-7, 4, -2), 96, .75, 1.25));
        assertEquals(expected, ExtendedIdentityShape.contains(new Vector3i(2, 7, -4), 96, .75, 1.25));
        assertEquals(expected, ExtendedIdentityShape.contains(new Vector3i(-4, -2, 7), 96, .75, 1.25));
    }

    @Test void selectedTermsSeparateAxisFaceAndBodyClasses() {
        assertEquals(0, ExtendedIdentityShape.score(1, 0, 0, 1, 1)
                - ExtendedIdentityShape.score(1, 0, 0, 0, 0));
        assertEquals(1, ExtendedIdentityShape.score(1, 1, 0, 1, 0)
                - ExtendedIdentityShape.score(1, 1, 0, 0, 0));
        assertEquals(0, ExtendedIdentityShape.score(1, 1, 1, 1, 0)
                - ExtendedIdentityShape.score(1, 1, 1, 0, 0));
        assertEquals(1, ExtendedIdentityShape.score(1, 1, 1, 0, 1)
                - ExtendedIdentityShape.score(1, 1, 1, 0, 0));
    }

    @Test void centerIsAlwaysOccupied() {
        assertTrue(ExtendedIdentityShape.contains(new Vector3i(0, 0, 0), 96, 0, 0));
        assertTrue(ExtendedIdentityShape.contains(new Vector3i(0, 0, 0), 96, 2, 2));
    }

    @Test void zeroParametersAreExactlyTheOrdinaryL1Predicate() {
        for (int x = -6; x <= 6; x++) for (int y = -6; y <= 6; y++) for (int z = -6; z <= 6; z++) {
            boolean ordinary = Math.abs(x) + Math.abs(y) + Math.abs(z) < 7;
            assertEquals(ordinary, ExtendedIdentityShape.contains(new Vector3i(x, y, z), 7, 0, 0));
        }
    }
}
