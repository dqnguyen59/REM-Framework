package remframework.geometry.discovery.research.diamond;

import java.util.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;

/** Small-disturbance transmission baseline, not a propagation speed or isotropy measurement. */
public final class DiamondPropagationBaseline {
    private DiamondPropagationBaseline() { }
    public record Result(int nodes,int source,int updates,int respondingNodes,double maximumBoundaryDrift,
                         double maximumDisplacement,GeometryConfigurationStatus status) { }
    public static Result run(int updates,double amplitude) {
        if(updates<1 || updates>10000 || !Double.isFinite(amplitude) || amplitude<=0 || amplitude>.01)
            throw new IllegalArgumentException("Updates 1..10000 and displacement (0,.01] required");
        var model=DiamondReference.create(5,10);var mechanics=new DiamondMechanics(model,.5,.1);
        var center=new Vector3d();model.nodes().forEach(n->center.add(n.position()));center.div(model.nodes().size());
        int source=model.nodes().stream().filter(n->n.neighborIds().size()==4)
            .min(Comparator.comparingDouble(n->n.position().distanceSquared(center))).orElseThrow().id();
        mechanics.displace(source,new Vector3d(amplitude,0,0));
        double boundary=0,max=0;Set<Integer> responding=new HashSet<>();int completed=0;
        try {
            for(int t=0;t<updates;t++) {
                mechanics.step();completed++;
                for(int id=0;id<mechanics.nodeCount();id++) {
                    double displacement=mechanics.position(id).distance(model.nodes().get(id).position());max=Math.max(max,displacement);
                    if(mechanics.fixed(id)) boundary=Math.max(boundary,displacement);
                    else if(id!=source && displacement>amplitude*1e-6) responding.add(id);
                }
            }
        } catch(IllegalStateException failure) {
            return new Result(mechanics.nodeCount(),source,completed,responding.size(),boundary,max,mechanics.status());
        }
        boolean pass=!responding.isEmpty() && boundary==0 && Double.isFinite(max);
        var diagnostic=pass?GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.GENERATED,"Diamond propagation baseline","Disturbance transmitted; fixed boundary retained. Isotropy and canonical runtime parity untested.")
            :GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Diamond propagation baseline","Transmission not established or fixed boundary changed.");
        return new Result(mechanics.nodeCount(),source,completed,responding.size(),boundary,max,GeometryConfigurationStatus.of(diagnostic));
    }
    public static void main(String[] args) {
        var result=run(200,.001);
        System.out.printf(Locale.ROOT,"Nodes: %d; updates: %d%nResponding nodes beyond source: %d%nFixed-boundary drift: %.3g%nMaximum displacement: %.6g%n%s%nIsotropy: not tested%n",
            result.nodes(),result.updates(),result.respondingNodes(),result.maximumBoundaryDrift(),result.maximumDisplacement(),result.status().explanation());
    }
}
