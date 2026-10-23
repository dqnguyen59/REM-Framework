package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondDirectionalComparisonTest {
    @Test void directionsCoverAxesFaceAndBodyDiagonalsAndBothSigns() {
        var directions=DiamondDirectionalComparison.directions();assertEquals(26,directions.size());
        for(var d:directions) {assertEquals(1,d.length(),1e-12);assertTrue(directions.stream().anyMatch(other->new Vector3d(d).add(other).length()<1e-12));}
    }
    @Test void receiversHaveNormalizedWeightsAndUseMovableInteriorNodes() {
        var model=DiamondReference.create(3,10);var receiver=DiamondDirectionalComparison.receiver(model,new Vector3d(20,20,20),"probe");
        assertEquals(8,receiver.nodes().size());assertEquals(1,receiver.weights().stream().mapToDouble(Double::doubleValue).sum(),1e-12);
        for(int id:receiver.nodes()) assertEquals(4,model.nodes().get(id).neighborIds().size());
    }
}
