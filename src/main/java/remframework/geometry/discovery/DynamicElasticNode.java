package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import org.joml.Vector3d;

public final class DynamicElasticNode {
    private final int id;
    private final Vector3d position;
    private final Vector3d velocity;
    private final boolean fixed;

    public DynamicElasticNode(int id, Vector3d position, Vector3d velocity, boolean fixed) {
        this.id = id;
        this.position = new Vector3d(position);
        this.velocity = new Vector3d(velocity);
        this.fixed = fixed;
    }
    public DynamicElasticNode copy() {
        return new DynamicElasticNode(id, position, velocity, fixed);
    }
    public int id() { return id; }
    public Vector3d position() { return position; }
    public Vector3d velocity() { return velocity; }
    public boolean fixed() { return fixed; }
}
