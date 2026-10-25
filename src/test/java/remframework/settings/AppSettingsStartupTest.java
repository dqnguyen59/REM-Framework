package remframework.settings;

import org.ini4j.Wini;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsStartupTest {
    @Test void explicitPacketPolarizationPersistsAndOldAutomaticSettingsBecomeZ() {
        var settings=new AppSettings();
        assertEquals(remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.Z,settings.getDiamondProbeExcitationAxis());
        for(var axis:remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.values()) {
            if(axis==remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.AUTO)continue;
            settings.setDiamondProbeExcitationAxis(axis);
            var ini=new Wini();settings.saveToFile(ini,"",0);
            var restored=new AppSettings();restored.loadFromFile(ini,"",0);
            assertEquals(axis,restored.getDiamondProbeExcitationAxis());
        }
        var ini=new Wini();ini.put("DiamondProbe","excitationAxis","invalid");settings.loadFromFile(ini,"",0);
        assertEquals(remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.Z,settings.getDiamondProbeExcitationAxis());
        ini.put("DiamondProbe","excitationAxis","AUTO");settings.loadFromFile(ini,"",0);
        assertEquals(remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.Z,settings.getDiamondProbeExcitationAxis());
        assertThrows(IllegalArgumentException.class,()->settings.setDiamondProbeExcitationAxis(remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.AUTO));
    }
    @Test
    void xyDirectionCountDefaultsTo32AndPersists() {
        var settings=new AppSettings();
        assertEquals(32,settings.getDiamondProbeXYDirections());
        for(int count:new int[]{8,16,32,64}) {
            settings.setDiamondProbeXYDirections(count);
            var ini=new Wini();settings.saveToFile(ini,"",0);
            var restored=new AppSettings();restored.loadFromFile(ini,"",0);
            assertEquals(count,restored.getDiamondProbeXYDirections());
            var directions=remframework.opengl.model.DiamondProbePlacement.xyDirections(count);
            assertEquals(count,directions.size());
            for(int i=0;i<count;i++) {
                assertEquals(1,directions.get(i).length(),1e-12);
                assertEquals(0,directions.get(i).z,1e-12);
                assertEquals(Math.cos(2*Math.PI/count),directions.get(i).dot(directions.get((i+1)%count)),1e-12);
            }
        }
        assertThrows(IllegalArgumentException.class,()->settings.setDiamondProbeXYDirections(12));
    }
    @Test
    void probeAmplitudeDefaultsToTenAndRoundTrips() {
        var settings = new AppSettings();
        assertEquals(10.0, settings.getDiamondProbeAmplitude());
        settings.setDiamondProbeAmplitude(25);
        var ini = new Wini();
        settings.saveToFile(ini, "", 0);
        var restored = new AppSettings();
        restored.loadFromFile(ini, "", 0);
        assertEquals(25, restored.getDiamondProbeAmplitude());
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> restored.setDiamondProbeAmplitude(invalid));
    }
    @Test
    void probeRecordingLimitIsSavedAndValidated() {
        var settings = new AppSettings();
        assertEquals(200, settings.getDiamondProbeMaximumUpdates());
        settings.setDiamondProbeMaximumUpdates(600);
        var ini = new Wini();
        settings.saveToFile(ini, "", 0);
        var restored = new AppSettings();
        restored.loadFromFile(ini, "", 0);
        assertEquals(600, restored.getDiamondProbeMaximumUpdates());
        assertThrows(IllegalArgumentException.class, () -> restored.setDiamondProbeMaximumUpdates(0));
        assertThrows(IllegalArgumentException.class, () -> restored.setDiamondProbeMaximumUpdates(-1));
    }
    @Test
    void dimensionsRoundTripIndependently() {
        var settings = new AppSettings();
        settings.rememberDimensions(new Vector3i(400, 300, 4));
        var ini = new Wini();
        settings.saveToFile(ini, "", 0);
        var restored = new AppSettings();
        restored.loadFromFile(ini, "", 0);
        assertEquals(new Vector3i(400, 300, 4), restored.startupDimensions(true));
    }

    @Test
    void legacyOddDefaultsHaveSafeDiamondFallbackButCubicIsUnchanged() {
        var settings = new AppSettings();
        settings.rememberDimensions(new Vector3i(151, 151, 3));
        assertEquals(new Vector3i(80, 80, 64), settings.startupDimensions(true));
        assertEquals(new Vector3i(151, 151, 3), settings.startupDimensions(false));
    }

    @Test
    void selectedModelAndSavedSlabSurviveReload() {
        var ini = new Wini();
        var settings = new AppSettings();
        settings.setLatticeModelIdentifier("research:diamond-mechanics");
        settings.rememberDimensions(new Vector3i(400, 400, 4));
        settings.saveToFile(ini, "", 0);
        var restored = new AppSettings();
        restored.loadFromFile(ini, "", 0);
        assertEquals("research:diamond-mechanics", restored.getLatticeModelIdentifier());
        assertEquals(new Vector3i(400, 400, 4), restored.startupDimensions(true));
    }
}
