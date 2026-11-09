package remframework.geometry.discovery.research.diamond;

import org.joml.Vector3d;

/** Repeating nested circular rotations; finite coverage is not an isotropy claim. */
public record DiamondRotatingOrbit(int fastPeriod, int ratio) {
    public DiamondRotatingOrbit {
        if(fastPeriod < 32 || fastPeriod > 4096 || ratio < 2 || ratio > 16)
            throw new IllegalArgumentException("X period 32..4096 updates; Y/X and Z/Y period ratio 2..16");
    }
    public int period() { return fastPeriod * ratio * ratio; }
    /** u = Rz(c) Ry(b) Rx(a) (0,1,0), with periods P, P*r, P*r*r. */
    public Vector3d direction(long update) {
        double phase=Math.floorMod(update,(long)period())/(double)period();
        double a=2*Math.PI*phase*ratio*ratio,b=2*Math.PI*phase*ratio,c=2*Math.PI*phase;
        return new Vector3d(0,1,0).rotateX(a).rotateY(b).rotateZ(c);
    }
}
