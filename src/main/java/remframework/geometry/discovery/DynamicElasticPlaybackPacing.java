package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import java.util.Arrays;

/**
 * UI-only pacing values for Dynamic Elastic Discovery playback.
 */
public final class DynamicElasticPlaybackPacing {
    public static final int[] SPEEDS = {1, 2, 5, 10, 20, 30, 50, 100, 200, 500, 1000};
    public static final int[] PUBLICATION_INTERVALS = {1, 2, 5, 10, 25, 50, 100};
    public static final int DEFAULT_SPEED = 30;
    public static final int DEFAULT_PUBLICATION_INTERVAL = 1;

    private DynamicElasticPlaybackPacing() { }

    public static boolean validSpeed(int value) {
        return Arrays.stream(SPEEDS).anyMatch(candidate -> candidate == value);
    }
    public static boolean validPublicationInterval(int value) {
        return Arrays.stream(PUBLICATION_INTERVALS)
                .anyMatch(candidate -> candidate == value);
    }
    public static long delayNanos(int updatesPerSecond, boolean maximum) {
        if (maximum) return 0;
        if (!validSpeed(updatesPerSecond))
            throw new IllegalArgumentException("Unsupported Dynamic Elastic playback speed.");
        return Math.round(1_000_000_000.0 / updatesPerSecond);
    }
    public static int speedSliderIndex(int speed, boolean maximum) {
        if (maximum) return SPEEDS.length;
        for (int i = 0; i < SPEEDS.length; i++)
            if (SPEEDS[i] == speed) return i;
        return indexOf(DEFAULT_SPEED);
    }
    public static int speedAtSliderIndex(int index) {
        if (index < 0 || index >= SPEEDS.length)
            throw new IllegalArgumentException("Maximum has no numeric speed.");
        return SPEEDS[index];
    }
    public static int publicationSliderIndex(int interval) {
        for (int i = 0; i < PUBLICATION_INTERVALS.length; i++)
            if (PUBLICATION_INTERVALS[i] == interval) return i;
        return 0;
    }
    private static int indexOf(int speed) {
        for (int i = 0; i < SPEEDS.length; i++)
            if (SPEEDS[i] == speed) return i;
        return 0;
    }
}
