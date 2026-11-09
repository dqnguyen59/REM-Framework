package remframework.settings;

import org.ini4j.Wini;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsStartupTest {
    @Test void connectionDisplayDefaultsAndConfigurationSurviveReload() {
        var settings=new AppSettings();settings.loadFromFile(new Wini(),"",0);
        assertFalse(settings.isShowLatticeConnections());assertEquals(.15,settings.getLatticeConnectionOpacity());
        settings.setLatticeConnectionDisplay(true,.27);var ini=new Wini();settings.saveToFile(ini,"",0);
        var restored=new AppSettings();restored.loadFromFile(ini,"",0);
        assertTrue(restored.isShowLatticeConnections());assertEquals(.27,restored.getLatticeConnectionOpacity());
        restored.setLatticeConnectionDisplay(true,2);assertEquals(1,restored.getLatticeConnectionOpacity());
        restored.setLatticeConnectionDisplay(false,Double.NaN);assertEquals(.15,restored.getLatticeConnectionOpacity());
    }
    @Test void surveyOpacityPersistsAndOldSettingsUseTransparentProbes() {
        var settings=new AppSettings();settings.loadFromFile(new Wini(),"",0);
        assertEquals(.2,settings.getSurveyProbeOpacity());assertEquals(1,settings.getSurveyHighlightOpacity());
        settings.setSurveyDisplayOpacity(.15,.65,.8,.45);
        var ini=new Wini();settings.saveToFile(ini,"",0);
        var restored=new AppSettings();restored.loadFromFile(ini,"",0);
        assertEquals(.15,restored.getSurveyProbeOpacity());assertEquals(.65,restored.getSurveyHighlightOpacity());
        assertEquals(.8,restored.getSurveySourceOpacity());assertEquals(.45,restored.getSurveyLineOpacity());
    }

    @Test void surveyOpacityClampsInvalidRangesAndRejectsNonfiniteValues() {
        var settings=new AppSettings();settings.setSurveyDisplayOpacity(-1,2,Double.NaN,Double.POSITIVE_INFINITY);
        assertEquals(0,settings.getSurveyProbeOpacity());assertEquals(1,settings.getSurveyHighlightOpacity());
        assertEquals(1,settings.getSurveySourceOpacity());assertEquals(1,settings.getSurveyLineOpacity());
        var ini=new Wini();ini.put("SurveyDisplay","probeOpacity","NaN");ini.put("SurveyDisplay","lineOpacity",-5);
        settings.loadFromFile(ini,"",0);assertEquals(.2,settings.getSurveyProbeOpacity());assertEquals(0,settings.getSurveyLineOpacity());
    }
    @Test void rotatingOrbitConfigurationSurvivesReload() throws Exception {
        var settings=new AppSettings();
        var ini=new org.ini4j.Wini();
        settings.setOrbitConfiguration(96,8,6,.25);settings.setOrbitWarmupCycles(3);settings.saveToFile(ini,"",0);
        settings.setOrbitConfiguration(64,4,4,1);settings.loadFromFile(ini,"",0);
        assertEquals(96,settings.getOrbitFastPeriod());assertEquals(8,settings.getOrbitRatio());
        assertEquals(6,settings.getOrbitWidth());assertEquals(.25,settings.getOrbitAmplitude());
        assertEquals(3,settings.getOrbitWarmupCycles());
    }

    @Test void completeExperimentControlsSurviveReload() {
        var settings=new AppSettings();settings.setDiamondProbeDetection(4,-9,-10,.5);
        settings.setDiamondProbeShellLayout(12,4,3,false);settings.setDiamondProbeDiagnostics(1e-7,true);
        var ini=new Wini();settings.saveToFile(ini,"",0);
        var restored=new AppSettings();restored.loadFromFile(ini,"",0);
        assertEquals(4,restored.getDiamondProbeSourceWidth());assertEquals(-9,restored.getDiamondProbePositionExponent());
        assertEquals(-10,restored.getDiamondProbeGradientExponent());assertEquals(.5,restored.getDiamondProbeTolerance());
        assertEquals(12,restored.getDiamondProbeFirstRadius());assertEquals(4,restored.getDiamondProbeShellSpacing());
        assertEquals(3,restored.getDiamondProbeShellCount());assertFalse(restored.isDiamondProbeXYOnly());
        assertEquals(1e-7,restored.getDiamondProbeCycleDeadband());assertTrue(restored.isDiamondProbeRecordTraces());
    }

    @Test void sphericalSettingsRoundTripAndRejectUndersampledSweeps() {
        var settings=new AppSettings();settings.setDiamondProbeSphericalSweep(true);
        settings.setDiamondProbeSweepConfiguration(512,8,3);
        var ini=new Wini();settings.saveToFile(ini,"",0);
        var restored=new AppSettings();restored.loadFromFile(ini,"",0);
        assertTrue(restored.isDiamondProbeSphericalSweep());assertEquals(512,restored.getDiamondProbeSweepPeriod());
        assertEquals(8,restored.getDiamondProbeSweepTurns());assertEquals(3,restored.getDiamondProbeMeasurementCycles());
        assertThrows(IllegalArgumentException.class,()->restored.setDiamondProbeSweepPeriod(32));
        ini.put("DiamondProbe","sweepPeriod",3);restored.loadFromFile(ini,"",0);
        assertEquals(128,restored.getDiamondProbeSweepPeriod());assertEquals(4,restored.getDiamondProbeSweepTurns());
    }

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
