package remframework.measurement.compact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class R096DirectionalCsvTest {
    @Test
    void writesDirectionAndGlobalMeansSeparately() {
        assertTrue(R096DirectionalCsv.header().contains("directionCycleMean,globalDirectionalMean,normalizedQ"));
        String row = R096DirectionalCsv.row("R01", "(1,0,0)", 120, 120, 0, 0, 2.0, 4.0, .5, "VALID");
        String[] fields = row.split(",(?=\"|[^\"]*$)");
        assertTrue(row.contains(",2.0000000000000000,4.0000000000000000,0.50000000000000000,VALID"));
    }
}
