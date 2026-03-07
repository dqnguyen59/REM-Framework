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

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.smartblackbox.qfs.opengl.model.*;
import org.smartblackbox.qfs.opengl.utils.Neighbor;
import org.smartblackbox.qfs.settings.AppSettings;
import org.smartblackbox.qfs.settings.QFSProject;

public class QFSNode extends Entity {

    private static final Vector3d FIXED_COLOR = new Vector3d(1f, 1f, 1f);

    private final AppSettings appSettings = AppSettings.getInstance();
    private final QFSProject qfsProject = QFSProject.getInstance();
    private final QFSModel qfsModel = qfsProject.getQfsModel();
    private final Scene scene = qfsProject.scene;

    private static final int NEIGHBOR_RIGHT = 0;
    private static final int NEIGHBOR_TOP = 1;
    private static final int NEIGHBOR_FRONT = 2;
    private static final int NEIGHBOR_LEFT = 3;
    private static final int NEIGHBOR_BOTTOM = 4;
    private static final int NEIGHBOR_BACK = 5;

    /*
     * 0: neighbors right
     * 1: neighbors top
     * 2: neighbors front
     * 3: neighbors left
     * 4: neighbors bottom
     * 5: neighbors back
     */
    private final QFSNode[] neighbors = new QFSNode[6];
    private boolean hasNeighbors;
    private boolean isFixed;
    private boolean isXFixed;
    private boolean isYFixed;
    private boolean isZFixed;
    private boolean isWall;

    private Vector3d fixedPos;

    /**
     * Electric Field
     */
    private AVP eField;

    /**
     * Magnetic Field
     */
    private AVP bField;

    private final Vector3d ef = new Vector3d();
    private final Vector3d efa = new Vector3d();
    private final Vector3d efRelP = new Vector3d();
    private final Vector3d efNeighborDir = new Vector3d();
    private final Vector3d bf = new Vector3d();
    private final Vector3d bfa = new Vector3d();
    private final Vector3d bfp = new Vector3d();
    private final Vector3d bfRelP = new Vector3d();
    private final Vector3d bfNeighborDir = new Vector3d();
    private Vector3d acceleration = new Vector3d();
    private Vector3d velocity = new Vector3d();
    private Vector3i index = new Vector3i(-1);
    private double customScale = -1;
    private Vector4f customColor;

    public QFSNode(Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale) {
        super(parent, model, position, rotation, scale);
    }

    public Vector3i getIndex() {
        return index;
    }

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

    public AVP getEField() {
        return eField = eField == null ? new AVP() : eField;
    }

    public AVP getBField() {
        return bField = bField == null ? new AVP() : bField;
    }

    public void addForce(Vector3d force) {
        eField.a.add(force);
    }

    @Override
    public void setPosition(Vector3d position) {
        super.setPosition(position);
        if (fixedPos == null) fixedPos = new Vector3d();
        fixedPos.set(position);
        //getEField().p.set(position);
    }

    @Override
    public void initPosition(Vector3d position) {
        super.setPosition(position);
        if (fixedPos == null) fixedPos = new Vector3d();
        fixedPos.set(position);
        getEField().p.set(position);
        getEField().pBuf.set(position);
        getBField().p.set(position);
        getBField().pBuf.set(position);
    }

    public QFSNode[] getNeighbors() {
        return neighbors;
    }

    public void incPosition(double x, double y, double z) {
        super.incPosition(x, y, z);
        eField.pBuf.add(x, y, z);
    }

    public void setNeighbor(Neighbor neighbor, QFSNode node) {
        this.neighbors[neighbor.ordinal()] = node;
        hasNeighbors = true;
    }

    public boolean isHasNeightbors() {
        return hasNeighbors;
    }

    public void setHasNeighbors(boolean hasNeighbors) {
        this.hasNeighbors = hasNeighbors;
    }

    @Override
    public boolean isVisible() {
        return isVisible || isWall && qfsModel.isWallVisible();
    }

    public boolean isFixed() {
        return isFixed;
    }

    public void setFixed(boolean isFixed) {
        this.isFixed = isFixed;
    }

    public boolean isXFixed() {
        return isXFixed;
    }

    public void setXFixed(boolean isXFixed) {
        this.isXFixed = isXFixed;
    }

    public boolean isYFixed() {
        return isYFixed;
    }

    public void setYFixed(boolean isYFixed) {
        this.isYFixed = isYFixed;
    }

    public boolean isZFixed() {
        return isZFixed;
    }

    public void setZFixed(boolean isZFixed) {
        this.isZFixed = isZFixed;
    }

    public boolean isWall() {
        return isWall;
    }

    public void setWall(boolean isWall) {
        this.isWall = isWall;
    }

    public Vector3d getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector3d velocity) {
        this.velocity = velocity;
    }

    /**
     * Custom scale must be set before calling updateMatrix().
     * <p>
     * Custom scale will be reset after updateMatrix() is called.
     *
     * @param customScale
     */
    public void setCustomScale(double customScale) {
        this.customScale = customScale;
    }

    /**
     * Custom color must be set before calling calcNewPosition().
     * <p>
     * Custom color will be null after calcNewPosition() is called.
     *
     * @param color
     */
    public void setCustomColor(Vector4f color) {
        customColor = color;
    }

    @Override
    public void updatePosition() {
        super.updatePosition();
        eField.p.set(eField.pBuf);
        bField.p.set(bField.pBuf);
    }

    @Override
    public void updateMatrix() {
        updatePosition();
        updateRotation();

        getTransformMatrix().identity().translate(position).setRotationXYZ(rotationRad.x, rotationRad.y,
                rotationRad.z).scale(
                settings.scale).scale(customScale > 0 ? customScale : scale);

        if (parent != null) parent.getTransformMatrix().mul(getTransformMatrix(), getTransformMatrix());
        transformMatrixfList[swapBufIndex].set(getTransformMatrix());
        customScale = -1;
    }

    private synchronized void setEMFieldColor(Vector3d emf) {
        Material m = getMaterial();

        Vector4f color = m.getDiffuseColor();
        float alpha = 0;
        float intensity = settings.getIntensity();
        float intensity2 = intensity * intensity;
        float intensity3 = intensity2 * intensity;
        float alphaIntensity = settings.getAlpha();

        if (customColor != null) {
            color.set(customColor).mul(2.0f);
            alpha = 1.0f;
        } else if (isSelected && !scene.isAnimated()) {
            color.set(settings.selectedColor).mul(2.0f);
            alpha = 1.0f;
        } else if (isHiLighted) {
            color.set(settings.hiLightColor).mul(2.0f);
            alpha = color.w;
        } else if (isWall) {
            color.set(settings.wallColor).mul(2.0f);
            alpha = color.w;
        } else if (isFixed) {
            color = settings.fixedColor;
            alpha = color.w;
        } else {
            switch (settings.getColorMode()) {
                case zColor:
                    color.x = (float) (Math.max(0, -emf.z) * intensity);
                    color.y = (float) (Math.abs(emf.y) * intensity);
                    color.z = (float) (Math.max(0, emf.z) * intensity);
                    color.w = 0;
                    break;
                case zColor2:
                    color.x = (float) (Math.max(0, -emf.z) * intensity2);
                    color.y = (float) (Math.abs(emf.y) * intensity2);
                    color.z = (float) (Math.max(0, emf.z) * intensity2);
                    color.w = 0;
                    break;
                case zColor3:
                    color.x = (float) (Math.max(0, -emf.z) * Math.max(0, -emf.z) * intensity3);
                    color.y = (float) (Math.abs(emf.y * emf.y) * intensity3);
                    color.z = (float) (Math.max(0, emf.z) * Math.max(0, emf.z) * intensity3);
                    color.w = 0;
                    break;
                case xyzColor:
                    color.x = (float) (Math.abs(emf.x)) * intensity;
                    color.y = (float) (Math.abs(emf.y)) * intensity;
                    color.z = (float) (Math.abs(emf.z)) * intensity;
                    color.w = 0;
                    break;
                case xyzColor2:
                    color.x = (float) (Math.abs(emf.x) * intensity2);
                    color.y = (float) (Math.abs(emf.y) * intensity2);
                    color.z = (float) (Math.abs(emf.z) * intensity2);
                    color.w = 0;
                    break;
                case xyzColor3:
                    color.x = (float) (emf.x * emf.x) * intensity3;
                    color.y = (float) (emf.y * emf.y) * intensity3;
                    color.z = (float) (emf.z * emf.z) * intensity3;
                    color.w = 0;
                    break;
                case normal:
                default:
                    color.x = settings.defaultNodeColor.x * intensity / 100.0f;
                    color.y = settings.defaultNodeColor.y * intensity / 100.0f;
                    color.z = settings.defaultNodeColor.z * intensity / 100.0f;
                    color.w = settings.defaultNodeColor.w * alphaIntensity;
                    alphaIntensity = color.w;
                    break;
            }
            alpha = (alphaIntensity * (m.getDiffuseColor().length()));
        }

        m.setAmbientColor(color);
        m.getDiffuseColor().w = alpha;
        m.getAmbientColor().w = m.getDiffuseColor().w;
        m.setShininess(settings.getShininess() * alpha);

        customColor = null;
    }

    /**
     * Note that this is not the full quantum field formula, only the electric field is simulated
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
     * The equation of Quantum Field is simply based on motion formula:<br/>
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
        // If set to false then the node stops updating its position
        if (qfsModel.isSimulating()) {
            double cws = qfsProject.getConstantWaveSpeed() / 6;
            double gamma = qfsProject.getConstantElectricFactor();
            double beta = qfsProject.getConstantMagneticFactor();
            double f = 0.1;

            ef.set(0);
            bf.set(0);

            for (QFSNode neighbor : neighbors) {
                if (neighbor != null) {
                    if (isFixed || isWall) {
                        double alpha = isWall ? 0.00 : (1.0 - appSettings.getReflectionFixedNodes());

                        if (alpha == 0) {
                            velocity.set(0);
                            posBuff.set(fixedPos);
                        }

                        if ((isXFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
                            ef.y += (neighbor.position.y - position.y) * alpha;
                            ef.z += (neighbor.position.z - position.z) * alpha;
                        }
                        if ((isYFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
                            ef.x += (neighbor.position.x - position.x) * alpha;
                            ef.z += (neighbor.position.z - position.z) * alpha;
                        }
                        if ((isZFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
                            ef.x += (neighbor.position.x - position.x) * alpha;
                            ef.y += (neighbor.position.y - position.y) * alpha;
                        }
                    } else {
                        ef.add(neighbor.position).sub(position);
                    }
                }
            }

            Vector3d nLeft = neighbors[NEIGHBOR_LEFT] == null ? new Vector3d()
                    : neighbors[NEIGHBOR_LEFT].position;
            Vector3d nRight = neighbors[NEIGHBOR_RIGHT] == null ? new Vector3d()
                    : neighbors[NEIGHBOR_RIGHT].position;
            Vector3d nTop = neighbors[NEIGHBOR_TOP] == null ? new Vector3d()
                    : neighbors[NEIGHBOR_TOP].position;
            Vector3d nBottom = neighbors[NEIGHBOR_BOTTOM] == null ? new Vector3d()
                    : neighbors[NEIGHBOR_BOTTOM].position;
            Vector3d nFront = neighbors[NEIGHBOR_FRONT] == null ? new Vector3d()
                    : neighbors[NEIGHBOR_FRONT].position;
            Vector3d nBack = neighbors[NEIGHBOR_BACK] == null ? new Vector3d()
                    : neighbors[NEIGHBOR_BACK].position;

            bf.x = (nFront.y - nBack.y) * f - (nTop.z - nBottom.z) * f;
            bf.y = (nRight.z - nLeft.z) * f - (nFront.x - nBack.x) * f;
            bf.z = (nTop.x - nBottom.x) * f - (nRight.y - nLeft.y) * f;

            Vector3d e_f = ef.mul(gamma);
            Vector3d m_f = bf.mul(beta);

            acceleration.set(e_f.add(m_f));

            // Note that the result of the addition or multiplication is stored in the object
            // variable itself.
            //positionBuff.add(velocity.add(e_f)).add(e_f.mul(0.5));
            posBuff.add(velocity.add(acceleration.mul(cws))).add(acceleration.mul(0.5));

            //			positionBuff.add(velocity.add(acceleration.mul(cws))).add(acceleration.mul
            //			(0.5));

            if ((isXFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) posBuff.x = fixedPos.x;
            if ((isYFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) posBuff.y = fixedPos.y;
            if ((isZFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) posBuff.z = fixedPos.z;
        }

        setEMFieldColor(isFixed ? FIXED_COLOR : ef);
    }

    private Vector3d getNeighborEFieldPosition(int index) {
        return neighbors[index] == null ? new Vector3d() : neighbors[index].eField.p;
    }

    private Vector3d getNeighborBFieldPosition(int index) {
        return neighbors[index] == null ? new Vector3d() : neighbors[index].bField.p;
    }

    public void updateNodeProperties_v0_2() {
        // If set to false then the node stops updating its position
        if (qfsModel.isSimulating()) {
            double cws = qfsProject.getConstantWaveSpeed() / 6;
            double gamma = qfsProject.getConstantElectricFactor();
            double beta = qfsProject.getConstantMagneticFactor();
            double f = 0.01;

            ef.set(0);
            bf.set(0);

            for (QFSNode neighbor : neighbors) {
                if (neighbor != null) {
                    if (isFixed || isWall) {
                        double alpha = isWall ? 0.00 : (1.0 - appSettings.getReflectionFixedNodes());

                        if (alpha == 0) {
                            velocity.set(0);
                            //eField.p.set(fixedPos);
                        }

//                        if ((isXFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
//                            ef.y += (neighbor.eField.p.y - eField.p.y) * alpha;
//                            ef.z += (neighbor.eField.p.z - eField.p.z) * alpha;
//                        }
//                        if ((isYFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
//                            ef.x += (neighbor.eField.p.x - eField.p.x) * alpha;
//                            ef.z += (neighbor.eField.p.z - eField.p.z) * alpha;
//                        }
//                        if ((isZFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
//                            ef.x += (neighbor.eField.p.x - eField.p.x) * alpha;
//                            ef.y += (neighbor.eField.p.y - eField.p.y) * alpha;
//                        }
                    } else {
//                        ef.add(neighbor.eField.p).sub(eField.p);

                        // Get eField relative position
//                        efRelP.set(neighbor.eField.p).sub(neighbor.basePosition);
                        efRelP.set(neighbor.eField.p).sub(neighbor.basePosition);
                        // Get bField direction relative to neighbor
                        bfNeighborDir.set(neighbor.bField.p).sub(bField.p).normalize();
                        // Calculate new bField acceleration
                        bfa.set(bfNeighborDir).cross(neighbor.eField.v);

                        bf.add(neighbor.bField.p).sub(bField.p).add(bfa.mul(1));

                        // Get bField relative velocity
                        //bfRelP.set(neighbor.bField.p).sub(neighbor.basePosition);
                        // Get eField direction relative to neighbor
                        efNeighborDir.set(bField.p).sub(neighbor.bField.p).normalize();
                        // Calculate new bField acceleration
                        efa.set(efNeighborDir).cross(neighbor.bField.v);

                        ef.add(neighbor.eField.p).sub(eField.p).add(efa);
//                        ef.add(neighbor.eField.pBuf).sub(basePosition);
                    }
                }
            }

            Vector3d e_f = ef.mul(gamma);
            Vector3d m_f = bf.mul(beta);

//            acceleration.set(e_f.add(m_f));
            eField.a.set(ef.mul(gamma));
            bField.a.set(bf.mul(beta));

            // Note that the result of the addition or multiplication is stored in the object
            // variable itself.
            //positionBuff.add(velocity.add(e_f)).add(e_f.mul(0.5));
//            posBuff.add(velocity.add(acceleration.mul(cws))).add(acceleration.mul(0.5));
            eField.pBuf.add(eField.v.add(eField.a.mul(cws))).add(eField.a.mul(0.5));
            bField.pBuf.add(bField.v.add(bField.a.mul(cws))).add(bField.a.mul(0.5));

            //			positionBuff.add(velocity.add(acceleration.mul(cws))).add(acceleration.mul
            //			(0.5));

            if ((isXFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
                eField.pBuf.x = fixedPos.x;
//                bField.pBuf.x = fixedPos.x;
            }
            if ((isYFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
                eField.pBuf.y = fixedPos.y;
//                bField.pBuf.y = fixedPos.y;
            }
            if ((isZFixed || isFixed && appSettings.isUseFixedNodes()) || isWall) {
                eField.pBuf.z = fixedPos.z;
//                bField.pBuf.z = fixedPos.z;
            }

            //eField.pBuf.set(ef);
            //bField.pBuf.set(bf);
            posBuff.set(eField.pBuf);
        }

        setEMFieldColor(isFixed ? FIXED_COLOR : posBuff);
    }

}
