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
package remframework.opengl.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.ini4j.Wini;
import org.joml.Vector3d;
import remframework.opengl.helpers.OscillatorType;
import remframework.opengl.helpers.Vector3b;
import remframework.opengl.helpers.VectorOscillatorType;
import remframework.helpers.AbstractSettings;
import remframework.helpers.ISettings;

/**
 * REM Framework Identity.
 *
 * <p>
 * An Identity is the fundamental oscillator of the REM Framework. It is not a
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
public class Identity extends AbstractSettings implements ISettings, Occupant {

    /** Runtime back-reference to this Identity's single normal Occupancy. */
    private transient Occupancy occupancy;

    /** Identity Occupancies retain both excitation and transport capabilities. */
    @Override
    public boolean canExcite() { return true; }

    /** Identity Occupancies retain both excitation and transport capabilities. */
    @Override
    public boolean canTransfer() { return true; }

    /**
     * Human-readable identifier used for debugging,
     * visualization and configuration.
     */
    private String name;

    private Entity entity;

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
    private Vector3d excitation = new Vector3d();

    /** Coupled XYZ rotation instead of three independent axis waveforms. */
    private boolean xyzOrbit;
    private boolean equalAreaOrbit;
    private transient long orbitUpdates;

    public Identity(String name) {
        this.name = name;
    }

    public Identity clone() {
        Identity identity = new Identity(name);
        identity.active         = new Vector3b(active);
        identity.oscillatorType = new VectorOscillatorType(oscillatorType);
        identity.phaseAngle     = new Vector3d(phaseAngle);
        identity.phase          = new Vector3d(phase);
        identity.phaseIncrement = new Vector3d(phaseIncrement);
        identity.amplitude      = new Vector3d(amplitude);
        identity.excitation     = new Vector3d(excitation);
        identity.xyzOrbit       = xyzOrbit;
        identity.equalAreaOrbit = equalAreaOrbit;
        identity.orbitUpdates   = orbitUpdates;
        return identity;
    }

    private double exciteAxis(
            boolean active,
            OscillatorType oscillatorType,
            Vector3d phase,
            Vector3d phaseAngle,
            Vector3d phaseIncrement,
            int axis) {

        if (!active) {
            return 0.0;
        }

        // Advance phase
        switch (axis) {
            case 0 -> phase.x += phaseIncrement.x;
            case 1 -> phase.y += phaseIncrement.y;
            case 2 -> phase.z += phaseIncrement.z;
        }

        // Wrap phase
        double p;
        switch (axis) {
            case 0 -> {
                if (phase.x >= 360) phase.x -= 360;
                else if (phase.x <= -360) phase.x += 360;
                p = phaseAngle.x + phase.x;
            }
            case 1 -> {
                if (phase.y >= 360) phase.y -= 360;
                else if (phase.y <= -360) phase.y += 360;
                p = phaseAngle.y + phase.y;
            }
            case 2 -> {
                if (phase.z >= 360) phase.z -= 360;
                else if (phase.z <= -360) phase.z += 360;
                p = phaseAngle.z + phase.z;
            }
            default -> throw new IllegalArgumentException("Invalid axis");
        }

        return switch (oscillatorType) {
            case sin -> Math.sin(Math.toRadians(p));
            case cos -> Math.cos(Math.toRadians(p));
            case square -> (p >= 0 && p < 180) ? 1.0 : -1.0;
        };
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
    public Vector3d generateExcitation() {
        excitation.zero();
        if (diagnosticPulseDirection != null) {
            double t=++diagnosticPulseUpdate;
            double duration=diagnosticPulsePeriod*diagnosticPulseCycles;
            double envelope=t<duration?Math.pow(Math.sin(Math.PI*t/duration),2):0;
            return excitation.set(diagnosticPulseDirection).mul(diagnosticPulseAmplitude*envelope
                    *Math.sin(2*Math.PI*t/diagnosticPulsePeriod));
        }

        if (xyzOrbit) {
            // The existing increments and offsets are degrees per update and degrees.
            phase.add(phaseIncrement);
            phase.set(wrapDegrees(phase.x), wrapDegrees(phase.y), wrapDegrees(phase.z));
            orbitUpdates++;
            excitation.set(sampleOrbitExcitation(0));
            return excitation;
        }

        excitation.x = exciteAxis(active.x, oscillatorType.x, phase, phaseAngle, phaseIncrement, 0);
        excitation.y = exciteAxis(active.y, oscillatorType.y, phase, phaseAngle, phaseIncrement, 1);
        excitation.z = exciteAxis(active.z, oscillatorType.z, phase, phaseAngle, phaseIncrement, 2);

        if (!isActive())
            return null;

        excitation.mul(amplitude);

        return excitation;
    }

    private static double wrapDegrees(double value) {
        return value % 360.0;
    }

    /** Samples the orbit relative to the current phase without advancing the Identity. */
    public Vector3d sampleOrbitExcitation(double updatesFromCurrent) {
        if (equalAreaOrbit) {
            double longitude = Math.toRadians(phase.x + phaseAngle.x + phase.y + phaseAngle.y
                    + updatesFromCurrent * (phaseIncrement.x + phaseIncrement.y));
            double turn = (phase.z + phaseAngle.z + updatesFromCurrent * phaseIncrement.z) / 360.0;
            turn -= Math.floor(turn);
            // Uniform height and longitude sample equal spherical areas: dA = d(longitude) dz.
            double z = 1 - 4 * Math.abs(turn - .5);
            double radius = Math.sqrt(Math.max(0, 1 - z * z));
            return new Vector3d(radius * Math.cos(longitude), radius * Math.sin(longitude), z).mul(amplitude);
        }
        return new Vector3d(0, 1, 0)
                .rotateX(Math.toRadians(phase.x + phaseAngle.x + updatesFromCurrent * phaseIncrement.x))
                .rotateY(Math.toRadians(phase.y + phaseAngle.y + updatesFromCurrent * phaseIncrement.y))
                .rotateZ(Math.toRadians(phase.z + phaseAngle.z + updatesFromCurrent * phaseIncrement.z))
                .mul(amplitude);
    }

    /**
     * Resets the oscillator to its initial state.
     */
    @Override
    public void reset() {
        excitation.set(0);
        phase.set(0);
        orbitUpdates = 0;
    }

    /**
     * Returns whether at least one oscillator axis is active.
     */
    public boolean isActive() {
        return diagnosticPulseDirection != null || xyzOrbit || active.x || active.y || active.z;
    }

    private Vector3d diagnosticPulseDirection;
    private double diagnosticPulseAmplitude;
    private int diagnosticPulsePeriod,diagnosticPulseCycles,diagnosticPulseUpdate;
    /** Temporary finite pulse through the normal Identity force path; saved oscillator settings are unchanged. */
    public void startDiagnosticPulse(Vector3d direction,double magnitude,int period,int cycles) {
        if(!direction.isFinite()||Math.abs(direction.length()-1)>1e-9||!Double.isFinite(magnitude)||magnitude<=0||period<4||cycles<1)
            throw new IllegalArgumentException("Invalid diagnostic pulse");
        diagnosticPulseDirection=new Vector3d(direction);diagnosticPulseAmplitude=magnitude;
        diagnosticPulsePeriod=period;diagnosticPulseCycles=cycles;diagnosticPulseUpdate=0;
    }
    public void endDiagnosticPulse(){diagnosticPulseDirection=null;}

    @Override
    public void loadFromFile(String filename) {
    }

    @Override
    public void saveToFile(String filename) {
    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        System.out.println("Identity.loadFromFile()");
        super.loadFromFile(ini, section, index);
        String s;
        name             = ini.get(section + index, "name");
        xyzOrbit         = getBool(section + index, "xyzOrbit", false);
        equalAreaOrbit   = getBool(section + index, "equalAreaOrbit", false);
        active.x         = getBool(section + index, "active.x", false);
        active.y         = getBool(section + index, "active.y", false);
        active.z         = getBool(section + index, "active.z", false);
        oscillatorType.x = (s = getString(section + index, "OscillatorType.x", "")).isEmpty()
                           ? OscillatorType.sin : OscillatorType.valueOf(s);
        oscillatorType.y = (s = getString(section + index, "OscillatorType.y", "")).isEmpty()
                           ? OscillatorType.sin : OscillatorType.valueOf(s);
        oscillatorType.z = (s = getString(section + index, "OscillatorType.z", "")).isEmpty()
                           ? OscillatorType.sin : OscillatorType.valueOf(s);
        phaseAngle.x     = getDouble(section + index, "startAngle.x", 0);
        phaseAngle.y     = getDouble(section + index, "startAngle.y", 0);
        phaseAngle.z     = getDouble(section + index, "startAngle.z", 0);
        phaseIncrement.x = getDouble(section + index, "angleIncrement.x", 0);
        phaseIncrement.y = getDouble(section + index, "angleIncrement.y", 0);
        phaseIncrement.z = getDouble(section + index, "angleIncrement.z", 0);
        amplitude.x      = getDouble(section + index, "amplitude.x", 0);
        amplitude.y      = getDouble(section + index, "amplitude.y", 0);
        amplitude.z      = getDouble(section + index, "amplitude.z", 0);
    }

    @Override
    public void saveToFile(Wini ini, String section, int index) {
        super.saveToFile(ini, section, index);
        put(section + index, "name", name);
        put(section + index, "xyzOrbit", xyzOrbit);
        put(section + index, "equalAreaOrbit", equalAreaOrbit);
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
