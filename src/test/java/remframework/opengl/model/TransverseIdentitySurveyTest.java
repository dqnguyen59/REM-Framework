package remframework.opengl.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TransverseIdentitySurveyTest {
    @Test void boundaryWindowUsesGraphRoundTripBoundWithOrderingMarginAndHardCap() {
        assertEquals(140,TransverseIdentitySurvey.reflectionFreeLimit(116,73,140));
        assertEquals(157,TransverseIdentitySurvey.reflectionFreeLimit(116,73,256));
        assertEquals(34,TransverseIdentitySurvey.reflectionFreeLimit(30,24,160));
        assertEquals(4096,TransverseIdentitySurvey.reflectionFreeLimit(Integer.MAX_VALUE,73,4096));
        assertTrue(TransverseIdentitySurvey.reflectionFreeLimit(10,24,160)<0,"Unsafe geometry must be rejected, not extended");
    }
    @TempDir Path directory;

    @Test void loadsLatestCompletedRunAndPreservesTruncationAndUnavailablePeaks() throws IOException {
        var completed=directory.resolve("transverse-100");
        writeSurvey(completed,true);
        writeSurvey(directory.resolve("transverse-200"),false);
        var survey=new TransverseIdentitySurvey();
        survey.loadLatestResults(directory);
        var view=survey.view();
        assertEquals(completed.toString(),view.location());
        assertEquals(26,view.completed());
        assertEquals(1,view.progress());
        assertFalse(view.running());
        assertFalse(view.absorber());
        assertTrue(view.traces().isEmpty());
        assertTrue(view.receiverDistances().isEmpty());
        var near=view.results().getFirst();
        assertEquals(2.25,near.range());
        assertFalse(near.provisional());
        assertEquals(8,near.targetDistanceMinimum());
        assertEquals(8,near.targetDistanceMaximum());
        assertEquals(0,near.targetDistanceRange());
        assertEquals(0,near.distances());
        assertTrue(Double.isNaN(near.distanceRange()));
        assertEquals(0,near.paths());
        assertTrue(Double.isNaN(near.weightedHopRange()));
        var far=view.results().getLast();
        assertEquals(.9,far.range(),1e-12);
        assertEquals(416,far.arrived());
        assertEquals(0,far.complete());
        assertTrue(far.provisional());
        assertEquals(0,far.peaks());
        assertTrue(Double.isNaN(far.peakSpread()));
    }

    @Test void rejectsPartialSweepRatherThanDisplayingItAsCompleted() throws IOException {
        writeSurvey(directory.resolve("transverse-100"),false);
        var survey=new TransverseIdentitySurvey();
        assertThrows(IOException.class,()->survey.loadLatestResults(directory));
        assertEquals(0,survey.view().completed());
        assertTrue(survey.view().results().isEmpty());
    }

    @Test void loadsMeasuredDistancesWithoutTreatingTruncatedPulseAsMissingGeometry() throws IOException {
        var path=directory.resolve("transverse-100");writeSurvey(path,true);
        Files.writeString(path.resolve("summary.csv"),"""
                all,-1,0,8,target_distance,416,416,8,8,8,0,0
                all,-1,0,8,probe_distance,416,416,7.999999999999998,8.000000000000002,8,4e-15,5e-14
                all,-1,1,36,target_distance,416,416,36,36,36,0,0
                all,-1,1,36,probe_distance,416,416,35.99,36.01,36,0.02,0.0555555556
                """,java.nio.file.StandardOpenOption.APPEND);
        var survey=new TransverseIdentitySurvey();survey.loadLatestResults(directory);
        var near=survey.view().results().getFirst();
        assertEquals(416,near.distances());
        assertTrue(near.distanceRange()>0&&near.distanceRange()<1e-13);
        var far=survey.view().results().getLast();
        assertTrue(far.provisional());
        assertEquals(416,far.distances());
        assertEquals(.02,far.distanceRange(),1e-12);
    }

    @Test void distanceUsesWeightedCoordinateAndIsInvariantToSourceTranslation() {
        var weights=java.util.List.of(new ProbeDefinition.InterpolationSource(new org.joml.Vector3i(),.5),
                new ProbeDefinition.InterpolationSource(new org.joml.Vector3i(1,0,0),.5));
        var positions=java.util.List.of(new org.joml.Vector3d(8,2,0),new org.joml.Vector3d(8,-2,0));
        assertEquals(8,TransverseIdentitySurvey.interpolatedDistance(new org.joml.Vector3d(),positions,weights),1e-14);
        var origin=new org.joml.Vector3d(10,-7,4);
        var translated=positions.stream().map(p->new org.joml.Vector3d(p).add(origin)).toList();
        assertEquals(8,TransverseIdentitySurvey.interpolatedDistance(origin,translated,weights),1e-14);
        assertNotEquals(positions.getFirst().length(),TransverseIdentitySurvey.interpolatedDistance(new org.joml.Vector3d(),positions,weights));
        assertEquals(8.5,TransverseIdentitySurvey.interpolatedDistance(new org.joml.Vector3d(-.5,0,0),positions,weights),1e-14);
    }

    @Test void historicalPathAuditLoadsWithoutOverwritingOriginalArrivalMeasurements() throws IOException {
        var path=directory.resolve("transverse-100");writeSurvey(path,true);
        var original=Files.readString(path.resolve("summary.csv"));
        Files.writeString(path.resolve("bond-path-summary.csv"),"""
                scope,orientation,shell,radius,metric,valid,total,min,max,mean,absolute_range,spread_percent
                all,-1,0,8,stencil_min_hops,416,416,7,14,10,7,70
                all,-1,0,8,stencil_max_hops,416,416,11,17,14,6,42.8
                all,-1,0,8,weighted_hops,416,416,10.048,13.856,12,3.808,31.7
                all,-1,1,36,stencil_min_hops,416,416,43,59,51,16,31.3
                all,-1,1,36,stencil_max_hops,416,416,47,65,56,18,32.1
                all,-1,1,36,weighted_hops,416,416,44.341,62.354,53,18.013,33.9
                """);
        var survey=new TransverseIdentitySurvey();survey.loadLatestResults(directory);
        var near=survey.view().results().getFirst();
        assertEquals(2.25,near.range());assertEquals(7,near.minimumHops());assertEquals(17,near.maximumHops());
        assertEquals(3.808,near.weightedHopRange(),1e-12);assertEquals(416,near.paths());
        assertTrue(survey.view().results().getLast().provisional());
        assertEquals(original,Files.readString(path.resolve("summary.csv")));
    }

    @Test void loadsEverySavedReceiverDirectionAndRetainsTinySignedDistanceDifferences() throws IOException {
        var path=directory.resolve("transverse-100");writeSurvey(path,true);
        Files.writeString(path.resolve("probes.csv"),"""
                orientation,shell,ray,radius,nx,ny,nz,probe_distance,lattice_center_distance
                0,0,0,8,1,0,0,7.999999999999998,8.5
                25,1,15,36,0,-1,0,36.01,35.5
                """);
        var survey=new TransverseIdentitySurvey();survey.loadLatestResults(directory);
        var distances=survey.view().receiverDistances();
        assertEquals(2,distances.size());
        var first=distances.getFirst();
        assertEquals(1,first.nx());assertEquals(0,first.ny());assertFalse(first.reconstructed());
        assertTrue(first.difference()<0&&first.difference()>-1e-13);
        assertEquals(8.5,first.latticeCenterDistance());
        var last=distances.getLast();
        assertEquals(25,last.orientation());assertEquals(15,last.ray());assertEquals(1,last.shell());
        assertEquals(.01,last.difference(),1e-12);
        assertEquals(35.5,last.latticeCenterDistance());
        assertThrows(UnsupportedOperationException.class,()->distances.clear());
    }

    @Test void historicalAuditProvidesActualDistancesAndReconstructsTheReceiverDirections() throws IOException {
        var path=directory.resolve("transverse-100");writeSurvey(path,true);
        Files.writeString(path.resolve("probes.csv"),"orientation,shell,ray,radius\n0,0,3,8\n");
        Files.writeString(path.resolve("probe-path-audit.csv"),"orientation,shell,ray,radius,probe_distance\n0,0,3,8,8.000000000000002\n");
        var survey=new TransverseIdentitySurvey();survey.loadLatestResults(directory);
        var point=survey.view().receiverDistances().getFirst();
        var expected=TransversePulseAnalysis.plane(remframework.geometry.discovery.research.diamond.DiamondDirectionalComparison.directions().getFirst(),16).get(3);
        assertTrue(point.reconstructed());assertTrue(point.difference()>0&&point.difference()<1e-13);
        assertTrue(Double.isNaN(point.latticeCenterDistance()));
        assertEquals(expected.x,point.nx());assertEquals(expected.y,point.ny());assertEquals(expected.z,point.nz());
    }

    private void writeSurvey(Path path,boolean complete) throws IOException {
        Files.createDirectories(path);
        Files.writeString(path.resolve("configuration.properties"),"""
                shells=2
                dimensionX=120
                dimensionY=120
                dimensionZ=120
                absorberEnabled=false
                absorberLayers=30
                rays=16
                periodUpdates=8
                pulseCycles=2
                amplitude=0.01
                recordingUpdates=94
                """);
        var csv=new StringBuilder("scope,orientation,shell,radius,metric,valid,total,min,max,mean,absolute_range,spread_percent\n");
        for(int orientation=0;orientation<(complete?26:25);orientation++)
            csv.append("plane,").append(orientation).append(",0,8,arrival,16,16,20,22.25,21,2.25,10.7\n");
        if(complete)csv.append("""
                all,-1,0,8,arrival,416,416,20,22.25,21,2.25,10.7
                all,-1,0,8,peak,416,416,0.01,0.02,0.015,0.01,66.7
                all,-1,0,8,rms,416,416,0.01,0.02,0.015,0.01,66.7
                all,-1,1,36,arrival,416,416,85.4,86.3,86,0.9,1.05
                all,-1,1,36,peak,0,416,NaN,NaN,NaN,NaN,NaN
                all,-1,1,36,rms,0,416,NaN,NaN,NaN,NaN,NaN
                """);
        Files.writeString(path.resolve("summary.csv"),csv);
    }
}
