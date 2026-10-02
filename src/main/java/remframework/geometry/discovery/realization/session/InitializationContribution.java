package remframework.geometry.discovery.realization.session;

import java.util.List;

/** Display and scientific contribution measurements for spectral initialization. */
public record InitializationContribution(DisplacementSummary randomToSpectral,
        DisplacementSummary scientificStartToFinal,List<InitializationMeasurement> snapshots) {
    public InitializationContribution { snapshots=List.copyOf(snapshots); }
}
