package remframework.geometry.discovery.realization.sequence;

import java.util.Comparator;

/** Stable comparison-table ordering: qualified, furthest stage, ratio, ID, seed. */
public final class SequenceResultOrdering {
    private SequenceResultOrdering() { }
    public static final Comparator<SequenceExperimentResult> SCIENTIFIC=
            Comparator.comparing(SequenceExperimentResult::qualified).reversed()
                    .thenComparing(Comparator.comparingInt(SequenceExperimentResult::furthestStage).reversed())
                    .thenComparingDouble(SequenceExperimentResult::worstRatio)
                    .thenComparing(r->r.sequence().sequenceId()).thenComparingLong(SequenceExperimentResult::seed);
}
