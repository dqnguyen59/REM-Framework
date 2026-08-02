package remframework.geometry.discovery.realization.session;

import remframework.geometry.discovery.experiment.ThreeDExperimentConditions;

public record ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,double outwardOrientationStrength) {
    public ContinuationSettings { strategy=strategy==null?RealizationStrategy.SIMULTANEOUS:strategy; }
    public static ContinuationSettings simultaneous(){return new ContinuationSettings(RealizationStrategy.SIMULTANEOUS,null,0);}
    public static ContinuationSettings sequential(ThreeDExperimentConditions c,int stable,int maximum,int ramp){return new ContinuationSettings(RealizationStrategy.SEQUENTIAL_CONTINUATION,ContinuationSchedule.closedUniformFaceSequential(c,stable,maximum,ramp),1);}
}
