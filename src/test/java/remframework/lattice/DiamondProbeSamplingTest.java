package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiamondProbeSamplingTest {
    @Test void xyShellsIncludeAxesAndDiagonalsWithoutZDirections() {
        var definitions=DiamondProbePlacement.create(nodes(),2,2,2,true);
        assertEquals(16,definitions.size());
        for(var definition:definitions) {
            assertEquals("Diamond physical XY",definition.getPlacement().measurementSpace());
            assertEquals(0,definition.getPlacement().targets().getFirst().requestedDirection().z);
        }
    }
    @Test void toleranceIsPositiveConfigurableAndIncludesBoundary() {
        assertEquals(1.0,ProbeArrivalMeasurement.DEFAULT_SPEED_SPREAD_TOLERANCE_PERCENT);
        assertEquals("Within tolerance (single run)",ProbeArrivalMeasurement.toleranceResult(1,1));
        assertEquals("Above tolerance",ProbeArrivalMeasurement.toleranceResult(1.001,1));
        assertEquals("Within tolerance (single run)",ProbeArrivalMeasurement.toleranceResult(1.001,2));
        assertEquals("Incomplete",ProbeArrivalMeasurement.toleranceResult(Double.NaN,1));
        assertThrows(IllegalArgumentException.class,()->ProbeArrivalMeasurement.toleranceResult(0,0));
        assertThrows(IllegalArgumentException.class,()->ProbeArrivalMeasurement.toleranceResult(0,Double.NaN));
    }
    @Test void incompleteArrivalsAreNotReportedAsZeroSpread() {
        var a=new ProbeArrivalMeasurement.Result(1,"a","X",10,20,0,0);
        var b=new ProbeArrivalMeasurement.Result(2,"b","Y",20,-1,0,0);
        assertEquals(66.6666666667,ProbeArrivalMeasurement.equalRadiusApparentSpread(List.of(a,b),false),1e-8);
        assertTrue(Double.isNaN(ProbeArrivalMeasurement.equalRadiusApparentSpread(List.of(a,b),true)));
        assertTrue(Double.isNaN(ProbeArrivalMeasurement.equalRadiusApparentSpread(List.of(),false)));
    }
    private Object[] nodes() {
        var model=new ExperimentalDiamondLatticeModel();var size=new Vector3i(16);
        var definitions=model.nodes(size);Object[] values=new Object[definitions.size()];
        for(int i=0;i<values.length;i++) {
            var d=definitions.get(i);Node n=new Node(null,null,new Vector3d(d.position()),new Vector3d(),1);
            n.setIndex(d.index());n.setGeometricDistanceImbalance(true);
            if(model.fixedBoundary(d,size))n.setType(EntityType.FIXED);values[i]=n;
        }
        return values;
    }
    private Map<Vector3i,Node> index(Object[] values) {
        Map<Vector3i,Node> map=new HashMap<>();for(Object value:values){Node n=(Node)value;map.put(new Vector3i(n.getIndex()),n);}return map;
    }
    @Test void shellsHaveExactPhysicalRadiusPositiveWeightsAndInteriorStencils() {
        var values=nodes();var map=index(values);var definitions=DiamondProbePlacement.create(values,2,2,2);
        assertEquals(52,definitions.size());Set<String> ids=new HashSet<>();
        for(var definition:definitions) {
            assertTrue(ids.add(definition.getId()));assertEquals(ProbeDetectorType.DIAMOND_INTERPOLATED,definition.getDetectorType());
            var target=definition.getPlacement().targets().getFirst();assertEquals(target.requestedRadius(),target.requestedCoordinate().length(),1e-12);
            assertEquals(1,target.requestedDirection().length(),1e-12);
            assertTrue(definition.getInterpolationSources().size()<=16);
            Vector3d reconstructed=new Vector3d();double sum=0;
            for(var source:definition.getInterpolationSources()) {
                assertTrue(source.weight()>=0);Node node=map.get(source.nodeIndex());assertFalse(node.isFixed());
                sum+=source.weight();reconstructed.fma(source.weight(),node.getEquilibriumPosition());
            }
            assertEquals(1,sum,1e-12);assertTrue(reconstructed.distance(target.requestedCoordinate())<1e-12);
        }
    }
    @Test void interpolationReproducesConstantAndAffinePhysicalFieldsWithoutMutatingNodes() {
        var values=nodes();var map=index(values);var definitions=DiamondProbePlacement.create(values,2,2,2);
        for(Object value:values) {
            Node n=(Node)value;Vector3d p=n.getEquilibriumPosition();
            n.setPosition(new Vector3d(p).add(affine(p)));n.updatePosition();
            n.getVelocity().set(affine(p));n.getAcceleration().set(3,-2,1);n.getGradientImbalance().set(affine(p));
        }
        for(var definition:definitions) {
            var sources=definition.getInterpolationSources();var bound=sources.stream().map(s->map.get(s.nodeIndex())).toList();
            var before=bound.stream().map(n->new Vector3d(n.getPosition())).toList();
            var observation=ProbeInterpolation.interpolate(bound,sources);var target=definition.getPlacement().targets().getFirst().requestedCoordinate();
            assertTrue(observation.position().distance(new Vector3d(target).add(affine(target)))<1e-12);
            assertTrue(observation.velocity().distance(affine(target))<1e-12);
            assertTrue(observation.gradientImbalance().distance(affine(target))<1e-12);
            assertTrue(observation.acceleration().distance(new Vector3d(3,-2,1))<1e-12);
            for(int i=0;i<bound.size();i++)assertEquals(before.get(i),bound.get(i).getPosition());
        }
    }
    private Vector3d affine(Vector3d p) {return new Vector3d(.001+.002*p.x-.003*p.y,.002+.001*p.z,-.001+.003*p.x);}
    @Test void unavailableOrInvalidShellsFailWithoutReturningPartialDefinitions() {
        Object[] values=nodes();
        assertThrows(IllegalArgumentException.class,()->DiamondProbePlacement.create(values,100,2,2));
        assertThrows(IllegalArgumentException.class,()->DiamondProbePlacement.create(values,2,0,2));
        assertThrows(IllegalArgumentException.class,()->DiamondProbePlacement.create(values,2,1,158));
        ((Node)values[0]).setGeometricDistanceImbalance(false);
        assertThrows(IllegalArgumentException.class,()->DiamondProbePlacement.create(values,2,1,1));
    }
}
