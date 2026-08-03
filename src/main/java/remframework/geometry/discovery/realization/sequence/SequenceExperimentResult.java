package remframework.geometry.discovery.realization.sequence;

import java.util.List;
import remframework.geometry.discovery.TopologyInitialization;

public record SequenceExperimentResult(ConstraintSequence sequence,String configurationId,long seed,
        TopologyInitialization initialization,boolean spectralCoordinatesUsed,
        boolean benchmarkReferenceUsed,boolean topologySpecificCoordinatesUsed,
        List<SequenceStageHistory> stageHistory,int furthestStage,boolean qualified,double worstRatio,
        long runtimeMillis,long iterations,int convexityViolations,int selfIntersections,
        Double volume,SequenceConfigurationStatus status,String explanation) {
    public SequenceExperimentResult {
        stageHistory=List.copyOf(stageHistory);explanation=explanation==null?"":explanation;
        if(sequence.mode()==ConstraintSequenceMode.CUSTOM_SEQUENCE&&(initialization!=TopologyInitialization.RANDOM_SPHERICAL
                ||spectralCoordinatesUsed||benchmarkReferenceUsed||topologySpecificCoordinatesUsed))
            throw new IllegalArgumentException("Custom-sequence result violates the random-spherical spectral firewall.");
    }
}
