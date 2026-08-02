package remframework.geometry.discovery;

import java.util.ArrayDeque;
import java.util.Deque;

/** Sliding-window numerical classification; it does not infer state from iteration count. */
public final class StagnationDetector {
    private final int windowSize;
    private final double minimumRelativeImprovement;
    private final Deque<Sample> samples=new ArrayDeque<>();
    public StagnationDetector(int windowSize,double minimumRelativeImprovement){if(windowSize<4||minimumRelativeImprovement<=0)throw new IllegalArgumentException("A finite stagnation window and positive improvement threshold are required.");this.windowSize=windowSize;this.minimumRelativeImprovement=minimumRelativeImprovement;}
    public void reset(){samples.clear();}
    public StagnationStatus add(double objective,double maximumVelocity,boolean converged){samples.addLast(new Sample(objective,maximumVelocity));while(samples.size()>windowSize)samples.removeFirst();if(!Double.isFinite(objective)||!Double.isFinite(maximumVelocity))return new StagnationStatus(StagnationClass.NUMERICALLY_UNSTABLE,"A non-finite objective or velocity was measured.",Double.NaN,samples.size());if(converged)return new StagnationStatus(StagnationClass.CONVERGED,"All active convergence gates passed.",1,samples.size());if(samples.size()<windowSize)return StagnationStatus.progressing();double first=samples.getFirst().objective,last=samples.getLast().objective;double improvement=(first-last)/Math.max(Math.abs(first),1e-12);int reversals=0;double previousObjective=Double.NaN,previousDelta=Double.NaN;for(Sample sample:samples){if(Double.isFinite(previousObjective)){double delta=sample.objective-previousObjective;if(Double.isFinite(previousDelta)&&delta*previousDelta<0)reversals++;previousDelta=delta;}previousObjective=sample.objective;}if(last>first*(1+minimumRelativeImprovement))return new StagnationStatus(StagnationClass.DIVERGING,"The normalized objective increased across the sliding window.",improvement,windowSize);if(improvement<minimumRelativeImprovement){double velocity=samples.stream().mapToDouble(Sample::velocity).max().orElse(0);if(reversals>windowSize/3&&velocity>1e-5)return new StagnationStatus(StagnationClass.OSCILLATING,"Objective changes repeatedly reversed sign while motion remained measurable.",improvement,windowSize);return new StagnationStatus(StagnationClass.PLATEAUED,"Normalized objective improvement remained below the configured threshold.",improvement,windowSize);}return new StagnationStatus(StagnationClass.PROGRESSING,"Normalized objective is improving across the sliding window.",improvement,windowSize);}
    private record Sample(double objective,double velocity){}
}
