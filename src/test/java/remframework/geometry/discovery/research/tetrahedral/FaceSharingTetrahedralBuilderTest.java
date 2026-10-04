package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.*;
import static org.junit.jupiter.api.Assertions.*;

class FaceSharingTetrahedralBuilderTest {
    @Test void startsWithRegularSeedAndCompletesEachLayerBeforeNext() {
        var builder=new FaceSharingTetrahedralBuilder(10,100);
        assertEquals(4,builder.snapshot().nodeCount());
        assertEquals(6,builder.snapshot().edges().size());
        assertEquals(0,builder.completedGeneration());
        assertEquals(1,builder.currentLayer());
        List<FaceSharingTetrahedralExperiment.Snapshot> expected=
            new FaceSharingTetrahedralExperiment().generate(3,56).generations();
        int[] completedNodeCounts={4,8,20,56};
        for(int count=4;count<=56;count++) {
            assertEquals(count,builder.snapshot().nodeCount());
            assertEquals(6+3*(count-4),builder.snapshot().edges().size());
            assertEquals(count-3,builder.snapshot().tetrahedra().size());
            checkLengths(builder);
            for(int g=0;g<completedNodeCounts.length;g++)
                if(count==completedNodeCounts[g]) {
                    assertEquals(expected.get(g),builder.snapshot());
                    assertEquals(g,builder.completedGeneration());
                    assertEquals(g+1,builder.currentLayer());
                    assertEquals(0,builder.additionsInLayer());
                    assertEquals(4*(int)Math.pow(3,g),builder.layerCapacity());
                }
            if(count<56) {
                int layer=builder.currentLayer();
                assertTrue(builder.addNode());
                assertEquals(layer,builder.snapshot().nodeBirthGenerations().getLast());
            }
        }
    }
    @Test void removalRestoresExactFaceQueueAcrossLayerBoundaries() {
        var builder=new FaceSharingTetrahedralBuilder(10,100);
        List<FaceSharingTetrahedralExperiment.Snapshot> snapshots=new ArrayList<>();
        List<String> progress=new ArrayList<>();
        for(int n=0;n<20;n++) {
            snapshots.add(builder.snapshot());progress.add(builder.progress());
            builder.addNode();
        }
        for(int n=19;n>=0;n--) {
            assertTrue(builder.removeNode());
            assertEquals(snapshots.get(n),builder.snapshot());
            assertEquals(progress.get(n),builder.progress());
        }
        assertFalse(builder.removeNode());
        assertEquals(4,builder.snapshot().nodeCount());
        assertTrue(builder.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.INVALID_PARAMETERS));
        builder.addNode();assertEquals(snapshots.get(1),builder.snapshot());
    }
    @Test void rescalingPreservesTopologyAndUndoUsesNewLength() {
        var builder=new FaceSharingTetrahedralBuilder(10,100);
        for(int n=0;n<9;n++) builder.addNode();
        var snapshot=builder.snapshot();
        var positions=builder.model().nodes().stream().map(n -> new Vector3d(n.position())).toList();
        assertTrue(builder.setBaseLength(7.5));
        assertEquals(snapshot,builder.snapshot());
        var nodes=builder.model().sortedNodes();
        for(int a=0;a<nodes.size();a++) for(int b=a+1;b<nodes.size();b++)
            assertEquals(.75*positions.get(a).distance(positions.get(b)),
                nodes.get(a).position().distance(nodes.get(b).position()),1e-9);
        checkLengths(builder);
        builder.removeNode();checkLengths(builder);
        builder.addNode();assertEquals(snapshot,builder.snapshot());checkLengths(builder);
        assertFalse(builder.setBaseLength(Double.NaN));
        assertEquals(7.5,builder.baseLength());
        checkLengths(builder);
    }
    @Test void nodeLimitAndResetKeepSeedAndRetainReasons() {
        var builder=new FaceSharingTetrahedralBuilder(12,6);
        builder.addNode();builder.addNode();var snapshot=builder.snapshot();
        assertFalse(builder.addNode());assertEquals(snapshot,builder.snapshot());
        assertEquals(2,builder.additionsInLayer());
        assertTrue(builder.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED && !s.mayContinue()));
        assertTrue(builder.removeNode());assertTrue(builder.addNode());
        builder.reset();assertEquals(4,builder.snapshot().nodeCount());
        assertEquals(12,builder.baseLength());assertFalse(builder.canRemove());checkLengths(builder);
    }
    @Test void incrementalViewerKeepsLengthsAndRefreshesSelectionAndDiagnostics() {
        var initial=FaceSharingTetrahedralPreview.firstLayer(10);
        var viewer=new GeometryDiscoveryVisualizer(initial,GeometryDiscoverySettings.defaults(10),"test");
        viewer.enableTetrahedralBuilder(10,100);
        assertEquals(4,viewer.session().model().nodes().size());
        assertTrue(viewer.result().validation().isValid());
        viewer.addTetrahedralNode();viewer.viewState().setSelectedNodeId(4);
        viewer.removeTetrahedralNode();
        assertTrue(viewer.viewState().selectedNodeId()<4);
        viewer.setTetrahedralBaseLength(8);
        assertEquals(8,viewer.session().settings().targetDistance());
        var positions=viewer.session().model().nodes().stream().map(n -> new Vector3d(n.position())).toList();
        viewer.start();viewer.singleStep();viewer.runToConvergence();viewer.updateFrame();
        assertFalse(viewer.session().isRunning());
        assertEquals(positions,viewer.session().model().nodes().stream().map(DiscoveryNode::position).toList());
        viewer.addTetrahedralNode();viewer.reset();
        assertEquals(4,viewer.session().model().nodes().size());assertEquals(8,viewer.tetrahedralBuilder().baseLength());
        for(int n=0;n<52;n++) viewer.addTetrahedralNode();
        assertTrue(viewer.researchStatuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.SELF_INTERSECTING));
    }
    private static void checkLengths(FaceSharingTetrahedralBuilder builder) {
        var model=builder.model();
        for(var edge:model.connections())
            assertEquals(builder.baseLength(),model.nodes().get(edge.firstNodeId()).position()
                .distance(model.nodes().get(edge.secondNodeId()).position()),builder.baseLength()*1e-9);
    }
}
