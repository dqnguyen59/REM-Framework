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
import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.opengl.model.ObjFileModel;
import remframework.opengl.model.RemModel;
import remframework.opengl.model.Scene;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.AppSettings;
import remframework.settings.Settings.NodeFormulaVersion;

@Getter
@Setter
public class Node extends Entity {

    private final AppSettings appSettings = AppSettings.getInstance();
    private final RemModel    remModel    = project.getRemModel();
    private final Scene       scene       = project.scene;

    private static final int NEIGHBOR_RIGHT  = 0;
    private static final int NEIGHBOR_TOP    = 1;
    private static final int NEIGHBOR_FRONT  = 2;
    private static final int NEIGHBOR_LEFT   = 3;
    private static final int NEIGHBOR_BOTTOM = 4;
    private static final int NEIGHBOR_BACK   = 5;

    /*
     * 0: neighbors right
     * 1: neighbors top
     * 2: neighbors front
     * 3: neighbors left
     * 4: neighbors bottom
     * 5: neighbors back
     */
    private final Node[]  neighbors = new Node[6];
    //    private       boolean isFixed;
    private       boolean isXFixed;
    private       boolean isYFixed;
    private       boolean isZFixed;
    //    private       boolean isWall;

    private Vector3d fixedPos;

    private Vector3d[] deltaPositions;
    private Vector3d[] deltaAcceleration;
    private Vector3d[] deltaVelocity;
    private double[] flow;
    private double[] previousFlow;
    private double[] deltaFlow;
    private final Vector3d gradientImbalance = new Vector3d();
    /** Experimental graph measurement; cubic nodes retain opposite-slot differences. */
    private boolean geometricDistanceImbalance;

    /**
     * Electric Field
     */
    private AVP eField;

    /**
     * Magnetic Field
     */
    private AVP bField;
    private double auxiliaryInitialX,auxiliaryInitialY,auxiliaryInitialZ;

    private final Vector3d ef           = new Vector3d();
    private Vector3d mf;
    private       Vector3d force        = new Vector3d();
    private       Vector3d acceleration = new Vector3d();
    private       Vector3d velocity     = new Vector3d();

    private static final Vector3d ZERO = new Vector3d();

    public Node(Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale) {
        super(parent, model, position, rotation, scale, EntityType.NODE);

    }

    private static Vector3d[] vectorCache() {
        Vector3d[] values=new Vector3d[6];for(int i=0;i<6;i++)values[i]=new Vector3d();return values;
    }
    public synchronized Vector3d[] getDeltaPositions(){if(deltaPositions==null)deltaPositions=vectorCache();return deltaPositions;}
    public synchronized Vector3d[] getDeltaAcceleration(){if(deltaAcceleration==null)deltaAcceleration=vectorCache();return deltaAcceleration;}
    public synchronized Vector3d[] getDeltaVelocity(){if(deltaVelocity==null)deltaVelocity=vectorCache();return deltaVelocity;}
    public synchronized double[] getFlow(){if(flow==null)flow=new double[6];return flow;}
    public synchronized double[] getPreviousFlow(){if(previousFlow==null)previousFlow=new double[6];return previousFlow;}
    public synchronized double[] getDeltaFlow(){if(deltaFlow==null)deltaFlow=new double[6];return deltaFlow;}
    public Vector3d getMf(){if(mf==null)mf=new Vector3d();return mf;}

    public void setIndex(Vector3i index) {
        this.index = index;
    }

    public boolean isIndex(int x, int y, int z) {
        return index.x == x && index.y == y && index.z == z;
    }

    public boolean isIndex(Vector3i index) {
        return isIndex(index.x, index.y, index.z);
    }

    public void setIndex(int x, int y, int z) {
        index.x = x;
        index.y = y;
        index.z = z;
    }

    public String printIndex() {
        return String.format("(%d, %d, %d)", index.x, index.y, index.z);
    }

    public synchronized AVP getEField() {
        if(eField==null){eField=new AVP();eField.p.set(fixedPos==null?position:fixedPos);eField.pBuf.set(auxiliaryInitialX,auxiliaryInitialY,auxiliaryInitialZ);}return eField;
    }

    public synchronized AVP getBField() {
        if(bField==null){bField=new AVP();bField.p.set(auxiliaryInitialX,auxiliaryInitialY,auxiliaryInitialZ);bField.pBuf.set(bField.p);}return bField;
    }

    /** Clears optional fields without allocating them for mechanical-only nodes. */
    public void resetAuxiliaryFieldMotion() {
        if(eField!=null){eField.v.zero();eField.a.zero();}
        if(bField!=null){bField.v.zero();bField.a.zero();}
    }

    public void addForce(Vector3d force) {
        this.force.set(force);
        //eField.a.add(force);
    }

    @Override
    public void setPosition(Vector3d position) {
        super.setPosition(position);
        if (fixedPos == null) fixedPos = new Vector3d();
        fixedPos.set(position);
        if(eField!=null)eField.p.set(position);
    }

    @Override
    public void initPosition(Vector3d position) {
        super.setPosition(position);
        auxiliaryInitialX=position.x;auxiliaryInitialY=position.y;auxiliaryInitialZ=position.z;
        if (fixedPos == null) fixedPos = new Vector3d();
        fixedPos.set(position);
        if(eField!=null){eField.p.set(position);eField.pBuf.set(position);}
        if(bField!=null){bField.p.set(position);bField.pBuf.set(position);}
    }

    public void setNeighbor(Neighbor neighbor, Node node) {
        this.neighbors[neighbor.ordinal()] = node;
    }

    /** Returns one direct graph neighbor. */
    public Node getNeighbor(Neighbor neighbor) {
        return neighbors[neighbor.ordinal()];
    }


    @Override
    public boolean isVisible() {
        return visible || (type == EntityType.WALL) && remModel.isWallVisible();
    }

    /**
     * Measures neighbor-distance imbalance. Experimental diamond nodes use physical
     * bond extension vectors; cubic nodes retain opposite-slot length differences.
     *
     * @return gradientImbalance = (flow[LEFT] - flow[RIGHT], flow[BOTTOM] - flow[TOP], flow[BACK] - flow[FRONT])
     */
    public Vector3d measureGradientImbalance() {
        if (geometricDistanceImbalance)
            return gradientImbalance.set(readBondDistanceImbalance());

        getDeltaPositions();getFlow();

        for (int i = 0; i < neighbors.length; i++) {
            if (neighbors[i] != null) {
                deltaPositions[i].set(neighbors[i].position).sub(position);
//                deltaAcceleration[i].set(neighbors[i].acceleration).sub(acceleration);
//                deltaVelocity[i].set(neighbors[i].velocity).sub(velocity);
                flow[i] = deltaPositions[i].length();
            }
        }


        return gradientImbalance.set(flow[NEIGHBOR_RIGHT] - flow[NEIGHBOR_LEFT],
                                     flow[NEIGHBOR_TOP] - flow[NEIGHBOR_BOTTOM],
                                     flow[NEIGHBOR_FRONT] - flow[NEIGHBOR_BACK]);
//        return gradientImbalance.set(flow[NEIGHBOR_LEFT] - flow[NEIGHBOR_RIGHT],
//                                     flow[NEIGHBOR_BOTTOM] - flow[NEIGHBOR_TOP],
//                                     flow[NEIGHBOR_BACK] - flow[NEIGHBOR_FRONT]);
    }

    /**
     * Reads the current neighbor-distance imbalance without changing any Node
     * field, cache, or transport state.
     *
     * @return a new physical bond-extension vector for experimental diamond nodes,
     *         or right-left, top-bottom and front-back differences for cubic nodes
     */
    public Vector3d readGradientImbalance() {
        if (geometricDistanceImbalance) return readBondDistanceImbalance();
        return new Vector3d(
                neighborDistance(NEIGHBOR_RIGHT) - neighborDistance(NEIGHBOR_LEFT),
                neighborDistance(NEIGHBOR_TOP) - neighborDistance(NEIGHBOR_BOTTOM),
                neighborDistance(NEIGHBOR_FRONT) - neighborDistance(NEIGHBOR_BACK));
    }

    private double neighborDistance(int neighborIndex) {
        Node neighbor = neighbors[neighborIndex];
        return neighbor == null ? 0.0 : neighbor.position.distance(position);
    }

    /**
     * Experimental local distance imbalance: sum of (length - restLength) times
     * the current unit direction to each direct neighbor. Units are length.
     * Rest lengths come from equilibrium positions, not storage-slot labels.
     * <br><br>
     * Missing bonds contribute nothing; incomplete boundary neighborhoods are not
     * equivalent to the four-bond bulk. This measurement enables no pressure or transport.
     * Reading leaves all Node fields unchanged. Collapsed/nonfinite bonds are rejected
     * because their physical direction is undefined.
     *
     * @return a new vector pointing toward net bond extension
     */
    public Vector3d readBondDistanceImbalance() {
        Vector3d result = new Vector3d();
        for (Node neighbor : neighbors) {
            if (neighbor == null) continue;
            Vector3d bond = new Vector3d(neighbor.position).sub(position);
            double length = bond.length();
            double restLength = neighbor.equilibriumPosition.distance(equilibriumPosition);
            if (!(length > 0) || !Double.isFinite(length)
                    || !(restLength > 0) || !Double.isFinite(restLength))
                throw new IllegalStateException("Distance imbalance requires finite, nonzero current and equilibrium bonds");
            result.fma((length - restLength) / length, bond);
        }
        return result;
    }

    /** Immutable mechanical inputs shared by every node in one simulation update. */
    public record FrameParameters(NodeFormulaVersion nodeFormulaVersion,
                                  double waveStep, double electricFactor,
                                  double magneticFactor, double absorption,
                                  boolean useFixedNodes) {
    }

    public void updateNodeProperties(NodeFormulaVersion nodeFormulaVersion) {
        // If set to false then the node stops updating its position
        if (!remModel.isSimulationUpdateActive()) return;

        if(remframework.opengl.model.Lattice.getInstance().isMechanicsOnly()) {
            updateNodeProperties(remframework.opengl.model.Lattice.getInstance().frameParameters());return;
        }
        updateNodeProperties(new FrameParameters(nodeFormulaVersion,
                project.getConstantWaveSpeed() / 6.0,
                project.getConstantElectricFactor(), project.getConstantMagneticFactor(),
                appSettings.getAbsorption(), appSettings.isUseFixedNodes()));
    }

    /** Applies one already-accepted frame using its stable parameter snapshot. */
    public void updateNodeProperties(FrameParameters parameters) {
        switch (parameters.nodeFormulaVersion()) {
            case v_0_3:
                updateNodeProperties_v0_3(parameters);
                break;
            case v_0_2:
                updateNodeProperties_v0_2(parameters);
                break;
            default:
                updateNodeProperties_v0_1(parameters);
                break;
        }
    }

    private Vector3d getNeighborEFieldPosition(int index) {
        return neighbors[index] == null ? ZERO : neighbors[index].getEField().p;
    }

    private Vector3d getNeighborBFieldPosition(int index) {
        return neighbors[index] == null ? ZERO : neighbors[index].getBField().p;
    }

    /**
     * .<br/>
     * <br/>
     * Some definitions:<br/>
     * <p style="margin-left: 30px;">
     * t = time																<br/>
     * a = acceleration														<br/>
     * v = node velocity														<br/>
     * N = neighbor position array[Right, Up, Front, Left, Down, Back]			<br/>
     * p = position (variable positionBuff)									<br/>
     * r = radiation (wave will fade when radiation < 1)						<br/>
     * k = constant light speed {k ∈ [0..1]}									<br/>
     * this constant is responsible for the speed of light.				<br/>
     * Meaning, changing this constant would change the speed of light.	<br/>
     * Decreasing this constant will result in a lower speed of light.		<br/>
     * </p>
     * <br/>
     * The fundamental node equation is simply based on motion formula:<br/>
     * <p style="margin-left: 30px;">
     * ∆v = avg(N) = k² / |N| * ∑(n – p) {n ∈ N}	<br/>
     * a = ∆v / ∆t									<br/>
     * v = v + a * t								<br/>
     * x = x0 + v0 * t + 1/2 a * t²				<br/>
     * </p>
     * <br/>
     * Note that v is not the speed of light, but is actually the speed of the node itself.
     * The propagation of the light wave is caused by the motion of the node
     * and the variable k determines the speed of light.<br/>
     * <br/>
     * If ∆t is one frame and one frame is one Planck time unit, then the short formula can be
     * written as:<br/>
     * <p style="margin-left: 30px;">
     * a = avg(N)        = k² / |N| * ∑(n – p) {n ∈ N}		<br/>
     * v = nVel(v, a)    = (v + a) * r						<br/>
     * p = nPos(v, p, a) = p + v + a * 0.5					<br/>
     * </p>
     * <br/>
     * A node can only have six neighbors in the field matrix, then the length of the array |N|
     * is 6.<br/>
     * <br/>
     * <b>Remarks:</b>
     * The higher the k value the higher the speed of light, but k cannot exceed 1.
     * If k is greater than 1, the affected nodes become unstable and eventually fly away.<br/>
     * <br/>
     *
     */
    public void updateNodeProperties_v0_1() {
        updateNodeProperties_v0_1(new FrameParameters(NodeFormulaVersion.v_0_1,
                project.getConstantWaveSpeed() / 6.0,
                project.getConstantElectricFactor(), project.getConstantMagneticFactor(),
                appSettings.getAbsorption(), appSettings.isUseFixedNodes()));
    }

    private void updateNodeProperties_v0_1(FrameParameters parameters) {
        boolean isFixed = type == EntityType.FIXED;
        boolean isWall  = type == EntityType.WALL;
        double  cws     = parameters.waveStep();
        double  gamma   = parameters.electricFactor();

        ef.set(0);

        double alpha = isWall ? absorptionFactor : 1.00;

        for (Node neighbor : neighbors) {
            if (neighbor != null) {
                //                if (isXFixed || isWall) {
                if (alpha == 0) {
                    //                    velocity.set(0);
                    //                    posBuff.set(fixedPos);
                }
                ef.add(neighbor.position).sub(position);
            }
        }

        acceleration.set(ef.add(force).mul(gamma * alpha));
        force.zero();

        // Note that the result of the addition or multiplication is stored in the object
        // variable itself.
        posBuff.add(velocity.add(acceleration.mul(cws))).add(acceleration.mul(0.5));

        //        if ((isXFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) posBuff.x = fixedPos.x;
        //        if ((isYFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) posBuff.y = fixedPos.y;
        //        if ((isZFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) posBuff.z = fixedPos.z;
        if (isFixed) posBuff.x = fixedPos.x;
        if (isFixed) posBuff.y = fixedPos.y;
        if (isFixed) posBuff.z = fixedPos.z;

        setColor(isFixed ? FIXED_COLOR : ef);
    }

    public void updateNodeProperties_v0_2() {
        updateNodeProperties_v0_2(new FrameParameters(NodeFormulaVersion.v_0_2,
                project.getConstantWaveSpeed() / 6.0,
                project.getConstantElectricFactor(), project.getConstantMagneticFactor(),
                appSettings.getAbsorption(), appSettings.isUseFixedNodes()));
    }

    private void updateNodeProperties_v0_2(FrameParameters parameters) {
        getMf();
        boolean isFixed = type == EntityType.FIXED;
        boolean isWall  = type == EntityType.WALL;
        double  cws     = parameters.waveStep();
        double  gamma   = parameters.electricFactor();
        double  beta    = parameters.magneticFactor();
        double  f       = 0.1;

        ef.set(0);
        mf.set(0);

        for (Node neighbor : neighbors) {
            if (neighbor != null) {
                if (isFixed || isWall) {
                    double alpha = isWall ? 0.00 : (1.0 - parameters.absorption());

                    if (alpha == 0) {
                        velocity.set(0);
                        posBuff.set(fixedPos);
                    }

                    if ((isXFixed || isFixed && parameters.useFixedNodes()) || isWall) {
                        ef.y += (neighbor.position.y - position.y) * alpha;
                        ef.z += (neighbor.position.z - position.z) * alpha;
                    }
                    if ((isYFixed || isFixed && parameters.useFixedNodes()) || isWall) {
                        ef.x += (neighbor.position.x - position.x) * alpha;
                        ef.z += (neighbor.position.z - position.z) * alpha;
                    }
                    if ((isZFixed || isFixed && parameters.useFixedNodes()) || isWall) {
                        ef.x += (neighbor.position.x - position.x) * alpha;
                        ef.y += (neighbor.position.y - position.y) * alpha;
                    }
                } else {
                    ef.add(neighbor.position).sub(position);
                }
            }
        }

        Vector3d nLeft = neighbors[NEIGHBOR_LEFT] == null ? ZERO
                                                          : neighbors[NEIGHBOR_LEFT].velocity;
        Vector3d nRight = neighbors[NEIGHBOR_RIGHT] == null ? ZERO
                                                            : neighbors[NEIGHBOR_RIGHT].velocity;
        Vector3d nTop = neighbors[NEIGHBOR_TOP] == null ? ZERO : neighbors[NEIGHBOR_TOP].velocity;
        Vector3d nBottom = neighbors[NEIGHBOR_BOTTOM] == null ? ZERO
                                                              : neighbors[NEIGHBOR_BOTTOM].velocity;
        Vector3d nFront = neighbors[NEIGHBOR_FRONT] == null ? ZERO
                                                            : neighbors[NEIGHBOR_FRONT].velocity;
        Vector3d nBack = neighbors[NEIGHBOR_BACK] == null ? ZERO
                                                          : neighbors[NEIGHBOR_BACK].velocity;

        mf.x = (nFront.y - nBack.y) * f - (nTop.z - nBottom.z) * f;
        mf.y = (nRight.z - nLeft.z) * f - (nFront.x - nBack.x) * f;
        mf.z = (nTop.x - nBottom.x) * f - (nRight.y - nLeft.y) * f;

        Vector3d e_f = ef.mul(gamma);
        Vector3d m_f = mf.mul(beta);

        acceleration.set(e_f.add(m_f)).add(force);

        // Note that the result of the addition or multiplication is stored in the object
        // variable itself.
        //positionBuff.add(velocity.add(e_f)).add(e_f.mul(0.5));
        posBuff.add(velocity.add(acceleration.mul(cws))).add(acceleration.mul(0.5));

        //			positionBuff.add(velocity.add(acceleration.mul(cws))).add(acceleration.mul
        //			(0.5));

        if ((isXFixed || isFixed && parameters.useFixedNodes()) || isWall) posBuff.x = fixedPos.x;
        if ((isYFixed || isFixed && parameters.useFixedNodes()) || isWall) posBuff.y = fixedPos.y;
        if ((isZFixed || isFixed && parameters.useFixedNodes()) || isWall) posBuff.z = fixedPos.z;

        setColor(isFixed ? FIXED_COLOR : ef);
    }

    public void updateNodeProperties_v0_3() {
        updateNodeProperties_v0_3(new FrameParameters(NodeFormulaVersion.v_0_3,
                project.getConstantWaveSpeed() / 6.0,
                project.getConstantElectricFactor(), project.getConstantMagneticFactor(),
                appSettings.getAbsorption(), appSettings.isUseFixedNodes()));
    }

    private void updateNodeProperties_v0_3(FrameParameters parameters) {
        getMf();getEField();getBField();
        boolean isFixed = type == EntityType.FIXED;
        double  cws     = parameters.waveStep();
        double  gamma   = parameters.electricFactor();
        double  beta    = parameters.magneticFactor();
        double  f       = 0.1;

        ef.set(0);
        mf.set(0);

        Vector3d nLeft   = getNeighborEFieldPosition(NEIGHBOR_LEFT);
        Vector3d nRight  = getNeighborEFieldPosition(NEIGHBOR_RIGHT);
        Vector3d nTop    = getNeighborEFieldPosition(NEIGHBOR_TOP);
        Vector3d nBottom = getNeighborEFieldPosition(NEIGHBOR_BOTTOM);
        Vector3d nFront  = getNeighborEFieldPosition(NEIGHBOR_FRONT);
        Vector3d nBack   = getNeighborEFieldPosition(NEIGHBOR_BACK);

        mf.x = (nFront.y - nBack.y) * f - (nTop.z - nBottom.z) * f;
        mf.y = (nRight.z - nLeft.z) * f - (nFront.x - nBack.x) * f;
        mf.z = (nTop.x - nBottom.x) * f - (nRight.y - nLeft.y) * f;

        bField.a.set(mf.mul(beta));
        bField.p.add(bField.v.add(bField.a.mul(cws))).add(bField.a.mul(0.5));


        nLeft   = getNeighborBFieldPosition(NEIGHBOR_LEFT);
        nRight  = getNeighborBFieldPosition(NEIGHBOR_RIGHT);
        nTop    = getNeighborBFieldPosition(NEIGHBOR_TOP);
        nBottom = getNeighborBFieldPosition(NEIGHBOR_BOTTOM);
        nFront  = getNeighborBFieldPosition(NEIGHBOR_FRONT);
        nBack   = getNeighborBFieldPosition(NEIGHBOR_BACK);

        ef.x = (nFront.y - nBack.y) * f - (nTop.z - nBottom.z) * f;
        ef.y = (nRight.z - nLeft.z) * f - (nFront.x - nBack.x) * f;
        ef.z = (nTop.x - nBottom.x) * f - (nRight.y - nLeft.y) * f;

        eField.a.set(ef.mul(gamma)).add(force);

        eField.p.add(eField.v.add(eField.a.mul(cws))).add(eField.a.mul(0.5));

        acceleration = eField.a;
        velocity     = eField.v;
        //position     = eField.p;
        posBuff = eField.p;

        setColor(isFixed ? FIXED_COLOR : ef);
    }

}
