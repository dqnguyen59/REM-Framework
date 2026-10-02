package remframework.measurement.compact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.settings.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XyzOscillatorVerificationTest {
    @TempDir
    Path output;

    @Test
    void verifiesTwoCyclesCoherentExtendedSourceAndZRegression() throws Exception {
        try {
            XyzOscillatorVerificationRunner.main(new String[]{
                    "samples/isotropy-reference-v2-XYZ-in-phase.qfs",
                    "samples/isotropy-reference-v2-Z.qfs",
                    output.toString()});

            List<String> cycle = Files.readAllLines(output.resolve("oscillator-cycle.csv"));
            assertEquals(37, cycle.size());
            for (int update = 1; update <= 36; update++) {
                String[] row = cycle.get(update).split(",");
                assertEquals(update, Integer.parseInt(row[0]));
                assertEquals("true", row[1]);
                assertEquals("true", row[2]);
                assertEquals("true", row[3]);
                double expectedPhase = update * 20.0 % 360.0;
                assertEquals(expectedPhase, Double.parseDouble(row[4]), 0);
                assertEquals(expectedPhase, Double.parseDouble(row[5]), 0);
                assertEquals(expectedPhase, Double.parseDouble(row[6]), 0);
                double x = Double.parseDouble(row[19]);
                double y = Double.parseDouble(row[20]);
                double z = Double.parseDouble(row[21]);
                double magnitude = Double.parseDouble(row[22]);
                assertEquals(x, y, 0);
                assertEquals(x, z, 0);
                assertEquals(Math.sqrt(x * x + y * y + z * z), magnitude, 1e-12);
                assertEquals(Math.sqrt(3) * Math.abs(x), magnitude, 1e-12);
            }

            List<String> source = Files.readAllLines(output.resolve("source-force-audit.csv"));
            assertEquals(145, source.size());
            for (String line : source.subList(1, source.size())) {
                String[] row = line.split(",");
                assertEquals(0, Double.parseDouble(row[12]), 0);
                assertEquals(Double.parseDouble(row[6]), Double.parseDouble(row[9]), 0);
                assertEquals(Double.parseDouble(row[7]), Double.parseDouble(row[10]), 0);
                assertEquals(Double.parseDouble(row[8]), Double.parseDouble(row[11]), 0);
            }
            assertEquals(4, source.stream().skip(1).map(line -> line.split(",")[1]).distinct().count());

            String status = Files.readString(output.resolve("CONFIGURATION-STATUS.md"));
            String report = Files.readString(output.resolve("report.md"));
            assertTrue(status.contains("VALID"));
            assertTrue(status.contains("IN_PHASE_BODY_DIAGONAL_CONTROL"));
            assertTrue(report.contains("unindented R=4 Extended Identity"));
            assertTrue(report.contains("matched the existing `(0,0,excitation.z)` path exactly"));
            assertFalse(report.contains("Emax="));
        } finally {
            Project.getInstance().occupancies.clear();
        }
    }
}
