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
import remframework.measurement.detector.ExtendedIdentityShape;
import org.joml.Vector4f;
import remframework.opengl.model.Lattice;
import remframework.opengl.helpers.Neighbor;
import remframework.helpers.AbstractSettings;
import remframework.helpers.ISettings;

/**
 * Represents the relationship between an Occupant and a Node.
 * <br>
 * <br>
 * An Identity does not directly have a position in REM-FIELD.
 * Instead, it occupies a Node through an Occupancy relation.
 * <br>
 * <br>
 * The Occupancy object is also the location where transport state is stored.
 * This is important because pressure belongs to the relationship:
 * <br>
 *     Occupant <-> Node
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

    /** Runtime occupant; Identity remains separately persisted for .qfs compatibility. */
    private transient Occupant occupant;

    /**
     * The lattice node currently occupied by this Identity.
     */
    private Node node;

    /** Graph radius of this single Identity's aggregate occupied region. */
    private int graphRadius;
    /** Symmetric body-diagonal indentation used by the shape-search experiment. */
    private double bodyDiagonalIndentation;
    private double twoAxisIndentation;
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
    /** Last scaled gradient sample retained for measurement-path auditing. */
    private final Vector3d lastMeasuredGradient = new Vector3d();

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

    private static final int GRADIENT_IMBALANCE_HISTORY_LENGTH = 360;

    private final Vector3d[] gradientImbalanceHistory        = new Vector3d[GRADIENT_IMBALANCE_HISTORY_LENGTH];

    private final Vector3d maxPositiveGradient = new Vector3d();
    private final Vector3d maxNegativeGradient = new Vector3d();
    private final Vector3d lastMaxPositiveGradient = new Vector3d();
    private final Vector3d lastMaxNegativeGradient = new Vector3d();

    private final Vector3d gradientSum = new Vector3d();

    /** Next fixed phase slot to receive a sample. */
    private int gradientHistoryIndex = 0;
    /** Cumulative number of samples written over the lifetime of this relation. */
    private long sampleCount          = 0;
    private int populatedHistorySlots = 0;
    private long completedCycleCount = 0;

    private boolean isPositiveX = true;
    private boolean isPositiveY = true;
    private boolean isPositiveZ = true;

    private boolean signIsPositiveZ = true;
    /** Read-only instrumentation flag for the most recent cycle check. */
    private boolean cycleCompletedThisUpdate;
    private boolean lastPositiveY   = true;
    private boolean lastPositiveZ   = true;
    /** Configured cycle length; it is intentionally not inferred from observed frame counts. */
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
        this.occupant = identity;
        if (identity != null) identity.setOccupancy(this);
        this.node = node;
        if (node != null) {
            nodeIndex.set(node.getIndex());
        }
        resetHistory();
    }

    /** Creates a general Occupant–Node relation without changing Identity serialization. */
    public Occupancy(Occupant occupant, Node node) {
        this.occupant = occupant;
        this.identity = occupant instanceof Identity identityValue ? identityValue : null;
        if (identity != null) identity.setOccupancy(this);
        this.node = node;
        if (node != null) nodeIndex.set(node.getIndex());
        resetHistory();
    }

    /** Returns the runtime occupant, falling back to the persisted Identity after loading. */
    public Occupant getOccupant() {
        return occupant != null ? occupant : identity;
    }

    /** Assigns a runtime occupant without altering the legacy Identity field. */
    public void setOccupant(Occupant occupant) {
        this.occupant = occupant;
        if (occupant instanceof Identity identityValue) this.identity = identityValue;
    }

    /** Compatibility accessor retained for existing Identity callers and project files. */
    public Identity getIdentity() {
        return identity;
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

    public double getTwoAxisIndentation() { return twoAxisIndentation; }

    public void setTwoAxisIndentation(double indentation) {
        if (!Double.isFinite(indentation) || indentation < 0.0) {
            throw new IllegalArgumentException("twoAxisIndentation must be finite and non-negative: " + indentation);
        }
        if (Double.compare(twoAxisIndentation, indentation) != 0) {
            twoAxisIndentation = indentation;
            invalidateOccupiedRegion();
        }
    }

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
        return ExtendedIdentityShape.score(dx, dy, dz, twoAxisIndentation,
                bodyDiagonalIndentation) < graphRadius;
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

    private void resetHistoryState() {
        resetHistory();
        gradientHistoryIndex = 0;
        sampleCount = 0;
        populatedHistorySlots = 0;
        completedCycleCount = 0;
        cycleSampleCount = 0;
        samplesPerCycle = GRADIENT_IMBALANCE_HISTORY_LENGTH;
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
        identity.setOccupancy(this);
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
        return getOccupant() != null && nodeIndex != null;// && node != null;
    }

    /** Detaches the old graph, preserving the index, pressure and momentum. */
    public void detachLattice() {
        node = null;
        invalidateOccupiedRegion();
        if (identity != null) identity.setEntity(null);
    }

    public void setNode(Node node) {
        if (this.node != node) invalidateOccupiedRegion();
        this.node = node;
        nodeIndex.set(node.getIndex());
    }

    @Override
    public synchronized void reset() {
        System.out.println("Occupancy.reset()");
        if (identity == null) {
            resetMeasurementState();
            return;
        }
        identity.reset();
        pressure.zero();
        resetMaxGradient();
        resetHistoryState();
        Entity entity = Lattice.getInstance().createEntity(new Vector3d(), new Vector3d(), 4.0f);
        entity.setOverrideModelMaterial(true);
        entity.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        entity.getMaterial().setAmbientColor(new Vector4f(10.0f, 10.0f, 10.0f, 1.0f));
        entity.getMaterial().setShininess(0.0f);

        identity.setEntity(entity);
    }

    /** Clears measurement-only state for a Probe Occupancy without creating an Identity entity. */
    public synchronized void resetMeasurementState() {
        gradientImbalance.zero();
        lastMeasuredGradient.zero();
        acceleration.zero();
        pressure.zero();
        transportMomentum.zero();
        resetMaxGradient();
        resetHistoryState();
        signIsPositiveZ = true;
        cycleCompletedThisUpdate = false;
        transportCyclesX = 0;
        transportCyclesY = 0;
        transportCyclesZ = 0;
        addAccX = 0.0;
        addAccY = 0.0;
        addAccZ = 0.0;
    }

    public synchronized void saveGradientToHistory(Vector3d gradient) {
        lastMeasuredGradient.set(gradient);
        // Slot i is always phase/sample i. The next cycle overwrites the same slots.
        gradientImbalanceHistory[gradientHistoryIndex].set(gradient);
        gradientHistoryIndex = (gradientHistoryIndex + 1) % GRADIENT_IMBALANCE_HISTORY_LENGTH;
        populatedHistorySlots = Math.min(GRADIENT_IMBALANCE_HISTORY_LENGTH, populatedHistorySlots + 1);
        sampleCount++;
        cycleSampleCount++;
    }

    public synchronized boolean isCycleCompleted() {
        cycleCompletedThisUpdate = false;
        if (signIsPositiveZ && node.getVelocity().z < 0) {
            // Set to negative
            signIsPositiveZ = false;
        }
        else if (!signIsPositiveZ && node.getVelocity().z >= 0) {
            updateMaxGradient(gradientImbalanceHistory);
            // Set to positive
            signIsPositiveZ      = true;
            // Preserve the fixed phase buffer. A cycle transition only starts
            // writing at phase zero; it does not clear or resize the history.
            gradientHistoryIndex = 0;
            cycleSampleCount = 0;
            completedCycleCount++;
            resetMaxGradient();
            cycleCompletedThisUpdate = true;
            return true;
        }
        return false;
    }

    /** Returns whether the most recent cycle check completed a receiver cycle. */
    public boolean wasCycleCompletedThisUpdate() {
        return cycleCompletedThisUpdate;
    }

    /** Read-only cycle-state access for compact-path equivalence auditing. */
    public boolean isSignIsPositiveZ() { return signIsPositiveZ; }

    /** Read-only history-counter access for compact-path equivalence auditing. */
    public int getCycleSampleCountForAudit() { return cycleSampleCount; }

    /** Read-only completed-cycle sample-size access for compact-path equivalence auditing. */
    public int getSamplesPerCycleForAudit() { return samplesPerCycle; }

    /** Returns the current phase slot that will receive the next sample. */
    public synchronized int getHistoryPhaseIndexForAudit() { return gradientHistoryIndex; }

    /** Returns the number of fixed phase slots populated at least once. */
    public synchronized int getPopulatedHistorySlotsForAudit() { return populatedHistorySlots; }

    public synchronized long getCompletedCycleCountForAudit() { return completedCycleCount; }

    public synchronized long getCumulativeSampleCountForAudit() { return sampleCount; }

    /** Copies the latest per-update scaled gradient into a caller-owned vector. */
    public synchronized Vector3d copyLastMeasuredGradientForAudit(Vector3d destination) {
        return destination.set(lastMeasuredGradient);
    }

    /** Adds a weighted latest sample to a caller-owned diagnostic accumulator. */
    public synchronized void accumulateLastMeasuredGradientForAudit(
            Vector3d accumulator, double weight) {
        accumulator.fma(weight, lastMeasuredGradient);
    }

    /** Captures a stable, immutable copy for diagnostic rendering. */
    public synchronized OccupancyHistorySnapshot getHistorySnapshotForAudit() {
        return new OccupancyHistorySnapshot(GRADIENT_IMBALANCE_HISTORY_LENGTH,
                java.util.Arrays.asList(gradientImbalanceHistory), populatedHistorySlots,
                gradientHistoryIndex, completedCycleCount, sampleCount, samplesPerCycle);
    }

    /** Returns defensive copies of the currently retained gradient samples. */
    public java.util.List<Vector3d> getGradientImbalanceHistoryForAudit() {
        return getHistorySnapshotForAudit().samples();
    }

    public void resetMaxGradient() {
        maxPositiveGradient.zero();
        maxNegativeGradient.zero();
    }

    public void updateMaxGradient(Vector3d[] history) {
        maxPositiveGradient.zero();
        maxNegativeGradient.zero();

        for(int i = 0; i < 360; i++) {
                maxPositiveGradient.max(history[i]);
                maxNegativeGradient.min(history[i]);
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
        if(!remframework.opengl.model.Lattice.getInstance().allowsIdentityExcitation()) return;
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
