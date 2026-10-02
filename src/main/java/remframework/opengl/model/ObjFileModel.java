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

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObjFileModel {

	private int id;
	private int vertexCount;
	private Material material;
	private float[] vertices;
	private float[] textureCoords;
	private float[] normals;
	private int[] indices;
	private final Map<Long, Integer> contextVaos = new ConcurrentHashMap<>();
	
	public ObjFileModel(int id, int vertexCount) {
		this.id = id;
		this.vertexCount = vertexCount;
		this.material = new Material();
		this.vertices = null;
		this.textureCoords = null;
		this.normals = null;
		this.indices = null;
		registerCurrentContext(id);
	}
	
	public ObjFileModel(int id, int vertexCount, Material material) {
		this.id = id;
		this.vertexCount = vertexCount;
		this.material = material;
		this.vertices = null;
		this.textureCoords = null;
		this.normals = null;
		this.indices = null;
		registerCurrentContext(id);
	}
	
	public ObjFileModel(int id, int vertexCount, Texture texture) {
		this.id = id;
		this.vertexCount = vertexCount;
		this.material = new Material(texture);
		this.vertices = null;
		this.textureCoords = null;
		this.normals = null;
		this.indices = null;
		registerCurrentContext(id);
	}

	/** Creates a model retaining its CPU geometry so shared OpenGL contexts can build local VAOs. */
	public ObjFileModel(int id, int vertexCount, float[] vertices, float[] textureCoords,
			float[] normals, int[] indices) {
		this(id, vertexCount);
		this.vertices = vertices.clone();
		this.textureCoords = textureCoords.clone();
		this.normals = normals == null ? null : normals.clone();
		this.indices = indices.clone();
	}
	
	public ObjFileModel(ObjFileModel model, Texture texture) {
		this.id = model.getId();
		this.vertexCount = model.getVertexCount();
		this.material = model.getMaterial();
		this.material.setTexture(texture);
		this.vertices = model.vertices == null ? null : model.vertices.clone();
		this.textureCoords = model.textureCoords == null ? null : model.textureCoords.clone();
		this.normals = model.normals == null ? null : model.normals.clone();
		this.indices = model.indices == null ? null : model.indices.clone();
		this.contextVaos.putAll(model.contextVaos);
	}
	
	public int getId() {
		long context = GLFW.glfwGetCurrentContext();
		if (context == 0L || vertices == null) return id;
		return contextVaos.computeIfAbsent(context, ignored -> createContextVao());
	}

	/** Registers the VAO created by the OBJ loader for the current context. */
	public void registerCurrentContext(int vao) {
		long context = GLFW.glfwGetCurrentContext();
		if (context != 0L) contextVaos.put(context, vao);
	}

	private int createContextVao() {
		int vao = GL30.glGenVertexArrays();
		GL30.glBindVertexArray(vao);
		storeIndices(indices);
		storeAttribute(0, 3, vertices);
		storeAttribute(1, 2, textureCoords);
		if (normals != null) storeAttribute(2, 3, normals);
		GL30.glBindVertexArray(0);
		return vao;
	}

	private static void storeIndices(int[] data) {
		int buffer = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer);
		GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
	}

	private static void storeAttribute(int attribute, int size, float[] data) {
		int buffer = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(attribute, size, GL11.GL_FLOAT, false, 0, 0);
		GL20.glEnableVertexAttribArray(attribute);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}
	
	public int getVertexCount() {
		return vertexCount;
	}

	public Material getMaterial() {
		return material;
	}

	public void setMaterial(Material material) {
		this.material= material;
	}

	public Texture getTexture() {
		return material.getTexture();			
	}
	
	public void setTexture(Texture texture) {
		material.setTexture(texture);			
	}
	
	public void setTexture(Texture texture, float reflactance) {
		material.setTexture(texture);
		material.setShininess(reflactance);
	}
	
}
