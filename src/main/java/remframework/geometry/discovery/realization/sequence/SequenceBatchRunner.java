package remframework.geometry.discovery.realization.sequence;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** Deterministic, sequential and resumable batch coordinator with isolated run creation. */
public final class SequenceBatchRunner {
    private final List<Job> jobs;private final BiFunction<ConstraintSequence,Long,SequenceExperimentResult> isolatedRun;
    private final List<SequenceExperimentResult> completed=new ArrayList<>();private final AtomicBoolean cancelled=new AtomicBoolean();private int cursor;
    public SequenceBatchRunner(List<ConstraintSequence> sequences,List<Long> seeds,BiFunction<ConstraintSequence,Long,SequenceExperimentResult> isolatedRun){this.isolatedRun=Objects.requireNonNull(isolatedRun);List<Job> ordered=new ArrayList<>();for(ConstraintSequence s:sequences)for(long seed:seeds)ordered.add(new Job(s,seed));jobs=List.copyOf(ordered);}
    public synchronized List<SequenceExperimentResult> run(){cancelled.set(false);while(cursor<jobs.size()&&!cancelled.get()){Job job=jobs.get(cursor);completed.add(isolatedRun.apply(job.sequence,job.seed));cursor++;}return List.copyOf(completed);}
    public void cancel(){cancelled.set(true);}
    public synchronized Progress progress(){long qualified=completed.stream().filter(SequenceExperimentResult::qualified).count();Map<Integer,Long> failures=new TreeMap<>();completed.stream().filter(r->!r.qualified()).forEach(r->failures.merge(r.furthestStage(),1L,Long::sum));SequenceExperimentResult best=completed.stream().min(SequenceResultOrdering.SCIENTIFIC).orElse(null);return new Progress(cursor,jobs.size(),qualified,failures,best,cursor<jobs.size());}
    private record Job(ConstraintSequence sequence,long seed) { }
    public record Progress(int completedRuns,int totalRuns,long qualifiedResults,Map<Integer,Long> failedStageCounts,SequenceExperimentResult currentBest,boolean resumable){public Progress{failedStageCounts=Map.copyOf(failedStageCounts);}}
}
