package remframework.lattice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.joml.Vector3i;
import java.nio.file.Path;
import remframework.settings.AppSettings;
import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import static org.junit.jupiter.api.Assertions.*;
class DiamondLatticeSelectionTest {
    @TempDir Path directory;
    @Test void rejectedDimensionsAndBudgetRetainLoadedSelectionThenAllowRetry() {
        var settings=new AppSettings();var manager=new LatticeManager(directory,settings);manager.initialize();
        String previous=manager.selected().identifier();
        assertFalse(manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID,new Vector3i(7,8,8),Long.MAX_VALUE));
        assertEquals(previous,manager.selected().identifier());assertEquals(previous,settings.getLatticeModelIdentifier());
        assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,manager.lastSelectionStatus().code());
        assertFalse(manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID,new Vector3i(8),0));
        assertEquals(previous,manager.selected().identifier());assertEquals(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,manager.lastSelectionStatus().code());
        assertTrue(manager.lastWarning().contains("Current model retained"));
        assertTrue(manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID,new Vector3i(8),Long.MAX_VALUE));
        assertEquals(LatticeManager.EXPERIMENTAL_DIAMOND_ID,manager.selected().identifier());assertNull(manager.lastSelectionStatus());assertEquals("",manager.lastWarning());
        assertTrue(manager.select(LatticeManager.SYSTEM_CUBIC_ID,new Vector3i(7,7,1),0));
        assertEquals(LatticeManager.SYSTEM_CUBIC_ID,manager.selected().identifier());
    }
}