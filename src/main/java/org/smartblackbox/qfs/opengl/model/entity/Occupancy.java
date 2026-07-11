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
import org.smartblackbox.qfs.settings.Project;
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
    private Vector3d pressure = new Vector3d();


    /**
     * Most recently measured lattice gradient.
     * <br>
     * This is recomputed every update and is not persistent.
     */
    private final Vector3d gradient = new Vector3d();


    /**
     * Persistent transport velocity.
     * <br>
     * This stores the current motion state.
     * It is preserved across node transfers and is responsible for
     * momentum-like behaviour.
     */
    private final Vector3d transportVelocity = new Vector3d();


    /**
     * Creates an empty occupancy.
     * <br><br>
     * The identity and node can be assigned later.
     */
    public Occupancy() {
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
    }

    @Override
    public AbstractSettings clone() {
        return null;
    }

    @Override
    public void loadFromFile(String filename) {
    }

    @Override
    public void saveToFile(String filename) {
    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        super.loadFromFile(ini, section, index);
        int x = getInt(section + index, "nodeIndex.x", 0);
        int y = getInt(section + index, "nodeIndex.y", 0);
        int z = getInt(section + index, "nodeIndex.z", 0);
        nodeIndex = new Vector3i(x, y, z);
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
    }

    public boolean isOccupied() {
        return identity != null && nodeIndex != null;// && node != null;
    }

    @Override
    public void reset() {
        identity.reset();
        pressure.zero();
        gradient.zero();
        transportVelocity.zero();
        node = Project.getInstance().scene.getLattice().getNodeByIndex(nodeIndex);

        Entity entity = Lattice.getInstance().createEntity(node.equilibriumPosition, new Vector3d(), 4.0f);
        entity.setOverrideModelMaterial(true);
        entity.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        entity.getMaterial().setAmbientColor(new Vector4f(10.0f, 10.0f, 10.0f, 1.0f));
        entity.getMaterial().setShininess(0.0f);

        identity.setEntity(entity);
    }

    public void update() {
        if (node != null) {
            node.setCustomColor(new Vector4f(0f, 1f, 0f, 1f));

            if (identity != null) {
                Entity entity = identity.getEntity();
                if (entity != null) {
                    entity.setEquilibriumPosition(node.getEquilibriumPosition());
                    entity.setPosition(node.getEquilibriumPosition());
                    entity.setScale(4.0);
                    entity.setCustomColor(new Vector4f(1f, 1f, 0f, 1f));
                }
                Vector3d o = identity.oscillate();
                if (o != null) {
                    node.getPosBuff().add(o);
                }
            }
        }
    }
}