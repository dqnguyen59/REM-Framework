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
package remframework.opengl.model;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import remframework.settings.AppSettings;

public class Camera {
	
	private AppSettings appSettings = AppSettings.getInstance();

	private Vector3f position = new Vector3f(0, 0, 0);
	private Vector3f rotation = new Vector3f(0, 0, 0);
	
	private float fieldOfView = 45;
	private float z_near      = 0.01f;
	private float z_far = 1000f;

	private Matrix4f viewMatrix = new Matrix4f();
	private Matrix4f projectionMatrix = new Matrix4f();
	private final Vector3d orbitTarget = new Vector3d();
	private double orbitDistance;

	public Camera() {
	}
	
	public Camera(Vector3f position, Vector3f rotation) {
		this.position = position;
		this.rotation = rotation;
	}
	
	public float getFieldOfView() {
		return fieldOfView;
	}

	public void setFieldOfView(float fieldOfView) {
		this.fieldOfView = fieldOfView;
		updateProjectionMatrix();
	}

	public float getZ_near() {
		return z_near;
	}

	public void setZ_near(float z_near) {
		this.z_near = z_near;
		updateProjectionMatrix();
	}

	public float getZ_far() {
		return z_far;
	}

	public void setZ_far(float z_far) {
		this.z_far = z_far;
		updateProjectionMatrix();
	}

	public void movePosition(float x, float y, float z) {
		if (z != 0) {
			position.x += Math.sin(Math.toRadians(rotation.y)) * -1.0f * z;
			position.z += Math.cos(Math.toRadians(rotation.y)) * z;
		}
		if (x != 0) {
			position.x += Math.sin(Math.toRadians(rotation.y - 90)) * -1.0f * x;
			position.z += Math.cos(Math.toRadians(rotation.y - 90)) * x;
		}
		
		position.y += y;
	}
	
	public void setPosition(float x, float y, float z) {
		this.position.x = x;
		this.position.y = y;
		this.position.z = z;
	}
	
	public void moveRotation(float x, float y, float z) {
		this.rotation.x += x;
		this.rotation.y += y;
		this.rotation.z += z;
	}
	
	public Vector3f getPosition() {
		return position;
	}

	public Vector3f getRotation() {
		return rotation;
	}

	public void setRotation(float x, float y, float z) {
		this.rotation.x = x;
		this.rotation.y = y;
		this.rotation.z = z;
	}

	/** Sets the target used by camera-centered orbit operations. */
	public void setOrbitTarget(Vector3d target) {
		orbitTarget.set(target);
		orbitDistance = new Vector3d(position).distance(orbitTarget);
	}

	/** Sets position and target together without changing any scene entity. */
	public void setOrbitState(Vector3d position, Vector3d target) {
		this.position.set((float) position.x, (float) position.y, (float) position.z);
		orbitTarget.set(target);
		orbitDistance = position.distance(target);
	}

	/** Restores a complete orbit state, including a deliberately preserved radius. */
	public void setOrbitState(Vector3d position, Vector3d target, double distance) {
		this.position.set((float) position.x, (float) position.y, (float) position.z);
		orbitTarget.set(target);
		orbitDistance = distance;
	}

	/** Recomputes yaw/pitch so the camera looks at the current orbit target. */
	public void lookAtOrbitTarget() {
		Vector3d offset = new Vector3d(position).sub(orbitTarget);
		if (offset.lengthSquared() == 0.0) return;
		double distance = offset.length();
		setRotation((float) Math.toDegrees(Math.asin(offset.y / distance)),
				(float) Math.toDegrees(Math.atan2(-offset.x, offset.z)), 0.0f);
		orbitDistance = distance;
	}

	public Vector3d getOrbitTarget() {
		return new Vector3d(orbitTarget);
	}

	public double getOrbitDistance() {
		return orbitDistance;
	}

	/** Moves the orbit target with a live object while preserving the camera offset. */
	public void followOrbitTarget(Vector3d target) {
		Vector3d delta = new Vector3d(target).sub(orbitTarget);
		if (delta.lengthSquared() != 0.0) {
			position.add((float) delta.x, (float) delta.y, (float) delta.z);
			orbitTarget.set(target);
		}
	}

	/** Orbits around the target, preserving distance and recomputing the look direction. */
	public void orbitAroundTarget(float rotationX, float rotationY, float rotationZ) {
		if (orbitDistance <= 0.0)
			orbitDistance = new Vector3d(position).distance(orbitTarget);
		moveRotation(rotationX, rotationY, rotationZ);
		applyOrbitOffset();
	}

	/** Places the camera above the target, looking down with zero roll. */
	public void resetOrbitTopView() {
		if (orbitDistance <= 0.0)
			orbitDistance = new Vector3d(position).distance(orbitTarget);
		setRotation(90.0f, 0.0f, 0.0f);
		applyOrbitOffset();
	}

	/** Focuses the orbit at a chosen distance without changing the target. */
	public void focusOrbit(double distance) {
		orbitDistance = Math.max(0.001, distance);
		setRotation(0.0f, 0.0f, 0.0f);
		applyOrbitOffset();
	}

	/** Moves along the camera-to-target ray; positive amount zooms toward the target. */
	public void zoomAroundTarget(double amount, double minimumDistance, double maximumDistance) {
		double distance = new Vector3d(position).distance(orbitTarget);
		if (distance <= 0.0) return;
		double nextDistance = Math.clamp(distance - amount,
				minimumDistance, maximumDistance);
		Vector3d directionFromTarget = new Vector3d(position)
				.sub(orbitTarget).normalize();
		position.set((float) (orbitTarget.x + directionFromTarget.x * nextDistance),
				(float) (orbitTarget.y + directionFromTarget.y * nextDistance),
				(float) (orbitTarget.z + directionFromTarget.z * nextDistance));
		orbitDistance = nextDistance;
	}

	private void applyOrbitOffset() {
		double pitch = Math.toRadians(rotation.x);
		double yaw = Math.toRadians(rotation.y);
		double horizontal = orbitDistance * Math.cos(pitch);
		position.x = (float) (orbitTarget.x - Math.sin(yaw) * horizontal);
		position.y = (float) (orbitTarget.y + Math.sin(pitch) * orbitDistance);
		position.z = (float) (orbitTarget.z + Math.cos(yaw) * horizontal);
	}

	public Matrix4f getViewMatrix() {
		return viewMatrix.identity()
		.rotate((float) Math.toRadians(rotation.x), new Vector3f(1, 0, 0))
		.rotate((float) Math.toRadians(rotation.y), new Vector3f(0, 1, 0))
		.rotate((float) Math.toRadians(rotation.z), new Vector3f(0, 0, 1))
		.translate(-position.x, -position.y, -position.z);
	}
	
	public Matrix4f getProjectionMatrix() {
		return projectionMatrix;
	}

	public Matrix4f updateProjectionMatrix() {
		float aspectRatio = (float) appSettings.getWindowWidth() / appSettings.getWindowHeight();
		return projectionMatrix.setPerspective(
				(float) Math.toRadians(fieldOfView),
				aspectRatio,
				z_near,
				z_far
		);
	}

}
