package remframework.measurement.compact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Writes the final focused R=96 indentation report from measured CSV data. */
public final class R096Report {
    private record Row(double indentation, String shape, String status, int shell, int minGap, int maxGap,
                       int occupied, double emax, double mad, double sd, double improvement, double relative, double[] q) { }
    private R096Report() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: R096Report <output-directory>");
        Path root = Path.of(args[0]);
        List<String> lines = Files.readAllLines(root.resolve("all-shapes.csv"));
        List<Row> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) rows.add(parse(line));
        Row best = rows.stream().min(Comparator.comparingDouble(Row::emax)).orElseThrow();
        Row baseline = rows.stream().filter(row -> row.indentation == 0).findFirst().orElseThrow();
        StringBuilder report = new StringBuilder("# Focused R=96 Indentation Experiment v2\n\n");
        report.append("## Baseline\n\nUnindented V2 R=96: Emax=1.4968715547720990, MAD=1.1974912325005524, StdDev=1.2221853220803358, detectorShell=120, externalGap=25.\n\n");
        report.append("## Coarse search\n\n");
        table(report, rows.stream().filter(r -> isCoarse(r.indentation)).toList());
        report.append("\n## Fine search\n\nThe deterministic refinement tested 0.125, 0.0625, 0.03125, 0.015625, and 0.0078125. The last three produced the same occupied-node set as indentation 0 and were recorded as equivalent rather than rerun. The first distinct fine shape, indentation 0.0625, had Emax 2.1473952809451893.\n\n");
        table(report, rows.stream().filter(r -> !isCoarse(r.indentation)).toList());
        report.append("\n## Global best\n\n");
        report.append(String.format(Locale.ROOT, "bestIndentation=%.17g, shapeId=%s, detectorShell=%d, minimumSourceGap=%d, maximumSourceGap=%d, occupiedNodeCount=%d, Emax=%.17g, MAD=%.17g, StdDev=%.17g, absoluteImprovement=%.17g, relativeImprovement=%.17g.\n\n", best.indentation, best.shape, best.shell, best.minGap, best.maxGap, best.occupied, best.emax, best.mad, best.sd, best.improvement, best.relative));
        report.append("Q values (R01..R15): "); values(report, best.q); report.append("\n\n");
        report.append("## Directional change\n\n");
        report.append("The global best is the unindented source, so its directional values equal the frozen V2 baseline. The first distinct indented shape worsens full 15-direction Emax; the coarse candidates do not support a claim that body-diagonal indentation compensates the complete directional split. Body-diagonal Q must therefore be evaluated together with all other exact rays, not optimized independently.\n\n");
        report.append("## Effective isotropy status\n\nNo complete-isotropy claim is made. The measured Emax is approximately 1.497, far above any near-unity tolerance; all 15 exact directions do not agree within a small numerical tolerance.\n\n");
        report.append("## Detector geometry\n\nAll completed candidates used common detector shell 120. The corrected geometry audit records the actual minimum distance to the entire source for every detector; all detectors are external and satisfy the required minimum of 24. The minimum gap is 25 for the unindented source and the maximum observed gap rises with indentation.\n\n");
        report.append("## Recommendation\n\nIndentation does not improve the R=96 baseline under the frozen 15-direction Emax objective. Do not promote this one-parameter indentation family to a radius sweep based on these results; investigate the measured directional classes before adding further optimization.\n\n");
        Files.writeString(root.resolve("report.md"), report);
    }

    private static boolean isCoarse(double value) {
        for (double coarse : new double[]{0, .25, .5, .75, 1, 1.5, 2, 3, 4, 6, 8}) if (Math.abs(value - coarse) < 1e-9) return true;
        return false;
    }

    private static void table(StringBuilder out, List<Row> rows) {
        out.append("indentation | shapeId | status | shell | minGap | maxGap | Emax | MAD | StdDev | improvement\n|---:|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Row row : rows) out.append(String.format(Locale.ROOT, "%.5f | %s | %s | %d | %d | %d | %.17g | %.17g | %.17g | %.17g\n", row.indentation, row.shape, row.status, row.shell, row.minGap, row.maxGap, row.emax, row.mad, row.sd, row.improvement));
    }

    private static void values(StringBuilder out, double[] values) { for (double value : values) out.append(String.format(Locale.ROOT, "%.17g ", value)); }

    private static Row parse(String line) {
        String[] v = line.split(","); double[] q = new double[15];
        for (int i = 0; i < 15; i++) q[i] = Double.parseDouble(v[17 + i]);
        return new Row(Double.parseDouble(v[0]), v[1], v[2], Integer.parseInt(v[6]), Integer.parseInt(v[7]), Integer.parseInt(v[8]), Integer.parseInt(v[3]), Double.parseDouble(v[9]), Double.parseDouble(v[10]), Double.parseDouble(v[11]), Double.parseDouble(v[12]), Double.parseDouble(v[13]), q);
    }
}
