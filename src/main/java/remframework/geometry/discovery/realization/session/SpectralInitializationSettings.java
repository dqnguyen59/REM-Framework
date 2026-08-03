package remframework.geometry.discovery.realization.session;

/** Immutable, topology-independent spectral initialization policy. */
public record SpectralInitializationSettings(SpectralPresentationMode mode,
        double animationDurationSeconds,int animationFramesPerSecond,
        int guidanceIterations,int handoverIterations,double guidanceStrength) {
    public SpectralInitializationSettings {
        mode=mode==null?SpectralPresentationMode.INSTANT:mode;
        if(!Double.isFinite(animationDurationSeconds)||animationDurationSeconds<=0)
            throw new IllegalArgumentException("Animation duration must be positive and finite.");
        if(animationFramesPerSecond<1||guidanceIterations<1||handoverIterations<1)
            throw new IllegalArgumentException("Animation rate and initialization iteration counts must be positive.");
        if(!Double.isFinite(guidanceStrength)||guidanceStrength<=0)
            throw new IllegalArgumentException("Spectral guidance strength must be positive and finite.");
    }
    public static SpectralInitializationSettings instant(){return new SpectralInitializationSettings(SpectralPresentationMode.INSTANT,2,60,240,120,0.08);}
    public int animationFrames(){return Math.max(1,(int)Math.round(animationDurationSeconds*animationFramesPerSecond));}
}
