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
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.smartblackbox.qfs.opengl.model.Material;
import org.smartblackbox.qfs.opengl.model.ObjFileModel;
import org.smartblackbox.qfs.settings.Project;
import org.smartblackbox.qfs.settings.Settings;

@Getter
@Setter
public class Entity {
	
	protected Project  project  = Project.getInstance();
	protected Settings settings = project.settings;
	
	protected String name = "";
	protected Entity parent = null;
	protected ObjFileModel model;
	protected Material material;

    /**
     *  If overrideModelMaterial is true then use the entity material,
     *  otherwise use material from the model.
     *  This is useful, if each entity must have its own material.
     */
    protected boolean overrideModelMaterial = false;

	/**
	 * Fixed equilibrium position of this node in the lattice.
	 *
	 * This represents the node location when the lattice is not disturbed.
	 * Identity oscillations and recursive field effects modify the node state
	 * around this reference position.
	 */
	protected Vector3d equilibriumPosition = new Vector3d();

    // Swap between two positions buffers. One for reading and the other for writing.
	// So data will not be messed up when doing some interacting calculations.
	// After all has been nodes has been calculated, then copy the positionBuff to position.
	protected Vector3d position = new Vector3d();
	protected Vector3d posBuff  = new Vector3d();
	// swapBufIndex can only be 0 or 1
	protected static int swapBufIndex = 0;

	protected Vector3d direction = new Vector3d(0, 0, 1);
	protected Vector3d rotation = new Vector3d();
	protected Vector3d rotationRad = new Vector3d();
	protected double scale;
	private Matrix4d     transformMatrix       = new Matrix4d();
	protected Matrix4f[] transformMatrix4fList = new Matrix4f[2];

	protected boolean isVisible = true;
	protected boolean isSelected = false;
	protected boolean isHiLighted = false;
	
	public Entity(Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale) {
		this.parent   = parent;
		this.model    = model;
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

	/**
	 * Restores the entity position to its equilibrium position.
	 *
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
	 * 
	 * @param x
	 * @param y
	 * @param z
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
	 * 
	 * @param x
	 * @param y
	 * @param z
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
	 *
	 * @param x
	 * @param y
	 * @param z
	 */
	public void decRotation(double x, double y, double z) {
		this.rotation.x -= x;
		this.rotation.y -= y;
		this.rotation.z -= z;
		updateRotation();
	}

	protected synchronized void updateRotation() {
		rotationRad.x = (double) Math.toRadians(rotation.x);
		rotationRad.y = (double) Math.toRadians(rotation.y);
		rotationRad.z = (double) Math.toRadians(rotation.z);
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
	}

	/**
	 * The returned matrix is divided in two fold, enables to use parallel processing.</br></br>
	 * While one matrix is used for sending data to GPU,
	 * the other matrix can be used to prepare / processing new updated data for the next frame.</br></br>
	 * Use the method {@link #swapBuffer()} to swap between these matrices.</br></br>
	 * When all data for the next frame has been processed,
	 * call {@link #swapBuffer()} before sending data to the GPU.
	 * </br></br>
	 * @return the processed matrix buffer
	 */
	public Matrix4f getTransformMatrix4f() {
		return transformMatrix4fList[1 - swapBufIndex];
	}

}
