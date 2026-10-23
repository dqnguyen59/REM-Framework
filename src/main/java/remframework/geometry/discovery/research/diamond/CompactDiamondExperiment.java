package remframework.geometry.discovery.research.diamond;

import remframework.lattice.CompactDiamondMechanics;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Bounded-memory, streamed mechanical probe experiment; no GUI or Identity state. */
public final class CompactDiamondExperiment {
    private CompactDiamondExperiment() { }
    public static void main(String[] args) throws Exception {
        if(args.length<9)throw new IllegalArgumentException("Expected x,y,z,steps,wavelength,firstRadius,spacing,xy|xyz,output.csv");
        int sx=Integer.parseInt(args[0]),sy=Integer.parseInt(args[1]),sz=Integer.parseInt(args[2]),steps=Integer.parseInt(args[3]);
        double wavelength=Double.parseDouble(args[4]),radius=Double.parseDouble(args[5]),spacing=Double.parseDouble(args[6]);
        boolean xy=args[7].equals("xy");
        if(!xy&&!args[7].equals("xyz"))throw new IllegalArgumentException("Plane must be xy or xyz");
        if(steps<1||!Double.isFinite(wavelength)||wavelength<2||!Double.isFinite(radius)||radius<=1.5*wavelength+1||!Double.isFinite(spacing)||spacing<=0)
            throw new IllegalArgumentException("Positive steps/spacing, wavelength >=2, receivers outside source support required");
        System.gc();long before=heap(),start=System.nanoTime();var mechanics=new CompactDiamondMechanics(sx,sy,sz);
        long buildNanos=System.nanoTime()-start;var p=new Vector3d();var minimum=mechanics.equilibrium(0,new Vector3d());
        if(Math.min(-minimum.x,-minimum.y)<=1.5*wavelength+1||(!xy&&-minimum.z<=1.5*wavelength+1))throw new IllegalArgumentException("Domain too small for source");
        var request=new DiamondRuntimeDisturbance.Request(wavelength,.001);
        for(int id=0;id<mechanics.size();id++)if(!mechanics.isFixed(id)) {
            mechanics.equilibrium(id,p);double r=xy?Math.hypot(p.x,p.y):p.length();p.x+=DiamondRuntimeDisturbance.displacement(r,request);mechanics.setPosition(id,p);
        }
        var sampler=mechanics.sampler(xy);var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        record Receiver(double radius,Vector3d target,DiamondDirectionalComparison.Receiver stencil) { }
        var receivers=new ArrayList<Receiver>();
        for(int shell=0;shell<2;shell++)for(var direction:DiamondDirectionalComparison.directions())if(!xy||direction.z==0) {
            var target=new Vector3d(direction).mul(radius+shell*spacing);
            receivers.add(new Receiver(radius+shell*spacing,target,sampler.receiver(rotation.transform(new Vector3d(target).sub(minimum)),"shell"+shell)));
        }
        System.gc();long live=heap()-before;start=System.nanoTime();
        try(var writer=Files.newBufferedWriter(Path.of(args[8]))) {
            writer.write("step,probe,radius,dx,dy,dz,gx,gy,gz\n");
            for(int step=0;step<=steps;step++) {
                for(int r=0;r<receivers.size();r++) {
                    var receiver=receivers.get(r);var observed=new Vector3d();var gradient=new Vector3d();var sample=new Vector3d();
                    for(int j=0;j<receiver.stencil.nodes().size();j++) {
                        int id=receiver.stencil.nodes().get(j);double weight=receiver.stencil.weights().get(j);
                        observed.fma(weight,mechanics.position(id,sample));gradient.fma(weight,mechanics.imbalance(id,sample));
                    }
                    observed.sub(receiver.target);if(!observed.isFinite()||!gradient.isFinite())throw new IllegalStateException("Nonfinite mechanical result");
                    writer.write(step+","+r+","+receiver.radius+","+observed.x+","+observed.y+","+observed.z+","+gradient.x+","+gradient.y+","+gradient.z+"\n");
                }
                if(step<steps)mechanics.step(.5*.5/4,1);
            }
        }
        long evolutionNanos=System.nanoTime()-start;
        String metrics="x,y,z,nodes,primitive_payload_bytes,live_heap_delta_bytes,build_ms,steps,evolution_ms\n"+sx+","+sy+","+sz+","+mechanics.size()+","+mechanics.stateBytes()+","+live+","+buildNanos/1e6+","+steps+","+evolutionNanos/1e6+"\n";
        Files.writeString(Path.of(args[8]+".metrics.csv"),metrics);System.out.println(metrics);
        System.out.println("Mechanical-only experiment. Fixed surfaces are included; no isotropy verdict or reflection-free claim.");
    }
    private static long heap(){return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
}
