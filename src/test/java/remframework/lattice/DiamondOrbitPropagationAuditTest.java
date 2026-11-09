package remframework.lattice;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import remframework.geometry.discovery.research.diamond.DiamondRotatingOrbit;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled finite-domain comparison, not a universal parameter optimum. */
class DiamondOrbitPropagationAuditTest {
    @Test void compareCouplingsWithIdenticalSourceAndObservationHorizon() throws Exception {
        var rows=new StringBuilder("k,gamma,q,radius,mean_peak_displacement,min_peak_displacement,max_peak_displacement,updates,source_amplitude,fast_period,ratio\n");
        double[][] configs={{.25,1},{.5,1},{.75,1},{.9,1},{Math.sqrt(.95),1},{.45,4}};
        var orbit=new DiamondRotatingOrbit(64,4);
        for(var cfg:configs) {
            var model=new CompactDiamondMechanics(64,64,64);
            var sources=new ArrayList<Integer>();var weights=new ArrayList<Double>();
            var receivers=new ArrayList<Integer>();var shell=new ArrayList<Integer>();
            var equilibrium=new HashMap<Integer,Vector3d>();var p=new Vector3d();
            double peak=0;
            for(int id=0;id<model.size();id++) {
                model.equilibrium(id,p);double r=p.length();
                if(r<6&&!model.isFixed(id)) {
                    double weight=Math.exp(-r*r/8)*Math.pow(1-r*r/36,2);
                    sources.add(id);weights.add(weight);equilibrium.put(id,new Vector3d(p));peak=Math.max(peak,weight);
                }
                for(int j=0;j<3;j++)if(Math.abs(r-(8+8*j))<.35&&!model.isFixed(id)) {
                    receivers.add(id);shell.add(j);equilibrium.put(id,new Vector3d(p));
                }
            }
            double[] maximum=new double[receivers.size()];
            for(int t=0;t<=96;t++) {
                var offset=orbit.direction(t).mul(.01);
                var velocity=new Vector3d(offset).sub(orbit.direction(t-1L).mul(.01));
                for(int j=0;j<sources.size();j++) {
                    int id=sources.get(j);double weight=weights.get(j)/peak;
                    model.setPosition(id,new Vector3d(equilibrium.get(id)).fma(weight,offset));
                    model.setVelocity(id,new Vector3d(velocity).mul(weight));
                }
                for(int j=0;j<receivers.size();j++) {
                    model.position(receivers.get(j),p);assertTrue(p.isFinite());
                    maximum[j]=Math.max(maximum[j],p.distance(equilibrium.get(receivers.get(j))));
                }
                if(t<96)model.step(cfg[0]*cfg[0]/4,cfg[1]);
            }
            for(int j=0;j<3;j++) {
                var stats=new DoubleSummaryStatistics();
                for(int i=0;i<maximum.length;i++)if(shell.get(i)==j)stats.accept(maximum[i]);
                rows.append(String.format(Locale.ROOT,"%.9g,%.9g,%.9g,%d,%.9g,%.9g,%.9g,96,.01,64,4%n",
                    cfg[0],cfg[1],cfg[0]*cfg[0]*cfg[1],8+j*8,stats.getAverage(),stats.getMin(),stats.getMax()));
            }
        }
        Files.createDirectories(Path.of("measurements","diamond-shells"));
        Files.writeString(Path.of("measurements","diamond-shells","orbit-coupling-comparison.csv"),rows);
    }
}
