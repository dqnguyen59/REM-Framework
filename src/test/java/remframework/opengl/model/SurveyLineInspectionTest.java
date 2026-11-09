package remframework.opengl.model;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SurveyLineInspectionTest {
    @TempDir Path directory;

    @Test void overlayKeepsSelectionColorsAcrossRefreshesAndClearsAfterLatticeReplacement() {
        var scene=new Scene();var lattice=scene.getLattice();
        var display=new remframework.settings.AppSettings();scene.setAppSettings(display);
        var node=new remframework.opengl.model.entity.Node(null,null,new org.joml.Vector3d(),new org.joml.Vector3d(),1);
        lattice.setNodeArray(new Object[]{node});
        var target=new ProbeDefinition.Target(0,0,"test",2,new org.joml.Vector3d(2,0,0),new org.joml.Vector3d(1,0,0),2,0,null,0,-1);
        var probe=ProbeDefinition.interpolated("test",ProbeDetectorType.DIAMOND_INTERPOLATED,new org.joml.Vector4f(1),
                new ProbeDefinition.Placement(java.util.List.of(target),0,"test"),java.util.List.of(new ProbeDefinition.InterpolationSource(new org.joml.Vector3i(),1)));
        var selection=new SurveyLineInspection.Selection("test",0,0,new org.joml.Vector3d(),java.util.List.of(probe),java.util.List.of());
        scene.showSurveyLine(selection,0,0);
        for(int i=0;i<3;i++) {
            var marks=scene.getSurveyLineOverlays();assertFalse(marks.isEmpty());
            marks.forEach(remframework.opengl.model.entity.Entity::updateMatrix);
            assertEquals(1,marks.size(),"Only the virtual probe has a separate marker");
            assertTrue(marks.stream().allMatch(m->m.getScale()==node.getScale()));
            var color=marks.getFirst().getMaterial().getAmbientColor();
            assertEquals(1,color.x,1e-6);assertEquals(.1,color.y,1e-6);assertEquals(.8,color.z,1e-6);
            assertNotNull(node.getSurveyHighlightColor());node.updateMatrix();
            assertEquals(1,node.getMaterial().getAmbientColor().y,1e-6,"The actual source node is yellow");
            assertEquals(1,scene.getSurveySegments().size());
            assertEquals(new org.joml.Vector3d(2,0,0),scene.getSurveySegments().getFirst().end());
        }
        assertEquals(new org.joml.Vector3d(),node.getVelocity());
        display.setSurveyDisplayOpacity(.25,.5,.75,.4);
        var receivers=java.util.List.of(new TransverseIdentitySurvey.ReceiverDistance(0,1,0,1,0,0,2,2,2,false));
        scene.showSurveyLine(selection,0,0,receivers,java.util.List.of(),false);
        var marks=scene.getSurveyLineOverlays();marks.forEach(remframework.opengl.model.entity.Entity::updateMatrix);
        assertEquals(.25,marks.getFirst().getMaterial().getDiffuseColor().w,1e-6);
        node.updateMatrix();assertEquals(.75,node.getMaterial().getDiffuseColor().w,1e-6);
        lattice.setNodeArray(new Object[]{node});assertTrue(scene.getSurveyLineOverlays().isEmpty());assertTrue(scene.getSurveySegments().isEmpty());
        assertNull(node.getSurveyHighlightColor());
    }

    @Test void connectedLineEndpointsFollowActualNodePositions() {
        var a=new remframework.opengl.model.entity.Node(null,null,new org.joml.Vector3d(),new org.joml.Vector3d(),1);
        var b=new remframework.opengl.model.entity.Node(null,null,new org.joml.Vector3d(1,0,0),new org.joml.Vector3d(),1);
        var edge=new Scene.SurveySegment(a,b);
        b.setPosition(new org.joml.Vector3d(2,3,4));b.updatePosition();
        assertEquals(new org.joml.Vector3d(2,3,4),edge.end());assertEquals(new org.joml.Vector3d(),edge.start());
        assertEquals(new org.joml.Vector3d(1,0,0),b.getEquilibriumPosition());
    }

    private void writeProbes() throws Exception {
        Files.writeString(directory.resolve("probes.csv"),"orientation,ray,shell,radius,arrival_half_peak,peak,status\n"
                +"2,3,1,12,15.625,0.003,complete\n2,3,0,8,9.125,0.004,complete\n"
                +"1,3,0,8,99,7,complete\n2,4,0,8,98,6,complete\n");
    }

    @Test void joinsExactOrientationRayShellAndPreservesFractionalTimes() throws Exception {
        writeProbes();
        Files.writeString(directory.resolve("traces.csv"),"orientation,shell,ray,update,parallel_displacement,other_displacement_squared\n"
                +"2,0,3,2,0.004,0\n2,0,3,0.5,-0.002,0\n2,1,3,3,0.003,0\n"
                +"1,0,3,1,99,0\n2,0,4,1,98,0\n");
        var result=SurveyLineInspection.readMeasurements(directory,2,3);
        assertEquals(2,result.size());assertEquals(0,result.getFirst().shell());
        assertEquals(9.125,result.getFirst().arrival());assertEquals(.004,result.getFirst().peak());
        assertEquals(java.util.List.of(new SurveyLineInspection.Point(.5,-.002),new SurveyLineInspection.Point(2,.004)),result.getFirst().trace());
        assertEquals(.003,result.getLast().trace().getFirst().value());
    }

    @Test void missingWaveformStaysUnavailableInsteadOfSynthesizingSamples() throws Exception {
        writeProbes();var result=SurveyLineInspection.readMeasurements(directory,2,3);
        assertEquals(2,result.size());assertTrue(result.stream().allMatch(m->m.trace().isEmpty()));
        assertTrue(SurveyLineInspection.readMeasurements(directory,25,0).isEmpty());
    }

    @Test void reconstructsStencilsAboutSavedSourceWithoutChangingNodeMotion() throws Exception {
        var model=new remframework.lattice.ExperimentalDiamondLatticeModel();var size=new org.joml.Vector3i(16);
        var definitions=model.nodes(size);var nodes=new Object[definitions.size()];
        remframework.opengl.model.entity.Node source=null;
        for(int i=0;i<nodes.length;i++) {
            var d=definitions.get(i);var n=new remframework.opengl.model.entity.Node(null,null,new org.joml.Vector3d(d.position()),new org.joml.Vector3d(),1);
            n.setIndex(d.index());n.setGeometricDistanceImbalance(true);n.getVelocity().set(1,2,3);
            if(model.fixedBoundary(d,size))n.setType(remframework.opengl.model.entity.EntityType.FIXED);
            nodes[i]=n;if(n.getIndex().equals(new org.joml.Vector3i(8)))source=n;
        }
        Files.writeString(directory.resolve("configuration.properties"),"sourceIndexX=8\nsourceIndexY=8\nsourceIndexZ=8\nfirstRadius=2\nspacing=1\n");
        writeProbes();
        var distances=java.util.List.of(new TransverseIdentitySurvey.ReceiverDistance(0,2,3,1,0,0,2,2,2,false));
        var view=new TransverseIdentitySurvey.ViewState(false,26,0,0,0,0,0,1,false,0,16,16,16,2,16,8,2,.01,directory.toString(),java.util.List.of(),java.util.List.of(),distances);
        var selection=SurveyLineInspection.load(view,2,3,nodes);
        assertEquals(source.getEquilibriumPosition(),selection.source());assertEquals(2,selection.probes().size());
        for(var probe:selection.probes()) {
            var sum=new org.joml.Vector3d();double weights=0;
            for(var stencil:probe.getInterpolationSources()) {
                var n=java.util.Arrays.stream(nodes).map(remframework.opengl.model.entity.Node.class::cast).filter(v->v.getIndex().equals(stencil.nodeIndex())).findFirst().orElseThrow();
                sum.fma(stencil.weight(),n.getEquilibriumPosition());weights+=stencil.weight();
            }
            assertEquals(1,weights,1e-12);assertTrue(sum.distance(probe.getPlacement().targets().getFirst().requestedCoordinate())<1e-12);
        }
        for(Object value:nodes)assertEquals(new org.joml.Vector3d(1,2,3),((remframework.opengl.model.entity.Node)value).getVelocity());
        var wrong=new TransverseIdentitySurvey.ViewState(false,26,0,0,0,0,0,1,false,0,120,120,120,2,16,8,2,.01,directory.toString(),java.util.List.of(),java.util.List.of(),distances);
        assertThrows(java.io.IOException.class,()->SurveyLineInspection.load(wrong,2,3,nodes));
    }
}
