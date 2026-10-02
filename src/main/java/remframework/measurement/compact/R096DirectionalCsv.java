package remframework.measurement.compact;

import java.util.Locale;

/** CSV schema for focused R=96 directional measurements. */
public final class R096DirectionalCsv {
    private R096DirectionalCsv() { }

    public static String header() {
        return "directionId,primitiveDirection,detectorShell,detectorRelativeX,detectorRelativeY,detectorRelativeZ,directionCycleMean,globalDirectionalMean,normalizedQ,status";
    }

    public static String row(String directionId, String ray, int shell, int x, int y, int z,
                             double directionMean, double globalMean, double q, String status) {
        return String.format(Locale.ROOT, "%s,\"%s\",%d,%d,%d,%d,%.17g,%.17g,%.17g,%s",
                directionId, ray, shell, x, y, z, directionMean, globalMean, q, status);
    }
}
