package remframework.lattice;

import org.junit.jupiter.api.Test;
import org.joml.*;
import remframework.opengl.model.entity.*;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.Settings.NodeFormulaVersion;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompactDiamondMechanicsTest {
    @Test void topologyMatchesHistoricalDefinitionsAndPrimitiveStateMatchesNodeUpdates() {
        var size=new Vector3i(8,10,6);var definitions=new ExperimentalDiamondLatticeModel().nodes(size);
        var topology=new DiamondTopology(size.x,size.y,size.z);var compact=new CompactDiamondMechanics(size.x,size.y,size.z);
        Node[] nodes=new Node[topology.size()];int[] links=new int[4];var random=new java.util.Random(2347);
        for(int id=0;id<nodes.length;id++) {
            var d=definitions.get(id);assertEquals(d.position(),topology.position(id,new Vector3d()));assertEquals(d.index(),topology.index(id,new Vector3i()));
            int degree=topology.neighbors(id,links);assertEquals(d.neighbors().size(),degree);
            for(int slot=0;slot<degree;slot++)assertEquals(Integer.parseInt(d.neighbors().get(slot)),links[slot]);
            nodes[id]=new Node(null,null,new Vector3d(d.position()),new Vector3d(),1);if(degree<4)nodes[id].setType(EntityType.FIXED);
            var p=new Vector3d(d.position()).add(random.nextDouble()*.001,random.nextDouble()*.001,random.nextDouble()*.001);
            nodes[id].setPosition(p);nodes[id].updatePosition();compact.setPosition(id,p);
            var v=new Vector3d(random.nextDouble()*.0001,random.nextDouble()*.0001,random.nextDouble()*.0001);nodes[id].getVelocity().set(v);compact.setVelocity(id,v);
        }
        for(int id=0;id<nodes.length;id++){int degree=topology.neighbors(id,links);for(int slot=0;slot<degree;slot++)nodes[id].setNeighbor(Neighbor.values()[slot],nodes[links[slot]]);}
        var parameters=new Node.FrameParameters(NodeFormulaVersion.v_0_1,.0625,.9,1,0,false);
        for(int step=0;step<40;step++) {
            var force=new Vector3d(.00003,-.00002,.00001);nodes[123].getForce().set(force);compact.setForce(123,force);
            for(var node:nodes)node.updateNodeProperties(parameters);compact.step(.0625,.9);
            for(int id=0;id<nodes.length;id++){nodes[id].updatePosition();assertEquals(nodes[id].getPosition(),compact.position(id,new Vector3d()));assertEquals(nodes[id].getVelocity(),compact.velocity(id,new Vector3d()));assertEquals(nodes[id].getAcceleration(),compact.acceleration(id,new Vector3d()));}
        }
        assertEquals(136L*nodes.length,compact.stateBytes());
        for(int id:new int[]{0,123,200,nodes.length-1})assertTrue(nodes[id].readBondDistanceImbalance().distance(compact.imbalance(id,new Vector3d()))<1e-14);
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var minimum=definitions.getFirst().position();
        var legacy=new remframework.geometry.discovery.research.diamond.DiamondReceiverSampling.Sampler(nodes.length,
            id->rotation.transform(new Vector3d(definitions.get(id).position()).sub(minimum)),id->true,1);
        var direct=compact.sampler(true);
        var point=rotation.transform(new Vector3d(.1,.2,.3).sub(minimum));
        var expected=legacy.receiver(point,"legacy");var actual=direct.receiver(point,"direct");
        assertEquals(expected.nodes(),actual.nodes());assertEquals(expected.weights(),actual.weights());
    }
    @Test void optionalFieldsPreserveInitializationAndRenderingCanBeRecreated() {
        var node=new Node(null,null,new Vector3d(1,2,3),new Vector3d(),1);
        node.initPosition(new Vector3d(4,5,6));node.setPosition(new Vector3d(7,8,9));
        assertEquals(new Vector3d(7,8,9),node.getEField().p);assertEquals(new Vector3d(4,5,6),node.getEField().pBuf);
        assertEquals(new Vector3d(4,5,6),node.getBField().p);
        node.getEField().v.set(3);node.resetAuxiliaryFieldMotion();assertEquals(new Vector3d(),node.getEField().v);
        node.updateMatrix();var before=new Matrix4f(node.getTransformMatrix4f());node.releaseRenderMatrices();assertEquals(before,node.getTransformMatrix4f());
    }
    @Test void overriddenLightMatrixInitializationDoesNotRecurse() {
        var model=new remframework.opengl.model.ObjFileModel(0,0);
        var light=new remframework.opengl.model.lights.Light(null,model,null,new Vector3d(1,2,3),new Vector3d(),1);
        assertNotNull(light.getTransformMatrix());assertNotNull(light.getCalculatedTransformMatrix4f());
        light.updateMatrix();assertTrue(light.getTransformMatrix().isFinite());
    }
}
