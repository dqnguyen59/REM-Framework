package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.realization.session.ContinuationConstraint;
import remframework.geometry.discovery.ui.ScientificDisplayNames;
import remframework.geometry.discovery.viewport.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScientificInspectionPresentationTest {
    @Test void centralizedNamesKeepInternalIdentifiersStable() {
        assertEquals("Edge Length", ScientificDisplayNames.constraint("edge-equality"));
        assertEquals("Face Distance", ScientificDisplayNames.constraint("face-plane-radius-equality"));
        assertEquals("Face-Center Distance", ScientificDisplayNames.constraint("face-centroid-radius-equality"));
        assertEquals("Final Validation", ScientificDisplayNames.continuation(ContinuationConstraint.JOINT_QUALIFICATION));
    }

    @Test void cubeFaceSeparatesPlaneAndCentroidDistancesAndHasOutwardNormal() {
        RenderModel model=cubeModel(7);
        SelectedElementMeasurements face=ElementMeasurementCalculator.measure(model,
                SelectedElement.face("face-top"),2,0.01);
        assertEquals(1,face.facePlaneDistance(),1e-12);
        assertEquals(1,face.faceCentroidDistance(),1e-12);
        assertEquals(4,face.area(),1e-12);
        assertEquals(8,face.perimeter(),1e-12);
        assertEquals("OUTWARD",face.state());
        assertEquals(1,face.outwardAlignment(),1e-12);
        assertTrue(face.angles().stream().allMatch(a->Math.abs(a-Math.PI/2)<1e-12));
    }

    @Test void edgeUsesRawLengthErrorAndUnclampedRatio() {
        SelectedElementMeasurements edge=ElementMeasurementCalculator.measure(cubeModel(1),
                SelectedElement.edge(0,1),1.5,.1);
        assertEquals(2,edge.length(),1e-12);
        assertEquals(.5,edge.error(),1e-12);
        assertEquals(5,edge.ratio(),1e-12);
        assertEquals(List.of(0,1),edge.vertexIds());
    }

    @Test void canonicalSelectionSurvivesSnapshotRevision() {
        ViewState view=new ViewState();SelectedElement selected=SelectedElement.face("face-top");
        view.setSelectedElement(selected);
        ElementMeasurementCalculator.measure(cubeModel(1),view.selectedElement(),2,.01);
        ElementMeasurementCalculator.measure(cubeModel(2),view.selectedElement(),2,.01);
        assertSame(selected,view.selectedElement());
        view.setSelectionFilters(new SelectionFilters(false,true,false,true));
        assertFalse(view.selectionFilters().vertices());
        assertTrue(view.selectionFilters().edges());
    }

    private static RenderModel cubeModel(long revision) {
        List<Vector3d> p=List.of(new Vector3d(-1,-1,1),new Vector3d(1,-1,1),
                new Vector3d(1,1,1),new Vector3d(-1,1,1),new Vector3d(-1,-1,-1),
                new Vector3d(1,-1,-1),new Vector3d(1,1,-1),new Vector3d(-1,1,-1));
        List<DiscoveryNode> nodes=java.util.stream.IntStream.range(0,p.size())
                .mapToObj(i->new DiscoveryNode(i,p.get(i),false)).toList();
        List<DiscoveryConnection> edges=List.of(new DiscoveryConnection(0,1),new DiscoveryConnection(1,2),
                new DiscoveryConnection(2,3),new DiscoveryConnection(3,0));
        GeometryDiscoveryModel geometry=new GeometryDiscoveryModel("cube","test",nodes,edges);
        DerivedFace top=new DerivedFace("face-top",List.of(0,1,2,3),true,true,0);
        return new RenderModel("cfg-test",geometry,DisplayTransform.calculate(p,4),false,
                revision,List.of(),List.of(top),p);
    }
}
