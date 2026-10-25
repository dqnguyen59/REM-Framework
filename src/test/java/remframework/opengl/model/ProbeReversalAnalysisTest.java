package remframework.opengl.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProbeReversalAnalysisTest {
    private ProbeReversalAnalysis.Trace sinusoid(String id,double delay,double amplitude) {
        double[] samples=new double[200];
        for(int i=0;i<100;i++){samples[2*i]=i+1;samples[2*i+1]=amplitude*Math.sin(2*Math.PI*(i+1-delay)/20);}
        return new ProbeReversalAnalysis.Trace(id,samples);
    }
    @Test void distinguishesExactSynchronyFromSmallFractionalDelaysDespiteAmplitudeDifferences() {
        var synchronous=ProbeReversalAnalysis.analyze(List.of(sinusoid("a",0,1),sinusoid("b",0,.1)),true);
        assertTrue(synchronous.events().size()>=8);
        assertTrue(synchronous.events().stream().allMatch(ProbeReversalAnalysis.Event::complete));
        assertTrue(synchronous.events().stream().allMatch(e->e.range()<1e-10));
        var delayed=ProbeReversalAnalysis.analyze(List.of(sinusoid("a",0,1),sinusoid("b",.25,.1)),true);
        assertTrue(delayed.events().size()>=8);
        for(var event:delayed.events()) {
            assertTrue(event.complete());assertEquals(.25,event.range(),.004);
            assertEquals(2,event.matches().size());
        }
    }
    @Test void deadbandSuppressesSignChatterAndDoesNotInventAZeroPlateauTime() {
        var events=ProbeReversalAnalysis.crossings(new double[]{1,1,2,.001,3,-.001,4,.001,5,-1,6,-1},.01,true);
        assertEquals(1,events.size());assertEquals(-1,events.getFirst().direction());
        assertEquals(4+.001/1.001,events.getFirst().update(),1e-12);
        var plateau=ProbeReversalAnalysis.crossings(new double[]{1,1,2,0,3,0,4,-1},.01,true);
        assertTrue(Double.isNaN(plateau.getFirst().update()));
        var step=ProbeReversalAnalysis.crossings(new double[]{1,1,2,0,3,0,4,-1},.01,false);
        assertEquals(4,step.getFirst().update());
        var zero=ProbeReversalAnalysis.crossings(new double[]{1,1,2,0,3,-1},.01,true);
        assertEquals(2,zero.getFirst().update());
    }
    @Test void missingSignalsAndUnalignedUpdatesRemainUnavailable() {
        var a=sinusoid("a",0,1);var missing=sinusoid("b",0,0);
        var result=ProbeReversalAnalysis.analyze(List.of(a,missing),true);
        assertFalse(result.events().isEmpty());
        assertTrue(result.events().stream().noneMatch(ProbeReversalAnalysis.Event::complete));
        assertTrue(result.events().stream().allMatch(e->Double.isNaN(e.range())));
        var unaligned=sinusoid("b",0,1);unaligned.samples()[2]=99;
        assertTrue(ProbeReversalAnalysis.analyze(List.of(a,unaligned),true).events().isEmpty());
        assertTrue(ProbeReversalAnalysis.analyze(List.of(missing),true).events().isEmpty());
    }
}
