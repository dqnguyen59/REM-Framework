package remframework.lattice;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.Node;

import static org.junit.jupiter.api.Assertions.*;

class DiamondLocalImbalanceTest {
    private static final double EPS = 1e-12;
    private static final double[][] DIRECTIONS = {
            {1,1,1}, {1,-1,-1}, {-1,1,-1}, {-1,-1,1}
    };

    private Node star(Quaterniond rotation, Vector3d translation, int shift) {
        Node center = node(new Vector3d(translation));
        center.setGeometricDistanceImbalance(true);
        for (int i=0;i<4;i++) {
            double[] d=DIRECTIONS[i];
            Vector3d position=rotation.transform(new Vector3d(d[0],d[1],d[2]).normalize()).add(translation);
            center.setNeighbor(Neighbor.values()[(i+shift)%6],node(position));
        }
        return center;
    }

    private Node node(Vector3d position) {
        return new Node(null,null,position,new Vector3d(),1);
    }

    private void radialChange(Node center, int slot, double change) {
        Node neighbor=center.getNeighbors()[slot];
        Vector3d direction=new Vector3d(neighbor.getPosition()).sub(center.getPosition()).normalize();
        neighbor.setPosition(new Vector3d(neighbor.getPosition()).fma(change,direction));
        neighbor.updatePosition();
    }

    @Test void equilibriumAndUniformExpansionHaveNoBulkDirectionalBias() {
        Node center=star(new Quaterniond(),new Vector3d(),0);
        assertEquals(new Vector3d(),center.readGradientImbalance());
        for(int i=0;i<4;i++)radialChange(center,i,.2);
        assertTrue(center.readGradientImbalance().length()<EPS);
    }

    @Test void extensionAndCompressionHaveKnownDirectionAndMagnitude() {
        for (double extension : new double[]{-.2,.2}) {
            Node center=star(new Quaterniond(),new Vector3d(),0);
            Vector3d expected=new Vector3d(1,1,1).normalize().mul(extension);
            radialChange(center,0,extension);
            assertTrue(center.readGradientImbalance().distance(expected)<EPS);
            assertTrue(center.measureGradientImbalance().distance(expected)<EPS);
        }
    }

    @Test void arbitraryRotationsTranslationsAndSlotPermutationsPreserveTheRule() {
        Node base=star(new Quaterniond(),new Vector3d(),0);
        radialChange(base,0,.12);radialChange(base,1,-.08);radialChange(base,2,.03);
        Vector3d expected=base.readGradientImbalance();
        for(int i=0;i<40;i++) {
            Quaterniond rotation=new Quaterniond().rotationXYZ(.17*i,.31*i,-.23*i);
            int shift=i%6;
            Node transformed=star(rotation,new Vector3d(7,-3,11),shift);
            radialChange(transformed,shift,.12);
            radialChange(transformed,(1+shift)%6,-.08);
            radialChange(transformed,(2+shift)%6,.03);
            assertTrue(transformed.readGradientImbalance().distance(rotation.transform(new Vector3d(expected)))<EPS);
        }
    }

    @Test void smallCenterDisplacementHasTetrahedralLinearResponse() {
        for(double[] d : new double[][]{{1,0,0},{0,1,0},{0,0,1},{1,2,3},{-2,1,4}}) {
            Node center=star(new Quaterniond(),new Vector3d(),0);
            Vector3d displacement=new Vector3d(d[0],d[1],d[2]).normalize().mul(1e-5);
            center.setPosition(displacement);center.updatePosition();
            assertTrue(center.readGradientImbalance().distance(new Vector3d(displacement).mul(-4.0/3))<2e-10);
        }
    }

    @Test void readIsNonmutatingAndIncompleteNeighborhoodsUseOnlyPresentBonds() {
        Node center=star(new Quaterniond(),new Vector3d(),0);
        radialChange(center,0,.1);
        center.measureGradientImbalance();
        Vector3d cached=new Vector3d(center.getGradientImbalance());
        Vector3d position=new Vector3d(center.getPosition());
        center.setNeighbor(Neighbor.values()[0],null);
        assertTrue(center.readGradientImbalance().length()<EPS);
        assertEquals(cached,center.getGradientImbalance());assertEquals(position,center.getPosition());
        assertEquals(new Vector3d(),node(new Vector3d()).readBondDistanceImbalance());
    }

    @Test void degenerateAndNonfiniteBondsAreExplicitlyRejected() {
        Node center=star(new Quaterniond(),new Vector3d(),0);
        center.getNeighbors()[0].setPosition(new Vector3d());center.getNeighbors()[0].updatePosition();
        assertThrows(IllegalStateException.class,center::readGradientImbalance);
        center.getNeighbors()[0].setPosition(new Vector3d(Double.NaN,0,0));center.getNeighbors()[0].updatePosition();
        assertThrows(IllegalStateException.class,center::readGradientImbalance);
    }

    @Test void cubicOppositeSlotRuleRemainsUnchanged() {
        Node center=node(new Vector3d());
        center.setNeighbor(Neighbor.values()[0],node(new Vector3d(2,0,0)));
        center.setNeighbor(Neighbor.values()[3],node(new Vector3d(-1,0,0)));
        assertFalse(center.isGeometricDistanceImbalance());
        assertEquals(new Vector3d(1,0,0),center.readGradientImbalance());
        assertEquals(new Vector3d(1,0,0),center.measureGradientImbalance());
    }
}
