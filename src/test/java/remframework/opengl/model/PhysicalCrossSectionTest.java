package remframework.opengl.model;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.lattice.ExperimentalDiamondLatticeModel;
import remframework.settings.Settings;
import static org.junit.jupiter.api.Assertions.*;
class PhysicalCrossSectionTest {
    @Test void planesSelectPhysicalCoordinatesAndRespectThickness() {
        var center=new Vector3d(10,20,30);
        assertTrue(PhysicalCrossSection.contains(new Vector3d(100,-50,30),center,Settings.SliceType.physicalXY,1));
        assertFalse(PhysicalCrossSection.contains(new Vector3d(10,20,31),center,Settings.SliceType.physicalXY,1));
        assertTrue(PhysicalCrossSection.contains(new Vector3d(100,20.5,-100),center,Settings.SliceType.physicalXZ,1));
        assertTrue(PhysicalCrossSection.contains(new Vector3d(10.5,100,-100),center,Settings.SliceType.physicalYZ,1));
        assertThrows(IllegalArgumentException.class,()->PhysicalCrossSection.contains(center,center,Settings.SliceType.physicalXY,Double.NaN));
    }
    @Test void diamondSelectionDiffersFromStorageSliceAndKeepsMechanicsTopology() {
        var nodes=new ExperimentalDiamondLatticeModel().nodes(new Vector3i(10));
        long physical=nodes.stream().filter(n->PhysicalCrossSection.contains(n.position(),new Vector3d(),Settings.SliceType.physicalXY,1)).count();
        assertTrue(physical>0);assertTrue(physical<nodes.size());
        assertTrue(nodes.stream().anyMatch(n->PhysicalCrossSection.contains(n.position(),new Vector3d(),Settings.SliceType.physicalXY,1)!=(n.index().z==5)));
        assertEquals(1000,nodes.size());assertEquals(729,nodes.stream().filter(n->n.neighbors().size()==4).count());
    }
    @Test void settingsChangeTriggersVisibilityRefreshAndRejectsInvalidThickness() {
        var settings=new Settings();settings.setPhysicalSliceThickness(2);assertEquals(2,settings.getPhysicalSliceThickness());
        settings.setPhysicalSliceThickness(-1);assertEquals(2,settings.getPhysicalSliceThickness());
        for(var plane:new Settings.SliceType[]{Settings.SliceType.physicalXY,Settings.SliceType.physicalXZ,Settings.SliceType.physicalYZ})
            assertEquals(plane,Settings.SliceType.fromLabel(plane.getLabel()));
    }
}
