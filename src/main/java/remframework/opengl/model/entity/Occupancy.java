/*
 * Copyright (C) 2023  Duy Quoc Nguyen <d.q.nguyen@smartblackbox.org> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 08/07/2026
 */
package remframework.opengl.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.ini4j.Wini;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import remframework.opengl.model.Lattice;
import remframework.opengl.helpers.Neighbor;
import remframework.helpers.AbstractSettings;
import remframework.helpers.ISettings;

/**
 * Represents the relationship between an Identity and a Node.
 * <br>
 * <br>
 * An Identity does not directly have a position in REM-FIELD.
 * Instead, it occupies a Node through an Occupancy relation.
 * <br>
 * <br>
 * The Occupancy object is also the location where transport state is stored.
 * This is important because pressure belongs to the relationship:
 * <br>
 *     Identity <-> Node
 * <br>
 * <br>
 * and not to the Identity itself or the Node itself.
 * <br>
 * <br>
 * Example:
 * <br>
 *     Identity A
 *          |
 *          |
 *      Occupancy
 *          |
 *          |
 *       Node(10,5,3)
 * <br>
 * <br>
 * The occupancy can later move to another Node while keeping its accumulated
 * transport state.
 */
@Getter
@Setter
public class Occupancy extends AbstractSettings implements ISettings {

    /** Identity currently occupying this node. */
    private Identity identity;

    /**
     * The lattice node currently occupied by this Identity.
     */
    private Node node;

    /** Graph radius of this single Identity's aggregate occupied region. */
    private int graphRadius;
    /** Symmetric body-diagonal indentation used by the shape-search experiment. */
    private double bodyDiagonalIndentation;
    private transient java.util.List<Node> occupiedNodesCache;
    private transient java.util.Set<Node> occupiedNodeSetCache;
    private transient OccupiedRegionDiagnostics occupiedRegionDiagnostics;

    /**
     * The node index lattice location.
     */
    private Vector3i nodeIndex = new Vector3i();

    /**
     * Accumulated transport pressure.
     * <br>
     * Pressure increases every update until one component exceeds the
     * transport threshold, causing a node transfer.
     */
    private final Vector3d pressure = new Vector3d();


    /**
     * Most recently measured lattice gradient imbalance.
     * <br>
     * This is recomputed every update and is not persistent.
     */
    private final Vector3d gradientImbalance = new Vector3d();

    private final Vector3d acceleration = new Vector3d();

    /**
     * Persistent transport momentum.
     * <br>
     * This stores the current motion state.
     * It is preserved across node transfers and is responsible for
     * momentum-like behavior.
     */
    private final Vector3d transportMomentum = new Vector3d();

    private final Vector3d initialTransportMomentum = new Vector3d();

    private static final int GRADIENT_IMBALANCE_HISTORY_LENGTH = 100;

    private final Vector3d[] gradientImbalanceHistory        = new Vector3d[GRADIENT_IMBALANCE_HISTORY_LENGTH];

    private final Vector3d maxPositiveGradient = new Vector3d();
    private final Vector3d maxNegativeGradient = new Vector3d();
    private final Vector3d lastMaxPositiveGradient = new Vector3d();
    private final Vector3d lastMaxNegativeGradient = new Vector3d();

    private final Vector3d gradientSum = new Vector3d();

    private int gradientHistoryIndex = 0;
    private int sampleCount          = 0;

    private boolean isPositiveX = true;
    private boolean isPositiveY = true;
    private boolean isPositiveZ = true;

    private boolean signIsPositiveZ = true;
    private boolean lastPositiveY   = true;
    private boolean lastPositiveZ   = true;
    private int     samplesPerCycle = GRADIENT_IMBALANCE_HISTORY_LENGTH;
    private int cycleSampleCount;

    private int transportCyclesX = 0;
    private int transportCyclesY = 0;
    private int transportCyclesZ = 0;

    private double addAccX = 0.0;
    private double addAccY = 0.0;
    private double addAccZ = 0.0;

    /**
     * Creates an empty occupancy.
     * <br><br>
     * The identity and node can be assigned later.
     */
    public Occupancy() {
        resetHistory();
    }

    /**
     * Creates an occupancy relation between an Identity and a Node.
     *
     * @param identity Identity being transported
     * @param node Initial node location
     */
    public Occupancy(Identity identity, Node node) {
        this.identity = identity;
        this.node = node;
        if (node != null) {
            nodeIndex.set(node.getIndex());
        }
        resetHistory();
    }

    public int getGraphRadius() { return graphRadius; }

    public void setGraphRadius(int graphRadius) {
        if (graphRadius < 0) {
            throw new IllegalArgumentException("graphRadius must be non-negative: " + graphRadius);
        }
        if (this.graphRadius != graphRadius) {
            this.graphRadius = graphRadius;
            invalidateOccupiedRegion();
        }
    }

    public double getBodyDiagonalIndentation() { return bodyDiagonalIndentation; }

    public void setBodyDiagonalIndentation(double indentation) {
        if (!Double.isFinite(indentation) || indentation < 0.0) {
            throw new IllegalArgumentException("bodyDiagonalIndentation must be finite and non-negative: " + indentation);
        }
        if (Double.compare(bodyDiagonalIndentation, indentation) != 0) {
            bodyDiagonalIndentation = indentation;
            invalidateOccupiedRegion();
        }
    }

    /** Returns the cached breadth-first region in deterministic graph order. */
    public java.util.List<Node> getOccupiedNodes() {
        if (occupiedNodesCache == null) rebuildOccupiedRegion();
        return occupiedNodesCache;
    }

    public OccupiedRegionDiagnostics getOccupiedRegionDiagnostics() {
        if (occupiedNodesCache == null) rebuildOccupiedRegion();
        return occupiedRegionDiagnostics;
    }

    public void invalidateOccupiedRegion() {
        occupiedNodesCache = null;
        occupiedNodeSetCache = null;
        occupiedRegionDiagnostics = null;
    }

    /** Rebuilds membership using only direct-neighbor graph traversal. */
    public void rebuildOccupiedRegion() {
        if (node == null) {
            occupiedNodesCache = java.util.List.of();
            occupiedNodeSetCache = java.util.Set.of();
            occupiedRegionDiagnostics = new OccupiedRegionDiagnostics(graphRadius, 0, 0, 0, null);
            return;
        }
        java.util.ArrayDeque<Node> queue = new java.util.ArrayDeque<>();
        java.util.Map<Node, Integer> distance = new java.util.LinkedHashMap<>();
        queue.add(node);
        distance.put(node, 0);
        while (!queue.isEmpty()) {
            Node current = queue.removeFirst();
            int currentDistance = distance.get(current);
            if (currentDistance >= graphRadius) continue;
            for (Neighbor direction : Neighbor.values()) {
                Node neighbor = current.getNeighbor(direction);
                if (neighbor != null && !distance.containsKey(neighbor)
                        && isInsideShape(neighbor.getIndex())) {
                    distance.put(neighbor, currentDistance + 1);
                    queue.addLast(neighbor);
                }
            }
        }
        occupiedNodesCache = java.util.List.copyOf(distance.keySet());
        occupiedNodeSetCache = java.util.Set.copyOf(distance.keySet());
        int boundaryNodes = 0;
        int crossingEdges = 0;
        for (Node occupied : occupiedNodesCache) {
            boolean boundary = false;
            for (Neighbor direction : Neighbor.values()) {
                Node neighbor = occupied.getNeighbor(direction);
                if (neighbor != null && !occupiedNodeSetCache.contains(neighbor)) {
                    boundary = true;
                    crossingEdges++;
                }
            }
            if (boundary) boundaryNodes++;
        }
        occupiedRegionDiagnostics = new OccupiedRegionDiagnostics(graphRadius,
                occupiedNodesCache.size(), boundaryNodes, crossingEdges,
                node.getIndex());
    }

    /**
     * Keeps the graph-radius region unchanged at indentation zero. Positive
     * indentation progressively removes nodes for which all three coordinate
     * offsets are non-zero, with the strongest effect toward the eight body
     * diagonals. Axis extent is therefore unchanged and the BFS remains the
     * only source of connectivity.
     */
    private boolean isInsideShape(Vector3i index) {
        int dx = Math.abs(index.x - nodeIndex.x);
        int dy = Math.abs(index.y - nodeIndex.y);
        int dz = Math.abs(index.z - nodeIndex.z);
        double score = (double) dx + dy + dz
                + bodyDiagonalIndentation * Math.min(dx, Math.min(dy, dz));
        return score < graphRadius;
    }

    @Override
    public AbstractSettings clone() {
        return null;
    }

    private void resetHistory() {
        for (int i = 0; i < GRADIENT_IMBALANCE_HISTORY_LENGTH; i++) {
            gradientImbalanceHistory[i]        = new Vector3d();
        }
    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        System.out.println("Occupancy.loadFromFile()");
        super.loadFromFile(ini, section, index);
        int x = getInt(section + index, "nodeIndex.x", 0);
        int y = getInt(section + index, "nodeIndex.y", 0);
        int z = getInt(section + index, "nodeIndex.z", 0);
        nodeIndex = new Vector3i(x, y, z);
        setGraphRadius(getInt(section + index, "graphRadius", 0));

        x = getInt(section + index, "initialTransportMomentum.x", 0);
        y = getInt(section + index, "initialTransportMomentum.y", 0);
        z = getInt(section + index, "initialTransportMomentum.z", 0);
        initialTransportMomentum.set(x, y, z);

        identity = new Identity("");
        identity.loadFromFile(ini, section, index);
    }

    @Override
    public void saveToFile(Wini ini, String section, int index) {
        super.saveToFile(ini, section, index);
        identity.saveToFile(ini, section, index);
        put(section + index, "nodeIndex.x", nodeIndex.x);
        put(section + index, "nodeIndex.y", nodeIndex.y);
        put(section + index, "nodeIndex.z", nodeIndex.z);
        put(section + index, "graphRadius", graphRadius);
        put(section + index, "initialTransportMomentum.x", initialTransportMomentum.x);
        put(section + index, "initialTransportMomentum.y", initialTransportMomentum.y);
        put(section + index, "initialTransportMomentum.z", initialTransportMomentum.z);
    }

    public boolean isOccupied() {
        return identity != null && nodeIndex != null;// && node != null;
    }

    public void setNode(Node node) {
        if (this.node != node) invalidateOccupiedRegion();
        this.node = node;
        nodeIndex.set(node.getIndex());
    }

    @Override
    public void reset() {
        System.out.println("Occupancy.reset()");
        identity.reset();
        pressure.zero();
        resetMaxGradient();
        resetHistory();
        Entity entity = Lattice.getInstance().createEntity(new Vector3d(), new Vector3d(), 4.0f);
        entity.setOverrideModelMaterial(true);
        entity.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        entity.getMaterial().setAmbientColor(new Vector4f(10.0f, 10.0f, 10.0f, 1.0f));
        entity.getMaterial().setShininess(0.0f);

        identity.setEntity(entity);
    }

    public void saveGradientToHistory(Vector3d gradient) {
        // Store the newest sample
        gradientImbalanceHistory[gradientHistoryIndex].set(gradient);

        // Advance circular buffer
        gradientHistoryIndex = (gradientHistoryIndex + 1) % GRADIENT_IMBALANCE_HISTORY_LENGTH;

        cycleSampleCount++;
    }

    public boolean isCycleCompleted() {
        if (signIsPositiveZ && node.getVelocity().z < 0) {
            // Set to negative
            signIsPositiveZ = false;
        }
        else if (!signIsPositiveZ && node.getVelocity().z >= 0) {
            updateMaxGradient(gradientImbalanceHistory);
            // Set to positive
            signIsPositiveZ      = true;
            samplesPerCycle      = cycleSampleCount;
            gradientHistoryIndex = 0;
            cycleSampleCount = 0;
            resetMaxGradient();
            resetHistory();
            return true;
        }
        return false;
    }

    public void resetMaxGradient() {
        maxPositiveGradient.zero();
        maxNegativeGradient.zero();
    }

    public void updateMaxGradient(Vector3d[] history) {
        maxPositiveGradient.zero();
        maxNegativeGradient.zero();

        for (Vector3d vector3d : history) {
            maxPositiveGradient.max(vector3d);
            maxNegativeGradient.min(vector3d);
        }
        gradientImbalance.set(maxPositiveGradient).add(maxNegativeGradient).mul(0.5);
        lastMaxPositiveGradient.set(maxPositiveGradient);
        lastMaxNegativeGradient.set(maxNegativeGradient);
    }

    public void updateVisualization() {
        Entity entity = identity.getEntity();
        if (entity != null) {
            entity.setEquilibriumPosition(node.getEquilibriumPosition());
            entity.setPosition(node.getEquilibriumPosition());
            entity.setScale(4.0);
            entity.setCustomColor(new Vector4f(1f, 1f, 0f, 1f));
        }
    }

    public void updateOscillation() {
        Vector3d o = identity.generateExcitation();
        if (o != null) {
            for (Node occupied : getOccupiedNodes()) occupied.addForce(o);
        }
    }

    public void update() {
        if (node != null) {
            node.setCustomColor(new Vector4f(0f, 1f, 0f, 1f));

            if (identity != null) {
                updateVisualization();
                updateOscillation();
            }
        }
    }

    public void incrementTransportCycleX() {
        transportCyclesX++;
    }

    public void incrementTransportCycleY() {
        transportCyclesY++;
    }

    public void incrementTransportCycleZ() {
        transportCyclesZ++;
    }
}
