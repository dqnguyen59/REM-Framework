package remframework.geometry.discovery;

import java.time.Instant;

public record DiscoveryExperimentEvent(
        Instant timestamp, String type, int iteration, String details) {
}
