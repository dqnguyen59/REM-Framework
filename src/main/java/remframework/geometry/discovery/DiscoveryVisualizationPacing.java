package remframework.geometry.discovery;

import java.util.Arrays;

public final class DiscoveryVisualizationPacing {
    public static final int[] SPEEDS = {1, 2, 5, 8, 10, 20, 50, 100, 200, 500, 1000};
    private DiscoveryVisualizationPacing() { }

    public static boolean validSpeed(int value) {
        return Arrays.stream(SPEEDS).anyMatch(candidate -> candidate == value);
    }

    public static int sliderIndex(int speed, boolean maximum) {
        if (maximum) return SPEEDS.length;
        for (int i = 0; i < SPEEDS.length; i++)
            if (SPEEDS[i] == speed) return i;
        return 3;
    }

    public static Selection selection(int index) {
        int clamped = Math.max(0, Math.min(SPEEDS.length, index));
        return clamped == SPEEDS.length
                ? new Selection(SPEEDS[SPEEDS.length - 1], true)
                : new Selection(SPEEDS[clamped], false);
    }

    public record Selection(int updatesPerSecond, boolean maximum) { }
}
