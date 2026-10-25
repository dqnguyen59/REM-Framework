package remframework.opengl.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProbeCycleMeasurementTest {
    @Test void auditExistingSignedRecordingWhenExplicitlyRequested() throws Exception {
        String path=System.getProperty("probeTraceAudit");
        org.junit.jupiter.api.Assumptions.assumeTrue(path!=null);
        var output=new StringBuilder("probe_id,signal,cycle_start_update,cycle_middle_update,cycle_end_update,period_updates,cycle_completion_arrival_update,deadband,status\n");
        var detectors=new java.util.LinkedHashMap<String,ProbeCycleMeasurement[]>();
        long last=0;
        try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(path))) {
            var header=reader.readLine();assertTrue(header.contains("velocity_z"));
            String line;
            while((line=reader.readLine())!=null) {
                var values=line.split(",");var id=values[2];
                var pair=detectors.computeIfAbsent(id,k->new ProbeCycleMeasurement[]{new ProbeCycleMeasurement(1e-9),new ProbeCycleMeasurement(1e-9)});
                long update=Long.parseLong(values[3]);last=Math.max(last,update);
                pair[0].sample(update,Double.parseDouble(values[11]));pair[1].sample(update,Double.parseDouble(values[12]));
            }
        }
        for(var entry:detectors.entrySet())for(int signal=0;signal<2;signal++) {
            var detector=entry.getValue()[signal];detector.finish();var r=detector.snapshot();
            if(r.complete()){assertTrue(r.periodUpdates()>0);assertTrue(r.arrivalUpdate()<=last);assertTrue(r.endUpdate()<=r.arrivalUpdate());}
            output.append(String.format(java.util.Locale.ROOT,"%s,%s,%.17g,%.17g,%.17g,%.17g,%d,%.17g,%s%n",entry.getKey(),signal==0?"velocity_z":"imbalance_z",r.startUpdate(),r.middleUpdate(),r.endUpdate(),r.periodUpdates(),r.arrivalUpdate(),r.deadband(),r.status()));
        }
        assertFalse(detectors.isEmpty());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/full-cycle-recording-audit.csv"),output);
    }
    @Test void capturesIndependentStartEndPeriodAndConfirmationUpdate() {
        var detector=new ProbeCycleMeasurement(.1);
        double[] values={-1,-.01,.01,1,.01,-.01,-1,-.01,.01,1};
        for(int i=0;i<values.length;i++) {
            detector.sample(i+1,values[i]);detector.sample(i+1,values[i]);
            if(i<9)assertFalse(detector.snapshot().complete());
        }
        var result=detector.snapshot();assertTrue(result.complete());
        assertEquals(2.5,result.startUpdate());assertEquals(5.5,result.middleUpdate());
        assertEquals(8.5,result.endUpdate());assertEquals(6,result.periodUpdates());assertEquals(10,result.arrivalUpdate());
        detector.sample(11,-99);detector.finish();assertEquals(result,detector.snapshot());
    }
    @Test void doesNotCountHalfCycleOrSubthresholdSignChatter() {
        var detector=new ProbeCycleMeasurement(.1);
        for(int i=1;i<=20;i++)detector.sample(i,i%2==0?.01:-.01);
        assertTrue(Double.isNaN(detector.snapshot().startUpdate()));
        detector.finish();assertEquals("incomplete",detector.snapshot().status());
        var half=new ProbeCycleMeasurement(.1);
        for(int i=1;i<=20;i++)half.sample(i,Math.sin(2*Math.PI*(i-.25)/20));
        half.finish();assertFalse(half.snapshot().complete());
    }
    @Test void flagsZeroPlateausAndMissingSamplesWithoutSkippingToALaterCycle() {
        var ambiguous=new ProbeCycleMeasurement(.1);
        double[] values={-1,0,0,1};
        for(int i=0;i<values.length;i++)ambiguous.sample(i+1,values[i]);
        assertEquals("ambiguous_zero_crossing",ambiguous.snapshot().status());
        var gap=new ProbeCycleMeasurement(.1);gap.sample(1,-1);gap.sample(3,1);
        assertEquals("invalid_or_missing_sample",gap.snapshot().status());
        assertThrows(IllegalArgumentException.class,()->new ProbeCycleMeasurement(Double.NaN));
    }
    @Test void knownFractionalDelayChangesCycleTimesButNotPeriod() {
        var a=new ProbeCycleMeasurement(.01);var b=new ProbeCycleMeasurement(.01);
        for(int i=1;i<=65;i++) {
            a.sample(i,Math.sin(2*Math.PI*(i-.1)/20));
            b.sample(i,Math.sin(2*Math.PI*(i-.35)/20));
        }
        assertTrue(a.snapshot().complete());assertTrue(b.snapshot().complete());
        assertEquals(20,a.snapshot().periodUpdates(),1e-10);assertEquals(20,b.snapshot().periodUpdates(),1e-10);
        assertEquals(.25,b.snapshot().endUpdate()-a.snapshot().endUpdate(),.004);
    }
}
