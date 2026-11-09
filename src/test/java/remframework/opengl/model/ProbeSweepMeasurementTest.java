package remframework.opengl.model;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProbeSweepMeasurementTest {
    @Test void sharedWindowIgnoresDetectionAndUsesSameCompleteCycles() {
        var measurement=new ProbeSweepMeasurement(20,2,new Vector3d(1,0,0),20);
        for(int t=1;t<=60;t++)measurement.sample(t,false,new Vector3d(0,Math.sin(2*Math.PI*t/20),Math.cos(2*Math.PI*t/20)));
        var result=measurement.snapshot();assertTrue(result.complete());assertEquals(20,result.startUpdate());
        assertEquals(40,result.samples());assertEquals(1,result.rms(),1e-12);
        assertEquals(0,result.meanY(),1e-12);assertEquals(0,result.meanZ(),1e-12);
        var interrupted=new ProbeSweepMeasurement(20,2,new Vector3d(1,0,0),20);
        interrupted.sample(1,false,new Vector3d());interrupted.finish();assertFalse(interrupted.snapshot().complete());
    }
    @Test void waitsForDetectionThenCollectsExactlyConfiguredWholePeriods() {
        var measurement=new ProbeSweepMeasurement(20,3,new Vector3d(1,0,0));
        for(int t=1;t<=80;t++) {
            double angle=2*Math.PI*t/20;
            var signal=new Vector3d(2,Math.sin(angle),Math.cos(angle));
            measurement.sample(t,t>=7,signal);measurement.sample(t,t>=7,signal);
            if(t<80)assertFalse(measurement.snapshot().complete());
        }
        var r=measurement.snapshot();assertTrue(r.complete());assertEquals(20,r.startUpdate());
        assertEquals(80,r.endUpdate());assertEquals(60,r.samples());assertEquals(3,r.completedCycles());
        assertEquals(2,r.meanX(),1e-12);assertEquals(0,r.meanY(),1e-12);assertEquals(0,r.meanZ(),1e-12);
        assertEquals(Math.sqrt(5),r.rms(),1e-12);assertEquals(2,r.radialMean(),1e-12);
        assertEquals(1,r.perpendicularRms(),1e-12);
        measurement.sample(81,true,new Vector3d(99));assertEquals(r,measurement.snapshot());
    }
    @Test void boundaryLimitAndGapsRemainIncompleteOrInvalid() {
        var shortRun=new ProbeSweepMeasurement(20,2,new Vector3d(0,0,1));
        for(int t=1;t<=50;t++)shortRun.sample(t,t>=1,new Vector3d(1));
        shortRun.finish();assertEquals("incomplete",shortRun.snapshot().status());
        assertEquals(1,shortRun.snapshot().completedCycles());assertEquals(30,shortRun.snapshot().samples());
        var gap=new ProbeSweepMeasurement(20,1,new Vector3d(0,0,1));
        gap.sample(1,true,new Vector3d());gap.sample(3,true,new Vector3d());
        assertEquals("invalid_or_missing_sample",gap.snapshot().status());
        var absent=new ProbeSweepMeasurement(20,1,new Vector3d(0,0,1));
        absent.sample(1,false,new Vector3d());absent.finish();assertEquals(-1,absent.snapshot().startUpdate());
    }
}
