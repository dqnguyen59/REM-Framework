package remframework.measurement.compact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscillatorPolarizationReportingTest {
    private static final Path PERSISTED = Path.of("measurements/oscillator-polarization-phase4");
    private static final double[][] EXPECTED_Q = {
            {-7.5901307103369380e-11, 3.1523869978259208e-09, 0.0038497570165710376,
                    0.0038497570165710376, 0.0038497570165710376, 0.0038497570165710376,
                    0.0038497570197631487, -0.00021651595833790087, 2.4968715547720990,
                    -0.00021651595833790087, 2.4968715547720990, 2.4968286059390015,
                    2.4968710207604725, 2.4968710207604725, 2.4968704867499990},
            {2.0847262327033094e-09, -1.0335237683330170e-10, 0.0024611757100420685,
                    0.0024611757100420685, 0.0024611757100420685, 0.0024611757113169744,
                    0.0024611757100420685, 0.0052186657645659764, 2.4962249187894560,
                    0.0052186657645659764, 2.4962249187894560, 2.4962260826504100,
                    2.4961890143594270, 2.4961890143594270, 2.4962028389898330},
            {7.2002556051218530e-10, 3.2327862309748476e-09, 0.0049397042983574950,
                    0.0049397042983574950, 0.0049397042983574950, 0.0049397042987512410,
                    0.0049397042983574950, 0.0017705839452070370, 2.4952922134842113,
                    0.0017705839452070370, 2.4952922134842113, 2.4952423634027830,
                    2.4953064324516950, 2.4953064324516950, 2.4953206513899953}
    };
    private static final double[][] EXPECTED_METRICS = {
            {1.4968715547720990, 1.1974912325005524, 1.2221853220803358},
            {1.4962260826504101, 1.1969675717250678, 1.2216506567249479},
            {1.4953206513899953, 1.1962347075552790, 1.2209030189091072}
    };

    @TempDir
    Path temporary;

    @Test
    void regeneratesSelfDescribingOutputsWithoutChangingMeasurements() throws Exception {
        for (String orientation : List.of("O1", "O2", "O3")) {
            Path target = temporary.resolve(orientation);
            Files.createDirectories(target);
            for (String file : List.of("metadata.csv", "directional-results.csv", "cycle-results.csv")) {
                Files.copy(PERSISTED.resolve(orientation).resolve(file), target.resolve(file));
            }
        }

        OscillatorPolarizationRunner.finalizeOutputs(temporary);

        Csv longFormat = readCsv(temporary.resolve("oscillator-directional-results.csv"));
        assertEquals(45, longFormat.rows.size());
        assertEquals(3, longFormat.rows.stream().map(row -> row.get("orientationId")).distinct().count());
        for (String orientation : List.of("O1", "O2", "O3")) {
            assertEquals(15, longFormat.rows.stream()
                    .filter(row -> orientation.equals(row.get("orientationId"))).count());
        }

        Map<String, String> o2r09 = row(longFormat, "O2", "R09");
        Map<String, String> o3r09 = row(longFormat, "O3", "R09");
        assertVector(o2r09, "oscillatorRaw", 1, -1, 0);
        assertVector(o3r09, "oscillatorRaw", 1, 1, -2);
        assertVector(o2r09, "direction", 1, 1, 1);
        assertVector(o3r09, "direction", 1, 1, 1);
        assertEquals(0, number(o2r09, "oscillatorDetectorDotProduct"), 1e-15);
        assertEquals(0, number(o3r09, "oscillatorDetectorDotProduct"), 1e-15);
        assertEquals(90, number(o2r09, "oscillatorDetectorAngleDegrees"), 1e-12);
        assertEquals(90, number(o3r09, "oscillatorDetectorAngleDegrees"), 1e-12);

        for (int orientation = 0; orientation < 3; orientation++) {
            for (int direction = 0; direction < 15; direction++) {
                Map<String, String> measured = row(longFormat, "O" + (orientation + 1),
                        String.format("R%02d", direction + 1));
                assertEquals(EXPECTED_Q[orientation][direction], number(measured, "normalizedQ"), 0);
            }
        }

        Csv summary = readCsv(temporary.resolve("oscillator-orientation-summary.csv"));
        assertTrue(summary.header.contains("Q_R09_1_1_1"));
        for (int orientation = 0; orientation < 3; orientation++) {
            Map<String, String> measured = summary.rows.get(orientation);
            assertEquals(EXPECTED_METRICS[orientation][0], number(measured, "Emax"), 0);
            assertEquals(EXPECTED_METRICS[orientation][1], number(measured, "MAD"), 0);
            assertEquals(EXPECTED_METRICS[orientation][2], number(measured, "StdDev"), 0);
        }

        for (String orientation : List.of("O1", "O2", "O3")) {
            assertEquals(15, readCsv(temporary.resolve(orientation).resolve("directional-results.csv")).rows.size());
            assertEquals(90, readCsv(temporary.resolve(orientation).resolve("cycle-results.csv")).rows.size());
        }
        assertTrue(Files.readString(temporary.resolve("CONFIGURATION-STATUS.md"))
                .contains("measurement data is unchanged"));
    }

    private static void assertVector(Map<String, String> row, String prefix, double x, double y, double z) {
        assertEquals(x, number(row, prefix + "X"), 0);
        assertEquals(y, number(row, prefix + "Y"), 0);
        assertEquals(z, number(row, prefix + "Z"), 0);
    }

    private static Map<String, String> row(Csv csv, String orientation, String direction) {
        return csv.rows.stream().filter(value -> orientation.equals(value.get("orientationId"))
                        && direction.equals(value.get("directionId"))).findFirst().orElseThrow();
    }

    private static double number(Map<String, String> row, String column) {
        return Double.parseDouble(row.get(column));
    }

    private static Csv readCsv(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path);
        List<String> header = List.of(lines.getFirst().split(",", -1));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.split(",", -1);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), values[i]);
            rows.add(row);
        }
        return new Csv(header, rows);
    }

    private record Csv(List<String> header, List<Map<String, String>> rows) { }
}
