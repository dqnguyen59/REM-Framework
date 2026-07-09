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
 * File created on 01/01/2023
 */
package org.smartblackbox.qfs.opengl.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.ini4j.Wini;
import org.joml.Vector3d;
import org.smartblackbox.qfs.opengl.utils.OscillatorType;
import org.smartblackbox.qfs.opengl.utils.Vector3b;
import org.smartblackbox.qfs.opengl.utils.VectorOscillatorType;
import org.smartblackbox.utils.AbstractSettings;
import org.smartblackbox.utils.ISettings;

/**
 * REM-FIELD Identity.
 *
 * <p>
 * An Identity is the fundamental oscillator of REM-FIELD. It is not a
 * physical particle, but a computational entity that excites the recursive
 * lattice through oscillation.
 * </p>
 *
 * <p>
 * An Identity can occupy exactly one node at any moment through an
 * associated Occupancy.
 * </p>
 *
 * <p>
 * The Identity has no knowledge of:
 * <ul>
 *     <li>the recursive lattice,</li>
 *     <li>other identities,</li>
 *     <li>transport,</li>
 *     <li>distance,</li>
 *     <li>direction,</li>
 *     <li>forces,</li>
 *     <li>or physical motion.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Its only responsibility is to generate oscillatory excitation that is
 * applied to the currently occupied node.
 * </p>
 */
@Getter
@Setter
public class Identity extends AbstractSettings implements ISettings {

    /**
     * Human-readable identifier used for debugging,
     * visualization and configuration.
     */
    private String name = "";

    /**
     * Indicates which spatial axes are active.
     */
    private Vector3b active = new Vector3b();

    /**
     * Waveform used for each spatial axis.
     */
    private VectorOscillatorType oscillatorType = new VectorOscillatorType();

    /**
     * Initial phase offset of the oscillator.
     */
    private Vector3d phaseAngle = new Vector3d();

    /**
     * Current oscillator phase.
     */
    private Vector3d phase = new Vector3d();

    /**
     * Phase advance applied during every simulation update.
     * <p>
     * The oscillation frequency is derived from this value and the
     * simulation update rate.
     */
    private Vector3d phaseIncrement = new Vector3d();

    /**
     * Oscillation amplitude.
     */
    private Vector3d amplitude = new Vector3d();

    /**
     * Cached oscillation output for the current simulation update.
     */
    private Vector3d oscillation = new Vector3d();

    public Identity(String name) {
        this.name      = name;
    }

    public Identity clone() {
        Identity identity = new Identity(name);
        identity.active           = new Vector3b(active);
        identity.oscillatorType   = new VectorOscillatorType(oscillatorType);
        identity.phaseAngle       = new Vector3d(phaseAngle);
        identity.phase            = new Vector3d(phase);
        identity.phaseIncrement   = new Vector3d(phaseIncrement);
        identity.amplitude        = new Vector3d(amplitude);
        identity.oscillation      = new Vector3d(oscillation);
        return identity;
    }

    /**
     * Advances the oscillator by one simulation update and returns the
     * excitation that should be applied to the occupied node.
     *
     * <p>
     * The phase advances independently for each active axis.
     * </p>
     *
     * <p>
     * The normalized waveform is multiplied by the configured amplitude.
     * </p>
     *
     * <p>
     * This method is the only mechanism by which an Identity influences
     * the recursive lattice.
     * </p>
     *
     * @return Oscillation vector, or {@code null} if the identity is inactive.
     */
    public Vector3d oscillate() {
        oscillation.zero();

        if (active.x) {
            phase.x += phaseIncrement.x;
            if (phase.x >= 360) phase.x = 0;
            else if (phase.x <= -360) phase.x = 0;

            if (oscillatorType.x == OscillatorType.sin)
                oscillation.x = Math.sin(Math.toRadians(phaseAngle.x + phase.x));
            else if (oscillatorType.x == OscillatorType.cos)
                oscillation.x = Math.cos(Math.toRadians(phaseAngle.x + phase.x));
            else if (oscillatorType.x == OscillatorType.square) {
                if (phaseAngle.x + phase.x >= 0 && phaseAngle.x + phase.x < 180)
                    oscillation.x = 1;
                else
                    oscillation.x = -1;
            }
        }

        if (active.y) {
            phase.y += phaseIncrement.y;
            if (phase.y >= 360) phase.y = 0;
            else if (phase.y <= -360) phase.y = 0;

            if (oscillatorType.y == OscillatorType.sin)
                oscillation.y = Math.sin(Math.toRadians(phaseAngle.y + phase.y));
            else if (oscillatorType.y == OscillatorType.cos)
                oscillation.y = Math.cos(Math.toRadians(phaseAngle.y + phase.y));
            else if (oscillatorType.y == OscillatorType.square) {
                if (phaseAngle.y + phase.y >= 0 && phaseAngle.y + phase.y < 180)
                    oscillation.y = 1;
                else
                    oscillation.y = -1;
            }
        }

        if (active.z) {
            phase.z += phaseIncrement.z;
            if (phase.z >= 360) phase.z = 0;
            else if (phase.z <= -360) phase.z = 0;

            if (oscillatorType.z == OscillatorType.sin)
                oscillation.z = Math.sin(Math.toRadians(phaseAngle.z + phase.z));
            else if (oscillatorType.z == OscillatorType.cos)
                oscillation.z = Math.cos(Math.toRadians(phaseAngle.z + phase.z));
            else if (oscillatorType.z == OscillatorType.square) {
                if (phaseAngle.z + phase.z >= 0 && phaseAngle.z + phase.z < 180)
                    oscillation.z = 1;
                else
                    oscillation.z = -1;
            }
        }

        if (active.x || active.y || active.z) {
            return oscillation.mul(amplitude);
        } else return null;
    }

    /**
     * Resets the oscillator to its initial state.
     */
    public void reset() {
        oscillation.set(0);
        phase.set(0);
    }

    /**
     * Returns whether at least one oscillator axis is active.
     */
    public boolean isActive() {
        return active.x || active.y || active.z;
    }

    @Override
    public void loadFromFile(String filename) {
        // TODO Auto-generated method stub

    }

    @Override
    public void saveToFile(String filename) {
    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        super.loadFromFile(ini, section, index);
        String s;
        name               = ini.get(section + index, "name");
        active.x           = getBool(section + index, "active.x", false);
        active.y           = getBool(section + index, "active.y", false);
        active.z           = getBool(section + index, "active.z", false);
        oscillatorType.x   = (s = getString(section + index, "OscillatorType.x", "")).isEmpty()
                             ? OscillatorType.sin : OscillatorType.valueOf(s);
        oscillatorType.y   = (s = getString(section + index, "OscillatorType.y", "")).isEmpty()
                             ? OscillatorType.sin : OscillatorType.valueOf(s);
        oscillatorType.z   = (s = getString(section + index, "OscillatorType.z", "")).isEmpty()
                             ? OscillatorType.sin : OscillatorType.valueOf(s);
        phaseAngle.x       = getDouble(section + index, "startAngle.x", 0);
        phaseAngle.y       = getDouble(section + index, "startAngle.y", 0);
        phaseAngle.z       = getDouble(section + index, "startAngle.z", 0);
        phaseIncrement.x   = getDouble(section + index, "angleIncrement.x", 0);
        phaseIncrement.y   = getDouble(section + index, "angleIncrement.y", 0);
        phaseIncrement.z   = getDouble(section + index, "angleIncrement.z", 0);
        amplitude.x        = getDouble(section + index, "amplitude.x", 0);
        amplitude.y        = getDouble(section + index, "amplitude.y", 0);
        amplitude.z        = getDouble(section + index, "amplitude.z", 0);
    }

    @Override
    public void saveToFile(Wini ini, String section, int index) {
        super.saveToFile(ini, section, index);
        put(section + index, "name", name);
        put(section + index, "active.x", active.x);
        put(section + index, "active.y", active.y);
        put(section + index, "active.z", active.z);
        put(section + index, "OscillatorType.x", oscillatorType.x == null ? "" : oscillatorType.x.toString());
        put(section + index, "OscillatorType.y", oscillatorType.y == null ? "" : oscillatorType.y.toString());
        put(section + index, "OscillatorType.z", oscillatorType.z == null ? "" : oscillatorType.z.toString());
        put(section + index, "startAngle.x", phaseAngle.x);
        put(section + index, "startAngle.y", phaseAngle.y);
        put(section + index, "startAngle.z", phaseAngle.z);
        put(section + index, "angleIncrement.x", phaseIncrement.x);
        put(section + index, "angleIncrement.y", phaseIncrement.y);
        put(section + index, "angleIncrement.z", phaseIncrement.z);
        put(section + index, "amplitude.x", amplitude.x);
        put(section + index, "amplitude.y", amplitude.y);
        put(section + index, "amplitude.z", amplitude.z);
    }

}
