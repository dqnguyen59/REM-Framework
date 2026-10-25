package remframework.geometry.discovery.research.diamond;

import org.joml.Vector3d;

/** Polarization of the initial displacement; the envelope still propagates radially. */
public enum DiamondExcitationAxis {
    AUTO, X, Y, Z;
    public DiamondExcitationAxis resolve(boolean xySlab) { return this == AUTO ? xySlab ? Z : X : this; }
    public Vector3d displaced(Vector3d equilibrium, double offset) {
        if(this==AUTO)throw new IllegalStateException("Resolve automatic polarization first");
        return new Vector3d(equilibrium).add(this==X?offset:0,this==Y?offset:0,this==Z?offset:0);
    }
    public double component(Vector3d vector) {
        return switch(this){case X->vector.x;case Y->vector.y;case Z->vector.z;case AUTO->throw new IllegalStateException("Resolve automatic polarization first");};
    }
}
