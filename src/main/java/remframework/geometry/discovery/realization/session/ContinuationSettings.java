package remframework.geometry.discovery.realization.session;

import remframework.geometry.discovery.experiment.ThreeDExperimentConditions;
import remframework.geometry.discovery.TopologyInitialization;

public record ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,
        double outwardOrientationStrength,TopologyInitialization initialization,
        SpectralInitializationSettings spectralInitialization) {
    public ContinuationSettings { strategy=strategy==null?RealizationStrategy.SIMULTANEOUS:strategy;
        initialization=initialization==null?TopologyInitialization.RANDOM_SPHERICAL:initialization;
        spectralInitialization=spectralInitialization==null?SpectralInitializationSettings.instant():spectralInitialization; }
    public ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,double outwardOrientationStrength,TopologyInitialization initialization){this(strategy,schedule,outwardOrientationStrength,initialization,SpectralInitializationSettings.instant());}
    public ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,double outwardOrientationStrength){this(strategy,schedule,outwardOrientationStrength,TopologyInitialization.RANDOM_SPHERICAL,SpectralInitializationSettings.instant());}
    public static ContinuationSettings simultaneous(){return new ContinuationSettings(RealizationStrategy.SIMULTANEOUS,null,0);}
    public static ContinuationSettings sequential(ThreeDExperimentConditions c,int stable,int maximum,int ramp){return new ContinuationSettings(RealizationStrategy.SEQUENTIAL_CONTINUATION,ContinuationSchedule.closedUniformFaceSequential(c,stable,maximum,ramp),1);}
    public ContinuationSettings withInitialization(TopologyInitialization value){return new ContinuationSettings(strategy,schedule,outwardOrientationStrength,value,spectralInitialization);}
    public ContinuationSettings withSpectralInitialization(SpectralInitializationSettings value){return new ContinuationSettings(strategy,schedule,outwardOrientationStrength,initialization,value);}
}
