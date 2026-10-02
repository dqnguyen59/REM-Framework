package remframework.measurement.compact;

import remframework.measurement.detector.IsotropyReferenceGeometry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Repairs detector geometry reports without rerunning completed measurements. */
public final class R096GeometryRepair {
    private R096GeometryRepair() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: R096GeometryRepair <output-directory>");
        Path root = Path.of(args[0]);
        try (var stream = Files.list(root)) {
            for (Path dir : stream.filter(p -> p.getFileName().toString().matches("i\\d{6}")).toList()) {
                if (Files.exists(dir.resolve("metadata.csv"))) repair(dir);
            }
        }
    }

    private static void repair(Path dir) throws IOException {
        Properties metadata = properties(dir.resolve("metadata.csv"));
        int shell = Integer.parseInt(metadata.getProperty("detectorShell"));
        String sourceLine = Files.readAllLines(dir.resolve("configuration.properties")).stream().filter(line -> line.startsWith("sourceNode=")).findFirst().orElseThrow();
        String[] center = sourceLine.substring("sourceNode=".length()).split(",");
        int cx = Integer.parseInt(center[0]), cy = Integer.parseInt(center[1]), cz = Integer.parseInt(center[2]);
        List<int[]> points = points(dir.resolve("shape-nodes.csv"));
        try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("detector-geometry.csv"))) {
            writer.write("directionId,primitiveDirection,detectorShell,detectorRelativeX,detectorRelativeY,detectorRelativeZ,minimumGraphDistanceToOccupiedRegion,detectorOccupied\n");
            for (int i = 0; i < 15; i++) {
                var ray = IsotropyReferenceGeometry.RAYS.get(i);
                int multiplier = shell / IsotropyReferenceGeometry.graphLength(ray);
                int x = ray.x * multiplier, y = ray.y * multiplier, z = ray.z * multiplier;
                int gap = minimumDistance(x, y, z, points);
                writer.write(String.format(Locale.ROOT, "R%02d,\"(%d,%d,%d)\",%d,%d,%d,%d,%d,false%n", i + 1, ray.x, ray.y, ray.z, shell, x, y, z, gap));
            }
        }
    }

    private static Properties properties(Path file) throws IOException {
        Properties properties = new Properties();
        for (String line : Files.readAllLines(file).subList(1, Files.readAllLines(file).size())) {
            int comma = line.indexOf(',');
            if (comma > 0) properties.setProperty(line.substring(0, comma), line.substring(comma + 1));
        }
        return properties;
    }

    private static List<int[]> points(Path file) throws IOException {
        return Files.readAllLines(file).subList(1, Files.readAllLines(file).size()).stream().map(line -> {
            String[] values = line.split(",");
            return new int[]{Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])};
        }).toList();
    }

    private static int minimumDistance(int x, int y, int z, List<int[]> points) {
        int minimum = Integer.MAX_VALUE;
        for (int[] point : points) minimum = Math.min(minimum, Math.abs(x - point[0]) + Math.abs(y - point[1]) + Math.abs(z - point[2]));
        return minimum;
    }
}
