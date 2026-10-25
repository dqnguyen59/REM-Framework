package remframework.opengl.model;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProbeShellArrivalReportTest {
    @Test void fullCyclesContinueAfterThresholdCompletionWithoutTraceRecording() throws Exception {
        var probe=probe(0);var report=new ProbeShellArrivalReport();report.setCycleDeadband(.1);
        report.start(List.of(probe),.1,.1);
        for(int update=1;update<=70;update++) {
            double signal=Math.sin(2*Math.PI*(update-.25)/20);
            probe.getNode().getVelocity().set(0,0,signal);
            probe.getNode().setPosition(new org.joml.Vector3d(0,0,1));probe.getNode().updatePosition();
            probe.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d(0,0,signal));
            report.sample();
            if(update==1)assertTrue(report.rows().getFirst().snapshot().complete());
        }
        report.finish();assertFalse(report.hasTraces());assertEquals(2,report.cycleRows().size());
        assertTrue(report.cycleRows().stream().allMatch(r->r.result().cycle().complete()));
        assertEquals(20,report.cycleRows().getFirst().result().cycle().periodUpdates(),1e-10);
        var writer=new java.io.StringWriter();report.writeCycles(writer);
        assertEquals(3,writer.toString().lines().count());assertTrue(writer.toString().contains("cycle_completion_arrival_update"));
        report.clear();assertTrue(report.cycleRows().isEmpty());
        assertThrows(IllegalArgumentException.class,()->report.setCycleDeadband(0));
    }
    @Test void smallAmplitudeDifferenceCanSwitchArrivalToALaterLobe() throws Exception {
        var early=probe(0);var late=probe(0);
        var report=new ProbeShellArrivalReport();report.setRecordTraces(true);
        report.start(List.of(early,late),.1,1);
        for(int update=1;update<=25;update++) {
            // Same peak times, only a 2% difference in the first peak's amplitude.
            double pulse=update==3?1:update==20?1.2:0;
            for(var probe:List.of(early,late)) {
                probe.getNode().setPosition(new org.joml.Vector3d(0,0,.2));probe.getNode().updatePosition();
                probe.getOccupancies().getFirst().saveGradientToHistory(
                    new org.joml.Vector3d(0,0,pulse*(probe==early?1.01:.99)));
            }
            report.sample();
        }
        report.finish();
        var row=report.rows().getFirst();
        assertEquals(3,row.gradient().minimum());assertEquals(20,row.gradient().maximum());
        assertEquals(17,row.gradient().difference());
        var writer=new java.io.StringWriter();report.writeTraces(writer);
        assertEquals(51,writer.toString().lines().count()); // continues after shell completion at 20
        assertTrue(writer.toString().contains(",25,"));
        assertFalse(writer.toString().contains("true"));
        report.clear();assertFalse(report.hasTraces());
    }

    @Test void tracesRespectBaselineCapacityAndFinish() throws Exception {
        var probe=probe(0);
        probe.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d(0,0,5));
        var measurement=new ProbeArrivalMeasurement();measurement.start(List.of(probe),0,.1,.5);
        measurement.enableTrace(2);
        for(int update=1;update<=3;update++) {
            probe.getNode().getVelocity().set(0,0,-update);
            probe.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d(0,0,5+update));
            measurement.sample();
            measurement.sample(); // duplicate calls must not duplicate recorded updates
        }
        measurement.finish();
        probe.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d(0,0,99));
        measurement.sample();
        var writer=new java.io.StringWriter();measurement.writeTrace(writer,10);
        var lines=writer.toString().lines().toList();assertEquals(2,lines.size());
        var first=lines.getFirst().split(",");
        assertEquals(1,Double.parseDouble(first[5]));assertEquals("true",first[10]);
        assertEquals(-1,Double.parseDouble(first[11]));
        assertEquals(6,Double.parseDouble(first[12])); // signed absolute gradient, not baseline-subtracted norm
        assertEquals(0,Double.parseDouble(first[13])); // cycle summary remains independent
        assertEquals(2,Double.parseDouble(lines.getLast().split(",")[5]));
    }
    @Test void reversalExportUsesEveryRecordedProbeAndClearsCachedResults() throws Exception {
        var a=probe(0);var b=probe(0);
        var report=new ProbeShellArrivalReport();report.setRecordTraces(true);
        report.start(List.of(a,b),.1,.1);
        for(int update=1;update<=60;update++) {
            for(var probe:List.of(a,b)) {
                double signal=Math.sin(2*Math.PI*(update-(probe==a?0:.25))/20);
                probe.getNode().getVelocity().set(0,0,signal);
                probe.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d(0,0,-signal));
            }
            report.sample();
        }
        assertTrue(report.reversalRows().isEmpty());
        report.finish();assertEquals(3,report.reversalRows().size());
        var velocity=report.reversalRows().getFirst().result();
        assertTrue(velocity.events().stream().allMatch(ProbeReversalAnalysis.Event::complete));
        assertEquals(.25,velocity.events().getFirst().range(),.004);
        var writer=new java.io.StringWriter();report.writeReversals(writer);
        assertTrue(writer.toString().contains("velocity_z"));assertTrue(writer.toString().contains("No nonzero signed signal"));
        report.clear();assertTrue(report.reversalRows().isEmpty());
    }
    private static ProbeShellArrivalReport.Row spreadRow(int shell,double radius,double spread) {
        var original=row(shell,radius,100,100,100,100);
        var timing=new ProbeShellArrivalReport.Timing(2,2,100,100,100,spread);
        return new ProbeShellArrivalReport.Row(shell,radius,original.snapshot(),timing,timing);
    }
    @Test void spreadTrendAcceptsDecliningFlatAndIncreasingSlopesUsingDistance() {
        var rows=List.of(spreadRow(0,10,4),spreadRow(1,30,3),spreadRow(2,70,1));
        var trend=ProbeShellArrivalReport.spreadTrend(rows,false);
        assertTrue(trend.valid());assertEquals(-.05,trend.slope(),1e-12);assertEquals(4.5,trend.intercept(),1e-12);
        assertTrue(ProbeShellArrivalReport.csv(rows).contains("spread_slope_pp_per_bond"));
        var flat=ProbeShellArrivalReport.spreadTrend(List.of(spreadRow(0,10,1),spreadRow(1,40,1)),true);
        assertTrue(flat.valid());assertEquals(0,flat.slope());
        var increasing=ProbeShellArrivalReport.spreadTrend(List.of(spreadRow(0,10,1),spreadRow(1,40,4)),true);
        assertTrue(increasing.valid());assertEquals(.1,increasing.slope(),1e-12);
        assertFalse(ProbeShellArrivalReport.spreadTrend(List.of(spreadRow(0,10,1),spreadRow(1,10,2)),true).valid());
        assertFalse(ProbeShellArrivalReport.spreadTrend(List.of(row(0,10,-1,-1,-1,-1),spreadRow(1,40,1)),true).valid());
    }
    @Test void allShellsShareBaselineAndKeepRecordingAfterInnerShellCompletes() {
        var inner=probe(0);var outer=probe(1);
        var report=new ProbeShellArrivalReport();report.start(List.of(inner,outer),.1,.1);
        inner.getNode().setPosition(new org.joml.Vector3d(0,0,1));inner.getNode().updatePosition();
        inner.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d(0,0,1));
        outer.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d());
        report.sample();
        assertTrue(report.rows().getFirst().snapshot().complete());
        assertFalse(report.rows().getLast().snapshot().complete());
        outer.getNode().setPosition(new org.joml.Vector3d(0,0,1));outer.getNode().updatePosition();
        outer.getOccupancies().getFirst().saveGradientToHistory(new org.joml.Vector3d(0,0,1));
        report.sample();report.finish();
        assertEquals(1,report.rows().getFirst().position().minimum());
        assertEquals(2,report.rows().getLast().position().minimum());
        assertTrue(report.rows().stream().allMatch(r->r.snapshot().complete() && !r.snapshot().running()));
        report.clear();assertTrue(report.rows().isEmpty());
    }
    private static Probe probe(int shell) {
        var node=new remframework.opengl.model.entity.Node(null,null,new org.joml.Vector3d(),new org.joml.Vector3d(),1);
        node.setIndex(shell,0,0);
        var target=new ProbeDefinition.Target(shell,0,"XY",10+shell*10,new org.joml.Vector3d(),new org.joml.Vector3d(1,0,0),10+shell*10,0,null,0,-1);
        return new Probe(new ProbeDefinition("shell-"+shell,node.getIndex(),new org.joml.Vector4f(1),new ProbeDefinition.Placement(List.of(target),0,"XY")),node,null);
    }
    private static ProbeShellArrivalReport.Row row(int shell,double radius,long p1,long p2,long g1,long g2) {
        var snapshot=new ProbeArrivalMeasurement.Snapshot(shell,1e-4,1e-5,false,1000,List.of(
            new ProbeArrivalMeasurement.Result(1,"a","ray 1",p1,g1,0,0),
            new ProbeArrivalMeasurement.Result(2,"b","ray 2",p2,g2,0,0)));
        return new ProbeShellArrivalReport.Row(shell,radius,snapshot,ProbeShellArrivalReport.timing(snapshot,false),ProbeShellArrivalReport.timing(snapshot,true));
    }
    @Test void reportsExtremaDifferencesAndFitsActualDistancesWithFreeIntercept() {
        var rows=List.of(row(0,10,28,32,38,42),row(1,30,68,72,98,102),row(2,80,168,172,248,252));
        assertEquals(28,rows.getFirst().position().minimum());
        assertEquals(32,rows.getFirst().position().maximum());
        assertEquals(4,rows.getFirst().position().difference());
        assertEquals(30,rows.getFirst().position().mean());
        var p=ProbeShellArrivalReport.fit(rows,false);var g=ProbeShellArrivalReport.fit(rows,true);
        assertEquals(2,p.slope(),1e-12);assertEquals(10,p.intercept(),1e-12);assertEquals(.5,p.speed(),1e-12);
        assertEquals(3,g.slope(),1e-12);assertEquals(10,g.intercept(),1e-12);
        var csv=ProbeShellArrivalReport.csv(rows);
        assertTrue(csv.contains("difference_updates"));assertEquals(7,csv.lines().count());
        assertTrue(csv.contains("position"));assertTrue(csv.contains("gradient"));
    }
    @Test void incompleteArrivalsAreNotZeroTimesOrIncludedInFit() {
        var incomplete=row(0,10,10,-1,-1,-1);
        assertFalse(incomplete.position().complete());assertEquals(1,incomplete.position().detected());
        assertEquals(-1,incomplete.gradient().minimum());assertTrue(Double.isNaN(incomplete.gradient().mean()));
        var fit=ProbeShellArrivalReport.fit(List.of(incomplete,row(1,20,30,30,40,40)),false);
        assertFalse(fit.valid());assertEquals(1,fit.shells());
        assertTrue(ProbeShellArrivalReport.csv(List.of(incomplete)).contains("incomplete"));
    }
}
