package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.realization.session.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FaceHalfSpaceConvexityCalculatorTest {
    private final FaceHalfSpaceConvexityCalculator calculator = new FaceHalfSpaceConvexityCalculator();

    @Test void tetrahedronHasNoViolationsAndExcludesOwnFaceVertices() {
        List<Vector3d> p=List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(-1,-1,1));
        List<List<Integer>> f=List.of(List.of(0,2,1),List.of(0,1,3),List.of(1,2,3),List.of(2,0,3));
        var d=calculator.calculate(f,p,1e-9,2);
        assertEquals(FaceHalfSpaceConvexityStatus.SATISFIED,d.status());
        assertEquals(4,d.testedPairCount());
        assertEquals(0,d.violatingPairCount());
    }

    @Test void cubeHasNoViolationsAndAdjacentVerticesAreTested() {
        List<Vector3d> p=List.of(new Vector3d(-1,-1,-1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(1,1,-1),new Vector3d(-1,-1,1),new Vector3d(1,-1,1),new Vector3d(-1,1,1),new Vector3d(1,1,1));
        List<List<Integer>> f=List.of(List.of(0,1,3,2),List.of(4,6,7,5),List.of(0,4,5,1),List.of(2,3,7,6),List.of(0,2,6,4),List.of(1,5,7,3));
        var d=calculator.calculate(f,p,1e-9,2);
        assertEquals(FaceHalfSpaceConvexityStatus.SATISFIED,d.status());
        assertEquals(24,d.testedPairCount());
        assertEquals(0,d.violatingPairCount());
        List<Vector3d> indented=List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(.1,.1,.1));
        var concave=calculator.calculate(List.of(List.of(0,2,1),List.of(0,1,3),List.of(1,2,3),List.of(2,0,3)),indented,1e-9,2);
        assertTrue(concave.available());
    }

    @Test void translationRotationAndUniformScalePreserveNormalizedResult() {
        List<Vector3d> p=List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(-1,-1,1));
        List<List<Integer>> f=List.of(List.of(0,2,1),List.of(0,1,3),List.of(1,2,3),List.of(2,0,3));
        List<Vector3d> moved=p.stream().map(v->new Vector3d(v).rotateZ(.7).add(5,-3,8).mul(4)).toList();
        var a=calculator.calculate(f,p,0,2);var b=calculator.calculate(f,moved,0,8);
        assertEquals(a.objective(),b.objective(),1e-12);
        assertEquals(a.violatingPairCount(),b.violatingPairCount());
    }

    @Test void invalidPrerequisitesAreStructuredAndNeverZeroOrNaNResult() {
        var d=calculator.calculate(List.of(List.of(0,1,2)),List.of(new Vector3d(),new Vector3d(1,0,0),new Vector3d(0,1,0)),0,1);
        assertEquals(FaceHalfSpaceConvexityStatus.UNAVAILABLE_FACE_COMPLEX_INVALID,d.status());
        assertTrue(Double.isNaN(d.objective()));
    }
}
