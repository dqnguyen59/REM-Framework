package remframework.opengl.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProbePeakAnalysisTest {
    private static ProbePeakAnalysis.Trace trace(String id,double scale,double delay) {
        double[] samples=new double[90*3];
        for(int i=0;i<90;i++) {
            double t=i+1;
            // Smooth first lobe bracketed by valleys, followed by another lobe.
            double value=t<50?Math.max(0,1-Math.pow((t-30-delay)/15,2)):
                Math.max(0,2-Math.pow((t-70-delay)/10,2));
            samples[i*3]=t;samples[i*3+1]=samples[i*3+2]=scale*value;
        }
        return new ProbePeakAnalysis.Trace(id,samples);
    }
    @Test void separatesAmplitudeDifferenceFromPeakTimeAndResolvesFractionalShift() {
        var result=ProbePeakAnalysis.analyze(List.of(trace("a",1.01,0),trace("b",.99,0)),true,25);
        assertTrue(result.complete());assertEquals(0,result.timingRange(),1e-12);
        assertTrue(result.halfPeakComplete());assertEquals(0,result.halfPeakTimingRange(),1e-12);
        assertEquals(.99,result.minimumAmplitude(),1e-12);assertEquals(1.01,result.maximumAmplitude(),1e-12);
        assertEquals(30,result.peaks().getFirst().update(),1e-12);
        var shifted=ProbePeakAnalysis.analyze(List.of(trace("a",1,0),trace("b",.8,1.25)),false,25);
        assertTrue(shifted.complete());assertEquals(1.25,shifted.timingRange(),1e-12);
        assertTrue(shifted.halfPeakComplete());assertEquals(1.25,shifted.halfPeakTimingRange(),.03);
    }
    @Test void rejectsUnfinishedUnalignedOrAmbiguousPeaks() {
        var a=trace("a",1,0);
        assertFalse(ProbePeakAnalysis.analyze(List.of(new ProbePeakAnalysis.Trace("short",java.util.Arrays.copyOf(a.samples(),30*3))),true,25).complete());
        var missing=trace("missing",1,0);missing.samples()[9]=99;
        assertFalse(ProbePeakAnalysis.analyze(List.of(a,missing),true,25).complete());
        var ambiguous=trace("ambiguous",1,0);
        ambiguous.samples()[27*3+2]=2;ambiguous.samples()[31*3+2]=2;
        var result=ProbePeakAnalysis.analyze(List.of(a,ambiguous),true,25);
        assertFalse(result.complete());assertTrue(Double.isNaN(result.timingRange()));
    }
    @Test void largeAmplitudeChangesDoNotSwitchHalfPeakToAnotherLobe() {
        var result=ProbePeakAnalysis.analyze(List.of(trace("weak",.2,0),trace("strong",2,0)),true,25);
        assertTrue(result.halfPeakComplete());assertEquals(0,result.halfPeakTimingRange(),1e-12);
        assertEquals(10,result.maximumAmplitude()/result.minimumAmplitude(),1e-12);
        assertTrue(result.peaks().stream().allMatch(p->p.halfPeakUpdate()<30));
    }
    @Test void capturedTraceAuditWhenExplicitlyRequested() throws Exception {
        var input=System.getProperty("probeTraceAudit");
        org.junit.jupiter.api.Assumptions.assumeTrue(input!=null);
        var byShell=new java.util.LinkedHashMap<Integer,java.util.List<ProbePeakAnalysis.Trace>>();
        var earliest=new java.util.HashMap<Integer,long[]>();
        var radii=new java.util.HashMap<Integer,Double>();
        try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(input))) {
            reader.readLine();String line,id=null;int shell=0;var samples=new java.util.ArrayList<Double>();
            while((line=reader.readLine())!=null) {
                String[] v=line.split(",");
                if(id!=null && !id.equals(v[2])) {
                    byShell.computeIfAbsent(shell,k->new java.util.ArrayList<>()).add(new ProbePeakAnalysis.Trace(id,samples.stream().mapToDouble(Double::doubleValue).toArray()));
                    samples.clear();
                }
                id=v[2];shell=Integer.parseInt(v[0]);radii.put(shell,Double.parseDouble(v[1]));
                samples.add(Double.parseDouble(v[3]));samples.add(Double.parseDouble(v[4]));samples.add(Double.parseDouble(v[5]));
                long[] arrivals=earliest.computeIfAbsent(shell,k->new long[]{Long.MAX_VALUE,Long.MAX_VALUE});
                for(int signal=0;signal<2;signal++){long arrival=Long.parseLong(v[8+signal]);if(arrival>0)arrivals[signal]=Math.min(arrivals[signal],arrival);}
                assertEquals("false",v[10]);
            }
            if(id!=null)byShell.computeIfAbsent(shell,k->new java.util.ArrayList<>()).add(new ProbePeakAnalysis.Trace(id,samples.stream().mapToDouble(Double::doubleValue).toArray()));
        }
        var output=new StringBuilder("shell,radius_bond_lengths,signal,timing_range_updates,min_amplitude,max_amplitude,window_start,window_end,status,half_peak_range_updates\n");
        for(var entry:byShell.entrySet())for(int signal=0;signal<2;signal++) {
            var result=ProbePeakAnalysis.analyze(entry.getValue(),signal==1,earliest.get(entry.getKey())[signal]);
            output.append(String.format(java.util.Locale.ROOT,"%d,%.9g,%s,%.9g,%.9g,%.9g,%.9g,%.9g,%s,%.9g%n",entry.getKey(),radii.get(entry.getKey()),signal==1?"gradient":"position",result.timingRange(),result.minimumAmplitude(),result.maximumAmplitude(),result.windowStart(),result.windowEnd(),result.status(),result.halfPeakTimingRange()));
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/matched-peak-audit.csv"),output);
        assertFalse(byShell.isEmpty());
    }
}
