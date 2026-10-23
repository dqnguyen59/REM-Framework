package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondThresholdSensitivityTest {
    @Test void identicalSignalsGiveZeroSpreadAtAllThresholds() {
        var analysis=new DiamondThresholdSensitivity(2,1,10);double[] values={.001,-.001};analysis.record(1,values);values[0]=100;
        analysis.record(2,new double[]{.01,-.01});
        for(var row:analysis.compare()){assertEquals(2,row.arrivals());assertEquals(0,row.speedSpreadPercent(),1e-12);}
    }
    @Test void incompleteThresholdsNeverReportPartialSpread() {
        var analysis=new DiamondThresholdSensitivity(2,1,10);analysis.record(1,new double[]{.001,.0001});
        var rows=analysis.compare();assertEquals(2,rows.get(1).arrivals());assertEquals(1,rows.get(2).arrivals());
        assertTrue(Double.isNaN(rows.get(2).speedSpreadPercent()));assertEquals(0,rows.get(3).arrivals());
        assertThrows(IllegalArgumentException.class,()->analysis.record(3,new double[]{1,1}));
        assertThrows(IllegalArgumentException.class,()->analysis.record(2,new double[]{Double.NaN,1}));
    }
}
