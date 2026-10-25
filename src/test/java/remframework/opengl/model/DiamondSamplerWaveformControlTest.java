package remframework.opengl.model;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.research.diamond.DiamondReceiverSampling;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Known radial field isolates interpolation from time integration, mechanics and boundaries. */
class DiamondSamplerWaveformControlTest {
    @Test void productionSamplerTransferAtWavelength32IsSmallComparedWithObservedAmplitudeVariation() {
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var center=new Vector3d(399.5,399.5,11.5).div(Math.sqrt(3));
        var positions=new ArrayList<Vector3d>();var lookup=new HashMap<String,Integer>();
        var sampler=new DiamondReceiverSampling.Sampler(0,
            id->rotation.transform(new Vector3d(positions.get(id))),id->true,1,
            (x,y,z)->lookup.computeIfAbsent(x+","+y+","+z,key->{int id=positions.size();positions.add(new Vector3d(x,y,z).div(Math.sqrt(3)));return id;}));
        var output=new StringBuilder("radius,minimum_amplitude,maximum_amplitude,amplitude_ratio\n");
        for(double radius:new double[]{104,128,152,192,208}) {
            double minimum=Double.POSITIVE_INFINITY,maximum=0;
            for(var direction:DiamondProbePlacement.xyDirections(64)) {
                var target=new Vector3d(direction).mul(radius).add(center);
                var receiver=sampler.receiver(rotation.transform(new Vector3d(target)),"radial sinusoid control");
                double re=0,im=0,weight=0;
                for(int j=0;j<receiver.nodes().size();j++) {
                    var point=positions.get(receiver.nodes().get(j));
                    double phase=2*Math.PI*(Math.hypot(point.x-center.x,point.y-center.y)-radius)/32;
                    double w=receiver.weights().get(j);re+=w*Math.cos(phase);im+=w*Math.sin(phase);weight+=w;
                }
                assertEquals(1,weight,1e-12);
                double amplitude=Math.hypot(re,im);minimum=Math.min(minimum,amplitude);maximum=Math.max(maximum,amplitude);
            }
            output.append(String.format(Locale.ROOT,"%.0f,%.9g,%.9g,%.9g%n",radius,minimum,maximum,maximum/minimum));
            assertTrue(maximum/minimum<1.02,"Smooth wavelength-32 interpolation control must stay below 2% directional amplitude variation");
        }
        System.out.println(output);
    }
}
