package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.*;
import static org.junit.jupiter.api.Assertions.*;

class FaceSharingTetrahedralSharingTest {
    private FaceSharingTetrahedralBuilder atReuseFace(int limit) {
        var builder=new FaceSharingTetrahedralBuilder(10,limit,true);
        for(int i=0;i<7;i++) assertTrue(builder.addNode());
        assertEquals(11,builder.snapshot().nodeCount());
        return builder;
    }

    @Test void reuseAddsConnectionsToEightWithoutAllocatingElevenOrRenumbering() {
        var builder=atReuseFace(100);
        var before=builder.snapshot();var positions=builder.model().nodes().stream().map(n -> new Vector3d(n.position())).toList();
        assertTrue(builder.addNode());
        assertEquals(11,builder.snapshot().nodeCount());
        assertEquals(28,builder.snapshot().edges().size());
        assertEquals(9,builder.snapshot().tetrahedra().size());
        assertEquals(18,builder.snapshot().cycleRank());
        assertEquals(15,builder.snapshot().attachmentReconnections());
        assertEquals(new FaceSharingTetrahedralExperiment.Tetrahedron(0,1,5,8,2),builder.snapshot().tetrahedra().getLast());
        assertEquals(List.of(0,1,4,5),builder.model().nodes().get(8).neighborIds().stream().sorted().toList());
        assertTrue(builder.model().nodes().stream().noneMatch(n -> n.id()==11));
        assertEquals(before.nodeBirthGenerations(),builder.snapshot().nodeBirthGenerations());
        assertTrue(FaceSharingTetrahedralClosure.validateBoundary(builder.snapshot()).isEmpty());
        assertTrue(builder.statuses().stream().anyMatch(s -> s.explanation().contains("Reused existing node 8")));
        assertTrue(builder.removeNode());
        assertEquals(before,builder.snapshot());
        assertEquals(positions,builder.model().nodes().stream().map(DiscoveryNode::position).toList());
        assertTrue(builder.addNode());assertEquals(11,builder.snapshot().nodeCount());
    }

    @Test void wholeNetworkDeformsTowardTargetAndChangingTargetRecalculates() {
        var builder=atReuseFace(100);builder.addNode();
        assertTrue(builder.deformation().stationary());
        assertTrue(builder.deformation().maximumRelativeEdgeError()>0);
        assertTrue(builder.deformation().maximumRelativeEdgeError()<.05);
        var topology=builder.snapshot();
        assertTrue(builder.model().nodes().stream().allMatch(n -> n.position().isFinite()));
        builder.setBaseLength(5);
        assertEquals(topology,builder.snapshot());
        assertTrue(builder.deformation().stationary());
        assertEquals(5,builder.baseLength());
        assertTrue(builder.deformation().maximumRelativeEdgeError()<.05);
        builder.removeNode();
        assertEquals(11,builder.snapshot().nodeCount());
        assertEquals(27,builder.snapshot().edges().size());
        assertTrue(builder.deformation().stationary());
    }

    @Test void reuseAllowedAtNodeLimitAndNextLayerWaitsForFullFrontier() {
        var limited=atReuseFace(11);
        assertTrue(limited.canAdd());assertTrue(limited.addNode());
        assertFalse(limited.canAdd());assertFalse(limited.addNode());
        assertTrue(limited.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED));
        var builder=atReuseFace(30);builder.addNode();
        for(int i=0;i<8;i++) assertTrue(builder.addNode());
        assertEquals(14,builder.snapshot().nodeCount());
        assertEquals(17,builder.snapshot().tetrahedra().size());
        assertEquals(2,builder.completedGeneration());
        assertEquals(3,builder.currentLayer());
        assertEquals(0,builder.additionsInLayer());
        assertTrue(FaceSharingTetrahedralClosure.validateBoundary(builder.snapshot()).isEmpty());
        assertTrue(builder.addNode());
        assertEquals(3,builder.snapshot().nodeBirthGenerations().getLast());
    }

    @Test void secondGapReusesNineInsteadOfCreatingThirteen() {
        var builder=atReuseFace(100);
        for(int i=0;i<3;i++) assertTrue(builder.addNode());
        var before=builder.snapshot();
        assertEquals(Set.of(9),FaceSharingTetrahedralBuilder.closureCandidates(before,
            new FaceSharingTetrahedralExperiment.Face(0,2,6)));
        assertTrue(builder.addNode());
        assertEquals(before.nodeCount(),builder.snapshot().nodeCount());
        assertEquals(new FaceSharingTetrahedralExperiment.Tetrahedron(0,2,6,9,2),builder.snapshot().tetrahedra().getLast());
        assertTrue(builder.model().nodes().get(9).neighborIds().contains(6));
        assertTrue(FaceSharingTetrahedralClosure.validateBoundary(builder.snapshot()).isEmpty());
        assertTrue(builder.removeNode());assertEquals(before,builder.snapshot());
    }

    @Test void growthCanMakeSeedVertexInteriorAndContinueBeyondThirtyEightNodes() {
        var builder=new FaceSharingTetrahedralBuilder(10,100,true);
        for(int i=0;i<90 && builder.snapshot().nodeCount()<=38;i++) {
            assertTrue(builder.addNode(), () -> builder.statuses().toString());
            assertTrue(FaceSharingTetrahedralClosure.validateBoundary(builder.snapshot()).isEmpty());
        }
        assertTrue(builder.snapshot().nodeCount()>38);
        var after=builder.snapshot();
        assertTrue(builder.removeNode());assertTrue(builder.addNode());assertEquals(after,builder.snapshot());
    }

    @Test void viewerSupportsSoftSharingWhileOriginalModeRemainsAvailable() {
        var viewer=new GeometryDiscoveryVisualizer(FaceSharingTetrahedralPreview.firstLayer(10),
            GeometryDiscoverySettings.defaults(10),"test");
        viewer.enableTetrahedralBuilder(10,100,true);
        for(int i=0;i<8;i++) viewer.addTetrahedralNode();
        assertEquals(11,viewer.session().model().nodes().size());
        assertTrue(viewer.tetrahedralBuilder().sharingMode());
        assertEquals(List.of(0,1,4,5),viewer.session().model().nodes().get(8).neighborIds().stream().sorted().toList());
        viewer.removeTetrahedralNode();assertEquals(27,viewer.session().model().connections().size());
        viewer.reset();assertEquals(4,viewer.session().model().nodes().size());
    }
}
