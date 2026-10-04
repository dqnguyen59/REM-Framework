package remframework.geometry.discovery.research.tetrahedral;

import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.*;
import static org.junit.jupiter.api.Assertions.*;

class FaceSharingTetrahedralPreviewTest {
    @Test void previewUsesExactSharedTopologyAndPlacesApicesOutsideTheirFaces() {
        var model = GeometryDiscoveryExamples.create("tetrahedral-network",10);
        var snapshot = new FaceSharingTetrahedralExperiment().generate(1,8).generations().getLast();
        assertEquals(8,model.nodes().size());
        assertEquals(snapshot.edges(),model.connections());
        assertEquals(List.of(6,6,6,6,3,3,3,3),
            model.sortedNodes().stream().map(n -> n.neighborIds().size()).toList());
        for (var tetrahedron : snapshot.tetrahedra().subList(1,5)) {
            var face = new FaceSharingTetrahedralExperiment.Face(tetrahedron.a(),tetrahedron.b(),tetrahedron.c());
            int opposite=0;
            while(face.nodes().contains(opposite)) opposite++;
            var nodes=model.sortedNodes();
            Vector3d a=nodes.get(face.a()).position();
            Vector3d normal=new Vector3d(nodes.get(face.b()).position()).sub(a)
                .cross(new Vector3d(nodes.get(face.c()).position()).sub(a)).normalize();
            double inside=new Vector3d(nodes.get(opposite).position()).sub(a).dot(normal);
            double outside=new Vector3d(nodes.get(tetrahedron.d()).position()).sub(a).dot(normal);
            assertTrue(inside*outside<0);
            assertEquals(-inside,outside,1e-10);
        }
        for(var edge:model.connections())
            assertEquals(10,model.sortedNodes().get(edge.firstNodeId()).position()
                .distance(model.sortedNodes().get(edge.secondNodeId()).position()),1e-10);
        assertTrue(model.nodes().stream().allMatch(n -> n.position().isFinite()));
        var viewer=new GeometryDiscoveryVisualizer(model,GeometryDiscoverySettings.defaults(10),"preview");
        assertTrue(viewer.viewState().showConnections());
        assertTrue(viewer.viewState().showVertices());
        assertFalse(viewer.session().isRunning());
        var positions=viewer.session().model().sortedNodes().stream().map(n -> new Vector3d(n.position())).toList();
        viewer.updateFrame();
        assertEquals(positions,viewer.session().model().sortedNodes().stream().map(DiscoveryNode::position).toList());
    }
    @Test void recursivePreviewPreservesTopologyAndDisplayEdgeLengths() {
        for(int generation=0;generation<=4;generation++) {
            var preview=FaceSharingTetrahedralPreview.create(generation,1000,10);
            var snapshot=preview.topology().generations().getLast();
            assertEquals(snapshot.nodeCount(),preview.model().nodes().size());
            assertEquals(snapshot.edges(),preview.model().connections());
            assertEquals(snapshot.degrees(),preview.model().sortedNodes().stream().map(n -> n.neighborIds().size()).toList());
            for(var node:preview.model().nodes()) assertTrue(node.position().isFinite());
            for(var edge:preview.model().connections())
                assertEquals(10,preview.model().nodes().get(edge.firstNodeId()).position()
                    .distance(preview.model().nodes().get(edge.secondNodeId()).position()),1e-8);
            if(preview.overlappingTetrahedronPairs()>0 || preview.coincidentNodePairs()>0)
                assertTrue(preview.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.SELF_INTERSECTING && !s.mayContinue()));
        }
        assertEquals(56,FaceSharingTetrahedralPreview.create(3,1000,10).model().nodes().size());
        assertEquals(0,FaceSharingTetrahedralPreview.create(1,8,10).overlappingTetrahedronPairs());
    }

    @Test void limitsRetainLastWholeGenerationAndInvalidRequestsHaveStatus() {
        var limited=FaceSharingTetrahedralPreview.create(3,19,10);
        assertEquals(8,limited.model().nodes().size());
        assertEquals(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,limited.topology().status().code());
        for(var invalid:List.of(FaceSharingTetrahedralPreview.create(-1,1000,10),
                FaceSharingTetrahedralPreview.create(2,1001,10),
                FaceSharingTetrahedralPreview.create(2,1000,Double.NaN))) {
            assertNull(invalid.model());
            assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,invalid.statuses().getFirst().code());
        }
    }

    @Test void overlapDiagnosticDistinguishesInteriorIntersectionFromFaceTouching() {
        var seed=new FaceSharingTetrahedralExperiment.Tetrahedron(0,1,2,3,0);
        var child=new FaceSharingTetrahedralExperiment.Tetrahedron(0,1,2,4,1);
        var positions=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),
            new Vector3d(0,1,0),new Vector3d(0,0,1),new Vector3d(0,0,-1));
        assertFalse(FaceSharingTetrahedralPreview.interiorsOverlap(seed,child,positions,1e-9));
        positions.get(4).set(0,0,.5);
        assertTrue(FaceSharingTetrahedralPreview.interiorsOverlap(seed,child,positions,1e-9));
    }
    @Test void invalidDisplayScalesAreRejected() {
        for(double scale:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,() -> FaceSharingTetrahedralPreview.firstLayer(scale));
    }
}
