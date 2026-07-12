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
import org.joml.*;
import org.smartblackbox.qfs.opengl.model.Material;
import org.smartblackbox.qfs.opengl.model.ObjFileModel;
import org.smartblackbox.qfs.settings.Project;
import org.smartblackbox.qfs.settings.Settings;

import java.lang.Math;

@Getter
@Setter
public class Entity {

    protected static final Vector3d FIXED_COLOR = new Vector3d(1f, 1f, 1f);

    protected EntityType type;

    protected Project  project  = Project.getInstance();
    protected Settings settings = project.settings;

    protected String       name  = "";
    protected Entity       parent;
    protected ObjFileModel model;
    protected Material     material;
    protected Vector3i     index = new Vector3i(-1);

    /**
     * If overrideModelMaterial is true then use the entity material,
     * otherwise use material from the model.
     * This is useful, if each entity must have its own material.
     */
    protected boolean overrideModelMaterial = false;

    /**
     * Fixed equilibrium position of this node in the lattice.
     * <p>
     * This represents the node location when the lattice is not disturbed.
     * Identity oscillations and recursive field effects modify the node state
     * around this reference position.
     */
    protected Vector3d equilibriumPosition = new Vector3d();

    // Swap between two positions buffers. One for reading and the other for writing.
    // So data will not be messed up when doing some interacting calculations.
    // After all has been nodes has been calculated, then copy the positionBuff to position.
    protected        Vector3d position     = new Vector3d();
    protected        Vector3d posBuff      = new Vector3d();
    // swapBufIndex can only be 0 or 1
    protected static int      swapBufIndex = 0;

    protected Vector3d   direction             = new Vector3d(0, 0, 1);
    protected Vector3d   rotation              = new Vector3d();
    protected Vector3d   rotationRad           = new Vector3d();
    protected double     scale;
    private   Matrix4d   transformMatrix       = new Matrix4d();
    protected Matrix4f[] transformMatrix4fList = new Matrix4f[2];

    /**
     * Custom scale must be set before calling updateMatrix().
     * Custom scale will be reset after updateMatrix() is called.
     */
    protected double customScale = -1;

    /**
     * Custom color must be set before calling calcNewPosition().
     * Custom color will be null after calcNewPosition() is called.
     */
    protected Vector4f customColor;

    protected boolean isVisible   = true;
    protected boolean isSelected  = false;
    protected boolean isHiLighted = false;

    public Entity(
            Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale,
            EntityType type) {
        this.parent = parent;
        this.type   = type;
        this.model  = model;
        this.equilibriumPosition.set(position);
        this.position.set(position);
        initPosition(position);
        this.rotation.set(rotation);
        this.scale               = scale;
        transformMatrix4fList[0] = new Matrix4f();
        transformMatrix4fList[1] = new Matrix4f();

        if (model != null)
            material = new Material(model.getMaterial());
        else
            material = new Material();

        updateMatrix();
    }

    public boolean isNode() {
        return type == EntityType.NODE;
    }

    public boolean isFixed() {
        return type == EntityType.FIXED;
    }

    public boolean isWall() {
        return type == EntityType.WALL;
    }

    public void setColor(Vector3d emf) {
        Material m = getMaterial();

        Vector4f color          = m.getDiffuseColor();
        float    alpha          = 0;
        float    intensity      = settings.getIntensity();
        float    intensity2     = intensity * intensity;
        float    intensity3     = intensity2 * intensity;
        float    alphaIntensity = settings.getAlpha();

        if (customColor != null) {
            color.set(customColor).mul(2.0f);
            alpha = 1.0f;
        } else if (isSelected && !(type == EntityType.ANIMATED)) {
            color.set(settings.selectedColor).mul(2.0f);
            alpha = 1.0f;
        } else if (isHiLighted) {
            color.set(settings.hiLightColor).mul(2.0f);
            alpha = color.w;
        } else if (type == EntityType.WALL) {
            color.set(settings.wallColor).mul(2.0f);
            alpha = color.w;
        } else if (type == EntityType.FIXED) {
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
     * Restores the entity position to its equilibrium position.
     * <p>
     * The equilibrium position represents the undisturbed lattice location.
     * Dynamic field effects may temporarily displace the entity from this
     * reference position.
     */
    public void resetToEquilibrium() {
        posBuff.set(equilibriumPosition);
    }

    public void setPosition(Vector3d position) {
        posBuff.set(position);
    }

    public void initPosition(Vector3d position) {
        posBuff.set(position);
    }

    public void setPosition(double x, double y, double z) {
        posBuff.set(x, y, z);
    }

    public void incPosition(Vector3d p) {
        incPosition(p.x, p.y, p.z);
    }

    public void incPosition(double x, double y, double z) {
        posBuff.add(x, y, z);
    }

    public void updatePosition() {
        position.x = posBuff.x;
        position.y = posBuff.y;
        position.z = posBuff.z;
    }

    public void setDirection(Vector3d direction) {
        this.direction.x = direction.x;
        this.direction.y = direction.y;
        this.direction.z = direction.z;
    }

    /**
     * Values are in degrees.
     * Range: 0..360
     */
    public void setRotation(double x, double y, double z) {
        this.rotation.x = x;
        this.rotation.y = y;
        this.rotation.z = z;
        updateRotation();
    }

    /**
     * Values are in degrees.
     * Range: 0..360
     */
    public void incRotation(double x, double y, double z) {
        this.rotation.x += x;
        this.rotation.y += y;
        this.rotation.z += z;
        updateRotation();
    }

    /**
     * Values are in degrees.
     * Range: 0..360
     */
    public void decRotation(double x, double y, double z) {
        this.rotation.x -= x;
        this.rotation.y -= y;
        this.rotation.z -= z;
        updateRotation();
    }

    protected void updateRotation() {
        rotationRad.x = Math.toRadians(rotation.x);
        rotationRad.y = Math.toRadians(rotation.y);
        rotationRad.z = Math.toRadians(rotation.z);
    }

    public Material getMaterial() {
        if (overrideModelMaterial || model == null) return material;
        else return model.getMaterial();
    }

    /**
     * See {@link #getTransformMatrix4f()}.
     */
    public static void swapBuffer() {
        swapBufIndex = 1 - swapBufIndex;
    }

    public void updateMatrix() {
        updatePosition();
        updateRotation();

        transformMatrix
                .identity()
                .translate(position)
                .setRotationXYZ(rotationRad.x, rotationRad.y, rotationRad.z)
                .scale(scale);

        if (parent != null)
            parent.transformMatrix.mul(transformMatrix, transformMatrix);
        transformMatrix4fList[swapBufIndex].set(transformMatrix);

        updatePosition();
        updateRotation();

        transformMatrix
                .identity()
                .translate(position)
                .setRotationXYZ(rotationRad.x, rotationRad.y, rotationRad.z)
                .scale(settings.scale)
                .scale(customScale > 0 ? customScale : scale);

        if (parent != null) parent.getTransformMatrix().mul(getTransformMatrix(), getTransformMatrix());
        transformMatrix4fList[swapBufIndex].set(getTransformMatrix());
        customScale = -1;
        setColor(FIXED_COLOR);
    }

    /**
     * The returned matrix is divided in twofold, enables to use parallel processing.</br></br>
     * While one matrix is used for sending data to GPU,
     * the other matrix can be used to prepare / processing new updated data for the next frame.</br></br>
     * Use the method {@link #swapBuffer()} to swap between these matrices.</br></br>
     * When all data for the next frame has been processed,
     * call {@link #swapBuffer()} before sending data to the GPU.
     * </br></br>
     *
     * @return the processed matrix buffer
     */
    public Matrix4f getTransformMatrix4f() {
        return transformMatrix4fList[1 - swapBufIndex];
    }

}
