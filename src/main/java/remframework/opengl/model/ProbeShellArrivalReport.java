package remframework.opengl.model;

import java.util.*;

/** Same-baseline shell measurements; no changes to mechanics or passive samples. */
public final class ProbeShellArrivalReport {
    public record Timing(int detected,int total,long minimum,long maximum,double mean,double spreadPercent) {
        public boolean complete(){return total>0 && detected==total;}
        public long difference(){return detected==0?-1:maximum-minimum;}
    }
    public record Row(int shell,double radius,ProbeArrivalMeasurement.Snapshot snapshot,Timing position,Timing gradient) {}
    public record Fit(int shells,double slope,double intercept) {
        public boolean valid(){return shells>=2 && Double.isFinite(slope) && slope>0;}
        public double speed(){return valid()?1/slope:Double.NaN;}
    }
    public record SpreadTrend(int shells,double slope,double intercept) {
        public boolean valid(){return shells>=2 && Double.isFinite(slope) && Double.isFinite(intercept);}
    }
    /** Least squares spread (%) = intercept + slope * radius; slope is percentage points per bond. */
    public static SpreadTrend spreadTrend(List<Row> rows,boolean gradient) {
        var complete=rows.stream().filter(r->{var t=gradient?r.gradient:r.position;
            return t.complete() && Double.isFinite(t.spreadPercent) && t.spreadPercent>=0;}).toList();
        if(complete.size()<2)return new SpreadTrend(complete.size(),Double.NaN,Double.NaN);
        double xMean=complete.stream().mapToDouble(Row::radius).average().orElseThrow();
        double yMean=complete.stream().mapToDouble(r->(gradient?r.gradient:r.position).spreadPercent).average().orElseThrow();
        double xx=0,xy=0;
        for(var r:complete){double dx=r.radius-xMean;xx+=dx*dx;xy+=dx*((gradient?r.gradient:r.position).spreadPercent-yMean);}
        double slope=xx>0?xy/xx:Double.NaN;
        return new SpreadTrend(complete.size(),slope,yMean-slope*xMean);
    }
    private record Entry(double radius,ProbeArrivalMeasurement measurement) {}
    private final Map<Integer,Entry> entries=new TreeMap<>();
    private boolean recordTraces;
    private int sweepTurns;
    private double sweepAmplitude=Double.NaN,sweepWidth=Double.NaN;
    private double cycleDeadband = 1e-9;
    public synchronized double getCycleDeadband() { return cycleDeadband; }
    public synchronized void setCycleDeadband(double value) {
        if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("Positive finite cycle deadband required");
        cycleDeadband=value;
    }
    public record CycleRow(int shell, double radius, ProbeArrivalMeasurement.CycleResult result) {}
    public synchronized List<CycleRow> cycleRows() {
        var rows=new ArrayList<CycleRow>();
        for(var entry:entries.entrySet())for(var result:entry.getValue().measurement.cycleResults())
            rows.add(new CycleRow(entry.getKey(),entry.getValue().radius,result));
        return List.copyOf(rows);
    }
    public synchronized void writeCycles(java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bond_lengths,probe_id,signal,cycle_start_update,cycle_middle_update,cycle_end_update,period_updates,cycle_completion_arrival_update,deadband,status\n");
        for(var row:cycleRows()) {
            var result=row.result;var cycle=result.cycle();
            writer.write(String.format(Locale.ROOT,"%d,%.17g,\"%s\",%s,%.17g,%.17g,%.17g,%.17g,%d,%.17g,%s%n",
                row.shell+1,row.radius,result.probeId().replace("\"","\"\""),result.signal(),cycle.startUpdate(),cycle.middleUpdate(),cycle.endUpdate(),cycle.periodUpdates(),cycle.arrivalUpdate(),cycle.deadband(),cycle.status()));
        }
    }
    public record SweepRow(int shell,double radius,ProbeArrivalMeasurement.SweepResult result) {}
    public synchronized List<SweepRow> sweepRows() {
        var rows=new ArrayList<SweepRow>();
        for(var entry:entries.entrySet())for(var result:entry.getValue().measurement.sweepResults())
            rows.add(new SweepRow(entry.getKey(),entry.getValue().radius,result));
        return List.copyOf(rows);
    }
    public synchronized boolean sweepsComplete() {
        var rows=sweepRows();return !rows.isEmpty() && rows.stream().allMatch(r->r.result.measurement().complete());
    }
    public synchronized void enableSweep(int period,int cycles,org.joml.Vector3d center) {
        entries.values().forEach(e->e.measurement.enableSweep(period,cycles,center));
    }
    public synchronized void enableSharedSweep(int period,int cycles,org.joml.Vector3d center,long start) {
        entries.values().forEach(e->e.measurement.enableSharedSweep(period,cycles,center,start));
    }
    public record ResponseSpread(int shell,double radius,int complete,int total,double minimum,double maximum,
                                 double mean,double spreadPercent) {}
    /** Spread of nonnegative per-probe RMS values; signed means are never used as a denominator. */
    public synchronized List<ResponseSpread> responseSpreads() {
        var result=new ArrayList<ResponseSpread>();
        for(var entry:entries.entrySet()) {
            var samples=entry.getValue().measurement.sweepResults();
            if(samples.isEmpty())continue;
            var complete=samples.stream().filter(s->s.measurement().complete()).toList();
            boolean ready=complete.size()==samples.size();
            var stats=complete.stream().mapToDouble(s->s.measurement().rms()).summaryStatistics();
            double mean=ready?stats.getAverage():Double.NaN;
            result.add(new ResponseSpread(entry.getKey(),entry.getValue().radius,complete.size(),samples.size(),
                ready?stats.getMin():Double.NaN,ready?stats.getMax():Double.NaN,mean,
                ready&&mean>0?100*(stats.getMax()-stats.getMin())/mean:Double.NaN));
        }
        return List.copyOf(result);
    }
    public synchronized void configureSweepSource(int turns,double amplitude,double width) {
        sweepTurns=turns;sweepAmplitude=amplitude;sweepWidth=width;
    }
    public synchronized void writeSweeps(java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bond_lengths,probe_id,window_start_update,window_end_update,requested_cycles,completed_cycles,updates_per_cycle,samples,mean_imbalance_x,mean_imbalance_y,mean_imbalance_z,imbalance_rms,radial_mean,perpendicular_rms,status,spiral_turns_per_half_cycle,source_amplitude,source_width,measurement_basis\n");
        for(var row:sweepRows()) {
            var r=row.result.measurement();
            writer.write(String.format(Locale.ROOT,"%d,%.17g,\"%s\",%d,%d,%d,%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s,%d,%.17g,%.17g,source_period_windows_after_detection%n",
                row.shell+1,row.radius,row.result.probeId().replace("\"","\"\""),r.startUpdate(),r.endUpdate(),r.requestedCycles(),r.completedCycles(),
                r.updatesPerCycle(),r.samples(),r.meanX(),r.meanY(),r.meanZ(),r.rms(),r.radialMean(),r.perpendicularRms(),r.status(),sweepTurns,sweepAmplitude,sweepWidth));
        }
    }
    public record CycleArrival(int shell, double radius, String signal, int completeDirections,
                               int totalDirections, double minimum, double maximum, double mean,
                               double standardDeviation, double spreadPercent) {
        public boolean complete() { return totalDirections > 0 && completeDirections == totalDirections; }
        public double range() { return maximum - minimum; }
    }

    /** Directional first-arrival statistics released only after every whole-cycle window completes.
     * <br><br>Times are relative to source start (update zero); the common window end is never used.
     * Spread is 100 * (maximum - minimum) / mean. This is timing spread, not inverse-time speed spread.
     */
    public synchronized List<CycleArrival> cycleArrivals() {
        var result = new ArrayList<CycleArrival>();
        for (var entry : entries.entrySet()) {
            var measurement = entry.getValue().measurement;
            var sweeps = measurement.sweepResults();
            if (sweeps.isEmpty()) continue;
            var arrivals = measurement.snapshot().results();
            int complete = (int) sweeps.stream().filter(r -> r.measurement().complete()).count();
            for (boolean gradient : new boolean[]{false, true}) {
                var times = arrivals.stream().mapToDouble(r -> gradient
                    ? r.gradientEstimatedArrival() : r.positionEstimatedArrival()).toArray();
                boolean ready = complete == arrivals.size() && times.length > 0
                    && java.util.Arrays.stream(times).allMatch(t -> Double.isFinite(t) && t > 0);
                var stats = java.util.Arrays.stream(times).summaryStatistics();
                double mean = ready ? stats.getAverage() : Double.NaN;
                double variance = ready ? java.util.Arrays.stream(times)
                    .map(t -> (t - mean) * (t - mean)).average().orElseThrow() : Double.NaN;
                result.add(new CycleArrival(entry.getKey(), entry.getValue().radius,
                    gradient ? "gradient" : "position", ready ? complete : 0, arrivals.size(),
                    ready ? stats.getMin() : Double.NaN, ready ? stats.getMax() : Double.NaN,
                    mean, Math.sqrt(variance), ready ? 100 * (stats.getMax() - stats.getMin()) / mean : Double.NaN));
            }
        }
        return List.copyOf(result);
    }

    public synchronized void writeCycleArrivals(java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bond_lengths,signal,complete_directions,total_directions,min_arrival_update,max_arrival_update,mean_arrival_update,range_updates,population_stddev_updates,timing_spread_percent,status,time_basis\n");
        for (var r : cycleArrivals()) writer.write(String.format(Locale.ROOT,
            "%d,%.17g,%s,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s,interpolated_first_threshold_from_source_start_after_whole_cycle_completion%n",
            r.shell + 1, r.radius, r.signal, r.completeDirections, r.totalDirections,
            r.minimum, r.maximum, r.mean, r.range(), r.standardDeviation, r.spreadPercent,
            r.complete() ? "complete" : "incomplete"));
    }

    public record PeakRow(int shell, double radius, boolean gradient, ProbePeakAnalysis.Result result) {}
    private List<PeakRow> peakRows;
    public record ReversalRow(int shell, double radius, String signal, ProbeReversalAnalysis.Result result) {}
    private List<ReversalRow> reversalRows;
    public synchronized List<ReversalRow> reversalRows() {
        if(entries.values().stream().anyMatch(e->e.measurement.isTraceRecording()))return List.of();
        if(reversalRows==null) {
            var rows=new ArrayList<ReversalRow>();
            String[] signals={"velocity_z","imbalance_z","cycle_imbalance_z"};
            for(var entry:entries.entrySet())for(int channel=0;channel<signals.length;channel++)
                rows.add(new ReversalRow(entry.getKey(),entry.getValue().radius,signals[channel],entry.getValue().measurement.reversalAnalysis(channel)));
            reversalRows=List.copyOf(rows);
        }
        return reversalRows;
    }
    public synchronized void writeReversals(java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bond_lengths,signal,event,direction,reference_update,window_start,window_end,deadband,probe_id,reversal_update,status,shell_min_update,shell_max_update,shell_range_updates\n");
        for(var row:reversalRows()) {
            if(row.result.events().isEmpty())writer.write(String.format(Locale.ROOT,"%d,%.17g,%s,0,,NaN,NaN,NaN,NaN,,NaN,%s,NaN,NaN,NaN%n",row.shell+1,row.radius,row.signal,row.result.status()));
            for(var event:row.result.events())for(var match:event.matches())writer.write(String.format(Locale.ROOT,
                "%d,%.17g,%s,%d,%s,%.17g,%.17g,%.17g,%.17g,\"%s\",%.17g,%s,%.17g,%.17g,%.17g%n",
                row.shell+1,row.radius,row.signal,event.ordinal(),event.direction()>0?"negative_to_positive":"positive_to_negative",
                event.referenceUpdate(),event.windowStart(),event.windowEnd(),event.deadband(),match.id().replace("\"","\"\""),match.update(),match.status(),event.minimum(),event.maximum(),event.range()));
        }
    }
    public synchronized List<PeakRow> peakRows() {
        if(entries.values().stream().anyMatch(e->e.measurement.isTraceRecording()))return List.of();
        if(peakRows==null){
            var result=new ArrayList<PeakRow>();
            for(var entry:entries.entrySet())for(boolean gradient:new boolean[]{false,true})
                result.add(new PeakRow(entry.getKey(),entry.getValue().radius,gradient,entry.getValue().measurement.peakAnalysis(gradient)));
            peakRows=List.copyOf(result);
        }
        return peakRows;
    }
    public synchronized void writePeaks(java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bond_lengths,signal,window_start,window_end,probe_id,peak_update,peak_amplitude,status,half_peak_update,half_peak_status\n");
        for(var row:peakRows()) {
            var result=row.result;
            if(result.peaks().isEmpty())writer.write(String.format(Locale.ROOT,"%d,%.17g,%s,NaN,NaN,,NaN,NaN,%s,NaN,unavailable%n",row.shell+1,row.radius,row.gradient?"gradient":"position",result.status()));
            for(var peak:result.peaks())writer.write(String.format(Locale.ROOT,"%d,%.17g,%s,%.17g,%.17g,\"%s\",%.17g,%.17g,%s,%.17g,%s%n",
                row.shell+1,row.radius,row.gradient?"gradient":"position",result.windowStart(),result.windowEnd(),peak.id().replace("\"","\"\""),peak.update(),peak.amplitude(),peak.status(),peak.halfPeakUpdate(),Double.isFinite(peak.halfPeakUpdate())?"complete":"No unique rising half-peak crossing"));
        }
    }
    public synchronized boolean isRecordTraces(){return recordTraces;}
    public synchronized void setRecordTraces(boolean enabled){recordTraces=enabled;}
    public synchronized boolean hasTraces(){return entries.values().stream().anyMatch(e->e.measurement.hasTrace());}
    public synchronized void writeTraces(java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bond_lengths,probe_id,update,position_change,gradient_change,position_threshold,gradient_threshold,position_arrival_update,gradient_arrival_update,trace_truncated,velocity_z,imbalance_z,cycle_imbalance_z,position_estimated_arrival,gradient_estimated_arrival\n");
        for(var entry:entries.values())entry.measurement.writeTrace(writer,entry.radius);
    }
    public synchronized void start(List<? extends PassiveObserver> probes,double position,double gradient) {
        entries.clear();
        sweepTurns=0;sweepAmplitude=Double.NaN;sweepWidth=Double.NaN;
        peakRows=null;
        reversalRows=null;
        for(int shell:ProbeArrivalMeasurement.availableShells(probes)) {
            var selected=probes.stream().filter(p->p.getDefinition().getPlacement()!=null
                && p.getDefinition().getPlacement().targets().stream().anyMatch(t->t.shell()==shell)).toList();
            var radii=selected.stream().flatMap(p->p.getDefinition().getPlacement().targets().stream())
                .filter(t->t.shell()==shell).mapToDouble(ProbeDefinition.Target::requestedRadius).distinct().toArray();
            if(radii.length!=1)continue;
            var measurement=new ProbeArrivalMeasurement();
            if(measurement.start(selected,shell,position,gradient,cycleDeadband))entries.put(shell,new Entry(radii[0],measurement));
        }
        if(recordTraces){
            int count=entries.values().stream().mapToInt(e->e.measurement.snapshot().results().size()).sum();
            // At most about 96 MB of primitive trace data across all shells (six doubles/sample).
            int capacity=Math.max(1,Math.min(4096,2_000_000/Math.max(1,count)));
            entries.values().forEach(e->e.measurement.enableTrace(capacity));
        }
    }
    public synchronized void sample(){entries.values().forEach(e->e.measurement.sample());}
    public synchronized void finish(){entries.values().forEach(e->e.measurement.finish());}
    public synchronized void clear(){entries.clear();peakRows=null;reversalRows=null;}
    public synchronized List<Row> rows(){
        return entries.entrySet().stream().map(e->{var s=e.getValue().measurement.snapshot();
            return new Row(e.getKey(),e.getValue().radius,s,timing(s,false),timing(s,true));}).toList();
    }
    public static Timing timing(ProbeArrivalMeasurement.Snapshot snapshot,boolean gradient) {
        var values=snapshot.results().stream().mapToLong(r->gradient?r.gradientArrivalUpdate():r.positionArrivalUpdate()).filter(v->v>=0).summaryStatistics();
        int count=(int)values.getCount();
        double spread=count==snapshot.results().size()?ProbeArrivalMeasurement.equalRadiusApparentSpread(snapshot.results(),gradient):Double.NaN;
        return new Timing(count,snapshot.results().size(),count==0?-1:values.getMin(),count==0?-1:values.getMax(),count==0?Double.NaN:values.getAverage(),spread);
    }
    /** Least squares t = intercept + slope * radius, using complete shells only. */
    public static Fit fit(List<Row> rows,boolean gradient){
        var complete=rows.stream().filter(r->(gradient?r.gradient:r.position).complete()).toList();
        if(complete.size()<2)return new Fit(complete.size(),Double.NaN,Double.NaN);
        double xMean=complete.stream().mapToDouble(Row::radius).average().orElseThrow();
        double yMean=complete.stream().mapToDouble(r->(gradient?r.gradient:r.position).mean).average().orElseThrow();
        double xx=0,xy=0;
        for(var r:complete){double dx=r.radius-xMean;xx+=dx*dx;xy+=dx*((gradient?r.gradient:r.position).mean-yMean);}
        double slope=xx>0?xy/xx:Double.NaN;
        return new Fit(complete.size(),slope,yMean-slope*xMean);
    }
    public static String csv(List<Row> rows){
        var text=new StringBuilder("shell,radius_bond_lengths,signal,detected,total,min_update,max_update,difference_updates,mean_update,speed_spread_percent,position_threshold,gradient_threshold,status,spread_slope_pp_per_bond,spread_fit_intercept_percent,spread_fit_shells,estimated_min_update,estimated_max_update,estimated_mean_update,spread_time_basis\n");
        var positionTrend=spreadTrend(rows,false);var gradientTrend=spreadTrend(rows,true);
        for(var r:rows)for(boolean gradient:new boolean[]{false,true}){
            var t=gradient?r.gradient:r.position;
            var trend=gradient?gradientTrend:positionTrend;
            var estimates=r.snapshot.results().stream().mapToDouble(result->gradient?result.gradientEstimatedArrival():result.positionEstimatedArrival()).summaryStatistics();
            boolean estimatedComplete=t.complete()&&r.snapshot.results().stream().allMatch(result->Double.isFinite(gradient?result.gradientEstimatedArrival():result.positionEstimatedArrival()));
            text.append(String.format(Locale.ROOT,"%d,%.9g,%s,%d,%d,%d,%d,%d,%.9g,%.9g,%.9g,%.9g,%s,%.9g,%.9g,%d,%.17g,%.17g,%.17g,interpolated_threshold_crossing%n",r.shell+1,r.radius,gradient?"gradient":"position",t.detected,t.total,t.minimum,t.maximum,t.difference(),t.mean,t.spreadPercent,r.snapshot.positionThreshold(),r.snapshot.gradientThreshold(),r.snapshot.running()?"recording":t.complete()?"complete":"incomplete",trend.slope,trend.intercept,trend.shells,estimatedComplete?estimates.getMin():Double.NaN,estimatedComplete?estimates.getMax():Double.NaN,estimatedComplete?estimates.getAverage():Double.NaN));
        }
        return text.toString();
    }
}
