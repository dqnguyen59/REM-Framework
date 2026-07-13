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
package org.smartblackbox.qfs.opengl.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.ini4j.Wini;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.smartblackbox.qfs.opengl.model.Lattice;
import org.smartblackbox.utils.AbstractSettings;
import org.smartblackbox.utils.ISettings;

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
     * Most recently measured lattice gradient.
     * <br>
     * This is recomputed every update and is not persistent.
     */
    private final Vector3d gradient = new Vector3d();


    /**
     * Persistent transport momentum.
     * <br>
     * This stores the current motion state.
     * It is preserved across node transfers and is responsible for
     * momentum-like behavior.
     */
    private final Vector3d transportMomentum = new Vector3d();

    private final Vector3d initialTransportMomentum = new Vector3d();

    private static final int GRADIENT_HISTORY = 50;

    private final Vector3d[] gradientHistory = new Vector3d[GRADIENT_HISTORY];

    private final Vector3d gradientSum = new Vector3d();

    private final Vector3d averageGradient = new Vector3d();

    private int gradientIndex = 0;
    private int gradientCount = 0;

    /**
     * Creates an empty occupancy.
     * <br><br>
     * The identity and node can be assigned later.
     */
    public Occupancy() {
        init();
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
        init();
    }

    @Override
    public AbstractSettings clone() {
        return null;
    }

    private void init() {
        for (int i = 0; i < GRADIENT_HISTORY; i++) {
            gradientHistory[i] = new Vector3d();
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
        put(section + index, "initialTransportMomentum.x", initialTransportMomentum.x);
        put(section + index, "initialTransportMomentum.y", initialTransportMomentum.y);
        put(section + index, "initialTransportMomentum.z", initialTransportMomentum.z);
    }

    public boolean isOccupied() {
        return identity != null && nodeIndex != null;// && node != null;
    }

    public void setNode(Node node) {
        this.node = node;
        nodeIndex.set(node.getIndex());
    }

    @Override
    public void reset() {
        System.out.println("Occupancy.reset()");
        identity.reset();
        pressure.zero();
        gradient.zero();
        transportMomentum.zero();
        Entity entity = Lattice.getInstance().createEntity(new Vector3d(), new Vector3d(), 4.0f);
        entity.setOverrideModelMaterial(true);
        entity.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        entity.getMaterial().setAmbientColor(new Vector4f(10.0f, 10.0f, 10.0f, 1.0f));
        entity.getMaterial().setShininess(0.0f);

        identity.setEntity(entity);
    }

    public void updateAverageGradient() {

        // Remove the oldest sample
        gradientSum.sub(gradientHistory[gradientIndex]);

        // Store the newest sample
        gradientHistory[gradientIndex].set(gradient);

        // Add the new sample
        gradientSum.add(gradient);

        // Advance circular buffer
        gradientIndex = (gradientIndex + 1) % GRADIENT_HISTORY;

        if (gradientCount < GRADIENT_HISTORY)
            gradientCount++;

        // Compute average
        averageGradient.set(gradientSum).div(gradientCount);
    }

    public void updateAverageGradient_v1_0(Vector3d gradient) {

        gradientHistory[gradientIndex].set(gradient);

        gradientIndex = (gradientIndex + 1) % GRADIENT_HISTORY;

        if (gradientCount < GRADIENT_HISTORY)
            gradientCount++;

        averageGradient.zero();

        for (int i = 0; i < gradientCount; i++) {
            averageGradient.add(gradientHistory[i]);
        }

        averageGradient.div(gradientCount);
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
        Vector3d o = identity.oscillate();
        if (o != null) {
            node.getPosBuff().add(o);
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
}