package remframework.geometry.discovery.realization.session;

import remframework.geometry.discovery.experiment.ThreeDExperimentConditions;
import remframework.geometry.discovery.TopologyInitialization;
import remframework.geometry.discovery.realization.sequence.*;

public record ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,
        double outwardOrientationStrength,TopologyInitialization initialization,
        SpectralInitializationSettings spectralInitialization,ConstraintSequenceMode sequenceMode,
        ConstraintSequence constraintSequence) {
    public ContinuationSettings { strategy=strategy==null?RealizationStrategy.SIMULTANEOUS:strategy;
        initialization=initialization==null?TopologyInitialization.RANDOM_SPHERICAL:initialization;
        spectralInitialization=spectralInitialization==null?SpectralInitializationSettings.instant():spectralInitialization;
        sequenceMode=sequenceMode==null?ConstraintSequenceMode.BUILT_IN_SEQUENCE:sequenceMode;
        if(sequenceMode==ConstraintSequenceMode.CUSTOM_SEQUENCE){
            if(constraintSequence==null)throw new IllegalArgumentException("CUSTOM_SEQUENCE requires an explicit sequence.");
            if(initialization!=TopologyInitialization.RANDOM_SPHERICAL)throw new IllegalArgumentException("Custom experiments require RANDOM_SPHERICAL initialization.");
            new ConstraintSequenceValidator().requireExecutable(constraintSequence);
        }
    }
    public ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,double outwardOrientationStrength,TopologyInitialization initialization,SpectralInitializationSettings spectralInitialization){this(strategy,schedule,outwardOrientationStrength,initialization,spectralInitialization,ConstraintSequenceMode.BUILT_IN_SEQUENCE,null);}
    public ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,double outwardOrientationStrength,TopologyInitialization initialization){this(strategy,schedule,outwardOrientationStrength,initialization,SpectralInitializationSettings.instant());}
    public ContinuationSettings(RealizationStrategy strategy,ContinuationSchedule schedule,double outwardOrientationStrength){this(strategy,schedule,outwardOrientationStrength,TopologyInitialization.RANDOM_SPHERICAL,SpectralInitializationSettings.instant());}
    public static ContinuationSettings simultaneous(){return new ContinuationSettings(RealizationStrategy.SIMULTANEOUS,null,0);}
    public static ContinuationSettings sequential(ThreeDExperimentConditions c,int stable,int maximum,int ramp){return new ContinuationSettings(RealizationStrategy.SEQUENTIAL_CONTINUATION,ContinuationSchedule.closedUniformFaceSequential(c,stable,maximum,ramp),1);}
    public static ContinuationSettings custom(ConstraintSequence sequence){return new ContinuationSettings(RealizationStrategy.SEQUENTIAL_CONTINUATION,CustomSequenceAdapter.toContinuation(sequence),1,TopologyInitialization.RANDOM_SPHERICAL,SpectralInitializationSettings.instant(),ConstraintSequenceMode.CUSTOM_SEQUENCE,sequence);}
    public ContinuationSettings withInitialization(TopologyInitialization value){return new ContinuationSettings(strategy,schedule,outwardOrientationStrength,value,spectralInitialization,sequenceMode,constraintSequence);}
    public ContinuationSettings withSpectralInitialization(SpectralInitializationSettings value){return new ContinuationSettings(strategy,schedule,outwardOrientationStrength,initialization,value,sequenceMode,constraintSequence);}
}
