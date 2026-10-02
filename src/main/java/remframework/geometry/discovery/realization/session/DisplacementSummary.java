package remframework.geometry.discovery.realization.session;

/** Coordinate-independent aggregate displacement between two immutable snapshots. */
public record DisplacementSummary(double maximum,double mean,double rms) { }
