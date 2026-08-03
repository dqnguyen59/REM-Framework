package remframework.geometry.discovery;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.experiment.ThreeDExperimentConditions;
import remframework.geometry.discovery.realization.session.*;

class Stage2MeasurementSemanticsTest {
    @Test void vertexAndEdgeDistanceRemainMeasuredWhenFaceNormalIsInvalid() {
        FixedDegreeGraph graph=graph();
        List<Vector3d> points=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(2,0,0),new Vector3d(0,0,1));
        var measurements=new Stage2MeasurementCalculator().calculate(graph,List.of(List.of(0,1,2)),points,ThreeDExperimentConditions.defaults(),7);
        assertEquals(MeasurementState.MEASURED,measurements.get("vertex-radius-equality").state());
        assertEquals(MeasurementState.MEASURED,measurements.get("edge-midpoint-radius-equality").state());
        assertEquals(MeasurementState.UNAVAILABLE_DEGENERATE_FACE,measurements.get("face-planarity").state());
        assertEquals(MeasurementState.UNAVAILABLE_INVALID_NORMAL,measurements.get("normal-center-alignment").state());
    }

    @Test void emptyEdgeSamplesAreExplicitAndDoNotProduceRatio() {
        FixedDegreeGraph graph=new FixedDegreeGraph(3,2,List.of(new DiscoveryConnection(0,1),new DiscoveryConnection(1,2),new DiscoveryConnection(2,0)),"cycle");
        var measurement=new Stage2MeasurementCalculator().calculate(graph,List.of(List.of(0,1,2)),List.of(new Vector3d(),new Vector3d(1,0,0),new Vector3d(0,1,0)),ThreeDExperimentConditions.defaults(),3).get("dihedral-equality");
        assertEquals(MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET,measurement.state());
        assertNull(measurement.rawValue());
        assertNull(measurement.ratio());
    }

    @Test void legacyNonFiniteValueIsNotConvertedToZero() {
        var measurement=ConstraintMeasurement.legacy("vertex-radius-equality",Double.NaN,1e-6,"length",2);
        assertEquals(MeasurementState.NUMERICALLY_INVALID_LEGACY_VALUE,measurement.state());
        assertNull(measurement.rawValue());
        assertEquals("LEGACY_NON_FINITE_VALUE",measurement.reasonCode());
    }

    @Test void presentationCarriesImmutableMeasurementSnapshotAndNoUnavailableRatio() {
        FixedDegreeGraph graph=graph();
        var measurements=new Stage2MeasurementCalculator().calculate(graph,List.of(List.of(0,1,2)),
                List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(2,0,0),new Vector3d(0,0,1)),ThreeDExperimentConditions.defaults(),11);
        var unavailable=measurements.get("face-planarity");
        assertNull(unavailable.ratio());assertEquals(0,unavailable.coordinateRevision()-11);
        assertThrows(UnsupportedOperationException.class,()->unavailable.affectedElementIds().add("face-99"));
    }

    private static FixedDegreeGraph graph(){return new FixedDegreeGraph(4,3,List.of(
            new DiscoveryConnection(0,1),new DiscoveryConnection(0,2),new DiscoveryConnection(0,3),
            new DiscoveryConnection(1,2),new DiscoveryConnection(1,3),new DiscoveryConnection(2,3)),"tetra");}
}
