package remframework.geometry.discovery.realization.session;

/** Immutable initialization status consumed by UI and persistence without recomputation. */
public record InitializationSnapshot(SpectralPresentationMode mode,InitializationPhase phase,
        int revision,double progress,int initializationIteration,double spectralGuidanceFactor,
        double geometricConstraintFactor,boolean solverRunning,boolean displayPreview) {
    public InitializationSnapshot { progress=Math.max(0,Math.min(1,progress)); }
    public static InitializationSnapshot instant(){return new InitializationSnapshot(SpectralPresentationMode.INSTANT,InitializationPhase.INSTANT,0,1,0,0,1,false,false);}
}
