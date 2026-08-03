package remframework.geometry.discovery.realization.session;

import remframework.geometry.discovery.experiment.ThreeDExperimentConditions;
import remframework.geometry.discovery.TopologyInitialization;

public record ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,
        double outwardOrientationStrength,TopologyInitialization initialization) {
    public ContinuationSettings { strategy=strategy==null?RealizationStrategy.SIMULTANEOUS:strategy;
        initialization=initialization==null?TopologyInitialization.RANDOM_SPHERICAL:initialization; }
    public ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,double outwardOrientationStrength){this(strategy,schedule,outwardOrientationStrength,TopologyInitialization.RANDOM_SPHERICAL);}
    public static ContinuationSettings simultaneous(){return new ContinuationSettings(RealizationStrategy.SIMULTANEOUS,null,0);}
    public static ContinuationSettings sequential(ThreeDExperimentConditions c,int stable,int maximum,int ramp){return new ContinuationSettings(RealizationStrategy.SEQUENTIAL_CONTINUATION,ContinuationSchedule.closedUniformFaceSequential(c,stable,maximum,ramp),1);}
    public ContinuationSettings withInitialization(TopologyInitialization value){return new ContinuationSettings(strategy,schedule,outwardOrientationStrength,value);}
}
