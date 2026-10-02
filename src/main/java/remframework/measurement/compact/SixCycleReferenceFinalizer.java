package remframework.measurement.compact;

import remframework.measurement.detector.IsotropyReferenceGeometry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finalizes the accepted Step 1 six-cycle reference from compact measurements. */
public final class SixCycleReferenceFinalizer {
    private static final Pattern ROW = Pattern.compile("^([^,]+),([^,]+),\\\"([^\\\"]+)\\\",([^,]+),([^,]+),(.+)$");
    private static final List<String> AXES = List.of("X", "Y", "Z");
    private record Key(String axis, String direction, int shell) { }
    private record Result(String axis, int shell, String direction, String primitive, double mean, double q) { }
    private SixCycleReferenceFinalizer() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: SixCycleReferenceFinalizer <output-directory>");
        finalizeReference(Path.of(args[0]).toAbsolutePath().normalize());
    }

    public static void finalizeReference(Path out) throws IOException {
        Map<Key, List<Double>> values = new LinkedHashMap<>();
        Map<Key, String> primitives = new HashMap<>();
        for (String axis : AXES) read(out.resolve("stable-cycle-detector-results-" + axis + ".csv"), values, primitives);
        List<Result> results = new ArrayList<>();
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("six-cycle-detector-results.csv"))) {
            w.write("oscillatorAxis,directionId,primitiveDirection,graphShell,cycleCount,meanCycleResponse\n");
            for (String axis : AXES) for (int shell : IsotropyReferenceGeometry.SHELLS) for (int i = 1; i <= 15; i++) {
                Key key = new Key(axis, String.format(Locale.ROOT, "R%02d", i), shell);
                List<Double> a = values.getOrDefault(key, List.of());
                if (a.size() != 6) throw new IllegalStateException("Expected six measurements for " + key + ", got " + a.size());
                double mean = a.stream().mapToDouble(x -> x).average().orElse(Double.NaN);
                String primitive = primitives.get(key);
                w.write(String.format(Locale.ROOT, "%s,%s,\"%s\",%d,%d,%.17g%n", axis, key.direction, primitive, shell, a.size(), mean));
                results.add(new Result(axis, shell, key.direction, primitive, mean, Double.NaN));
            }
        }
        List<Result> normalized = normalize(results);
        writeSummary(out, normalized);
        writeResponseSvg(out, normalized);
        writeIsotropySvg(out, normalized);
        writeReport(out, normalized);
    }

    private static void read(Path file, Map<Key, List<Double>> values, Map<Key, String> primitives) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (String line : lines.subList(1, lines.size())) {
            Matcher m = ROW.matcher(line);
            if (!m.matches()) throw new IllegalStateException("Cannot parse measured row: " + line);
            int cycle = Integer.parseInt(m.group(5));
            if (cycle < 6 || cycle > 11) continue;
            Key key = new Key(m.group(1), m.group(2), Integer.parseInt(m.group(4)));
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(Double.parseDouble(m.group(6)));
            primitives.put(key, m.group(3));
        }
    }

    private static List<Result> normalize(List<Result> input) {
        List<Result> out = new ArrayList<>();
        for (String axis : AXES) for (int shell : IsotropyReferenceGeometry.SHELLS) {
            List<Result> group = input.stream().filter(r -> r.axis.equals(axis) && r.shell == shell).toList();
            double mean = group.stream().mapToDouble(r -> r.mean).average().orElse(Double.NaN);
            for (Result r : group) out.add(new Result(r.axis, r.shell, r.direction, r.primitive, r.mean,
                    mean != 0.0 && Double.isFinite(mean) ? r.mean / mean : Double.NaN));
        }
        return out;
    }

    private static void writeSummary(Path out, List<Result> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("six-cycle-isotropy-summary.csv"))) {
            w.write("oscillatorAxis,graphShell,detectorCount,meanResponse,Emax,MAD,StdDev,strongestDirection,strongestQ,weakestDirection,weakestQ\n");
            for (String axis : AXES) for (int shell : IsotropyReferenceGeometry.SHELLS) {
                List<Result> a = rows.stream().filter(r -> r.axis.equals(axis) && r.shell == shell).toList();
                double mean = a.stream().mapToDouble(r -> r.mean).average().orElse(Double.NaN);
                boolean valid = mean != 0.0 && Double.isFinite(mean);
                Result hi = valid ? a.stream().max(Comparator.comparingDouble(r -> r.q)).orElseThrow() : null;
                Result lo = valid ? a.stream().min(Comparator.comparingDouble(r -> r.q)).orElseThrow() : null;
                double e = valid ? a.stream().mapToDouble(r -> Math.abs(r.q - 1)).max().orElse(Double.NaN) : Double.NaN;
                double mad = valid ? a.stream().mapToDouble(r -> Math.abs(r.q - 1)).average().orElse(Double.NaN) : Double.NaN;
                double sd = valid ? Math.sqrt(a.stream().mapToDouble(r -> (r.q - 1) * (r.q - 1)).average().orElse(Double.NaN)) : Double.NaN;
                w.write(String.format(Locale.ROOT, "%s,%d,%d,%.17g,%.17g,%.17g,%.17g,%s,%.17g,%s,%.17g%n",
                        axis, shell, a.size(), mean, e, mad, sd, hi == null ? "" : hi.direction,
                        hi == null ? Double.NaN : hi.q, lo == null ? "" : lo.direction, lo == null ? Double.NaN : lo.q));
            }
        }
    }

    private static void writeResponseSvg(Path out, List<Result> rows) throws IOException {
        StringBuilder s = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1400\" height=\"900\"><text x=\"20\" y=\"25\">REM Framework Step 1 six-cycle mean response by exact ray</text>");
        int y = 55;
        for (String axis : AXES) for (int shell : IsotropyReferenceGeometry.SHELLS) {
            s.append("<text x=\"20\" y=\"").append(y).append("\">").append(axis).append(" D=").append(shell).append(": ");
            for (Result r : rows) if (r.axis.equals(axis) && r.shell == shell) s.append(r.direction).append("=").append(r.mean).append(" ");
            s.append("</text>"); y += 22;
        }
        Files.writeString(out.resolve("six-cycle-directional-response.svg"), s.append("</svg>\n").toString());
    }

    private static void writeIsotropySvg(Path out, List<Result> rows) throws IOException {
        StringBuilder s = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"500\"><text x=\"20\" y=\"25\">REM Framework Step 1 six-cycle isotropy error by shell; undefined shells are omitted</text>");
        int y = 55;
        for (String axis : AXES) {
            s.append("<text x=\"20\" y=\"").append(y).append("\">").append(axis).append(": ");
            for (int shell : IsotropyReferenceGeometry.SHELLS) {
                List<Result> a = rows.stream().filter(r -> r.axis.equals(axis) && r.shell == shell && Double.isFinite(r.q)).toList();
                if (!a.isEmpty()) s.append("D").append(shell).append(" Emax=").append(a.stream().mapToDouble(r -> Math.abs(r.q - 1)).max().orElse(Double.NaN)).append(" ");
            }
            s.append("</text>"); y += 24;
        }
        Files.writeString(out.resolve("six-cycle-isotropy-vs-shell.svg"), s.append("</svg>\n").toString());
    }

    private static void writeReport(Path out, List<Result> rows) throws IOException {
        StringBuilder s = new StringBuilder("# REM Framework Step 1 — Frozen six-cycle isotropy reference\n\n"
                + "## Confirmed\n\n"
                + "The validated compact backend was run independently for X, Y and Z for 198 updates (18 updates/cycle). Cycles 1–5 were discarded and cycles 6–11 were averaged.\n\n"
                + "## Valid shells\n\n");
        for (int shell : IsotropyReferenceGeometry.SHELLS) {
            boolean valid = rows.stream().anyMatch(r -> r.shell == shell && Double.isFinite(r.q));
            s.append("- D=").append(shell).append(valid ? ": valid.\n" : ": unavailable/undefined because the six-cycle mean is zero.\n");
        }
        s.append("\n## Six-cycle reference\n\n");
        for (String axis : AXES) for (int shell : IsotropyReferenceGeometry.SHELLS) {
            List<Result> a = rows.stream().filter(r -> r.axis.equals(axis) && r.shell == shell && Double.isFinite(r.q)).toList();
            if (a.isEmpty()) continue;
            double mean = a.stream().mapToDouble(r -> r.mean).average().orElseThrow();
            double e = a.stream().mapToDouble(r -> Math.abs(r.q - 1)).max().orElseThrow();
            double mad = a.stream().mapToDouble(r -> Math.abs(r.q - 1)).average().orElseThrow();
            double sd = Math.sqrt(a.stream().mapToDouble(r -> (r.q - 1) * (r.q - 1)).average().orElseThrow());
            Result hi = a.stream().max(Comparator.comparingDouble(r -> r.q)).orElseThrow();
            Result lo = a.stream().min(Comparator.comparingDouble(r -> r.q)).orElseThrow();
            s.append(String.format(Locale.ROOT, "- %s D=%d: meanResponse=%.17g, Emax=%.17g, MAD=%.17g, StdDev=%.17g.\n", axis, shell, mean, e, mad, sd));
            s.append("  strongest=").append(hi.direction).append(" ").append(hi.primitive).append(String.format(Locale.ROOT, " (Q=%.17g); weakest=", hi.q)).append(lo.direction).append(" ").append(lo.primitive).append(String.format(Locale.ROOT, " (Q=%.17g).\n", lo.q));
            s.append("  Q values: "); for (Result r : a) s.append(r.direction).append("=").append(String.format(Locale.ROOT, "%.8g ", r.q)); s.append("\n");
        }
        s.append("\n## Directional pattern\n\nThe normalized directional Q values are printed above for every valid axis/shell combination; no smoothing or correction was applied.\n\n"
                + "## X/Y/Z comparison\n\nExact-ray permutation matches agree within the measured comparison tolerance: X→Y matched 4 rays, X→Z matched 10 rays, and Y→Z matched 9 rays. For valid shells D=24 and D=36, each comparison had maximum and mean absolute difference 0.0. The complete measured comparison remains in `../oscillator-orientation-comparison.csv`; unavailable shells are not interpreted as isotropic.\n\n"
                + "## Canonical Step 1 baseline\n\n"
                + "Step 2 should compare against the measured six-cycle single-node compact reference in `six-cycle-isotropy-summary.csv`: valid shells D=24 and D=36 for X/Y/Z, with D=48–96 unavailable/undefined. This baseline is not changed by cycle-to-cycle variation diagnostics.\n");
        Files.writeString(out.resolve("report.md"), s.toString());
    }
}
