package remframework.opengl.controller;

import org.joml.Vector3d;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;

/** Experimental one-edge-per-update local transport with persistent residual pressure. */
public final class DiamondIdentityTransport {
    private DiamondIdentityTransport() { }

    /**
     * Select the unique eligible direct neighbor maximizing P dot unit(current bond).
     * A positive projection must reach threshold T. Transfer consumes T times that
     * unit vector: P(next)=P-T*n; momentum is unchanged.
     * <br><br>
     * Exact/numerical ties wait, avoiding slot-order bias. Fixed/wall targets are
     * excluded; no global target, opposite-slot fallback or multihop search is used.
     */
    public static void update(Occupancy occupancy, double threshold) {
        synchronized (occupancy) {
            if (!(threshold > 0) || !Double.isFinite(threshold))
                throw new IllegalArgumentException("Diamond transfer threshold must be finite and positive");
            Node current=occupancy.getNode();
            if (current==null || occupancy.getIdentity()==null || !occupancy.getOccupant().canTransfer()) return;
            if (!current.isGeometricDistanceImbalance())
                throw new IllegalArgumentException("Diamond transport requires geometric bond imbalance");
            Vector3d pressure=occupancy.getPressure();
            if (!pressure.isFinite()) throw new IllegalStateException("Nonfinite diamond transport pressure");
            if (current.isFixed() || current.isWall()) {
                occupancy.recordDiamondTransport(null,null,"Current node is fixed or a wall");return;
            }
            Node best=null;
            Vector3d bestBond=null;
            double bestScore=Double.NEGATIVE_INFINITY;
            boolean tied=false;
            for(Node candidate:current.getNeighbors()) {
                if(candidate==null || candidate==current || candidate.isFixed() || candidate.isWall()) continue;
                Vector3d bond=new Vector3d(candidate.getPosition()).sub(current.getPosition());
                double length=bond.length();
                if (!(length>0) || !Double.isFinite(length))
                    throw new IllegalStateException("Diamond transport requires finite nonzero bonds");
                double score=pressure.dot(new Vector3d(bond).div(length));
                if (!Double.isFinite(score)) throw new IllegalStateException("Nonfinite diamond transport projection");
                double tolerance=best==null?0:32*Math.ulp(Math.max(Math.abs(score),Math.abs(bestScore)));
                if(best==null || score>bestScore+tolerance) {
                    best=candidate;bestBond=bond;bestScore=score;tied=false;
                } else if(candidate!=best && Math.abs(score-bestScore)<=tolerance) tied=true;
            }
            if(best==null) {occupancy.recordDiamondTransport(null,null,"No eligible direct neighbor");return;}
            if(bestScore<threshold) {occupancy.recordDiamondTransport(null,null,"Pressure below transfer threshold");return;}
            if(tied) {occupancy.recordDiamondTransport(null,null,"Equal best directions; pressure retained");return;}
            Vector3d consumed=new Vector3d(bestBond).normalize().mul(threshold);
            Vector3d residual=new Vector3d(pressure).sub(consumed);
            if(!residual.isFinite()) throw new IllegalStateException("Nonfinite diamond residual pressure");
            occupancy.setNode(best);
            pressure.set(residual);
            occupancy.recordDiamondTransport(bestBond,consumed,"Transferred to a direct neighbor");
        }
    }
}
