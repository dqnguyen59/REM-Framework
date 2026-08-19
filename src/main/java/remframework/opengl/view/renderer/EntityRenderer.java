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
package remframework.opengl.view.renderer;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import remframework.helpers.Constants;
import remframework.opengl.model.Camera;
import remframework.opengl.model.ObjFileModel;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.lights.DirectionalLight;
import remframework.opengl.model.lights.Light;
import remframework.settings.Project;
import remframework.settings.Settings;
import remframework.helpers.Utils;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.nio.FloatBuffer;

public class EntityRenderer implements IRenderer {

	Project project = Project.getInstance();
	
	Shader shader;
    @Getter
    private Map<ObjFileModel, List<Entity>> entities = new  HashMap<>();

	private int numLights = Constants.INITIAL_NUM_LIGHTS;
	private String vertexShader;
	private String fragmentShader;
	private final Map<ObjFileModel, Integer> instanceBuffers = new HashMap<>();
	private final Map<ObjFileModel, List<Entity>> visibleEntityCache = new HashMap<>();
	private boolean visibleEntityCacheDirty = true;
	private boolean cachedInspectionMode;
	private long cachedInspectionVisibilityVersion = Long.MIN_VALUE;
	private static final int INSTANCE_FLOATS = 30;
	private static final int INSTANCE_STRIDE_BYTES = INSTANCE_FLOATS * Float.BYTES;
	private static final int INSTANCE_THRESHOLD = 32;
	private long instanceRenderWindowStartedNanos;
	private long instanceRenderWindowNanos;
	private long instanceRenderWindowCalls;
	private long instanceRenderWindowInstances;
	private long instanceRenderLastNanos;
	private long instanceBufferUploadWindowNanos;
	private long instanceBufferUploadWindowCalls;
	private long instanceBufferUploadLastNanos;
	private long instanceBufferUploadMaxNanos;
	private long instancePreparationWindowNanos;
	private long instancePreparationLastNanos;
	private long instancePreparationMaxNanos;
	private long instanceDrawWindowNanos;
	private long instanceDrawLastNanos;
	private long instanceDrawMaxNanos;
	private long renderWindowStartedNanos;
	private long renderWindowNanos;
	private long renderWindowCalls;
	private long renderLastNanos;
	
	public EntityRenderer() throws Exception {
		entities = new HashMap<>();
		shader = new Shader();
	}
	
	@Override
	public void init() throws Exception {
		vertexShader = Utils.loadResource(Constants.SHADER_NODE_VERTEX_FILE);
		fragmentShader = Utils.loadResource(Constants.SHADER_NODE_FRAGMENT_FILE);
		updateShaders();
	}

	public void updateShaders() {
		try {
			shader.createVertextShader(vertexShader);
			String fragmentShader = this.fragmentShader.replace("%MAX_LIGHTS%", "" + numLights);
			shader.createFragmentShader(fragmentShader);
			shader.link();
			shader.createUniform("textureSampler");
			shader.createUniform("transformationMatrix");
			shader.createUniform("projectionMatrix");
			shader.createUniform("viewMatrix");
			shader.createUniform("ambientLight");
			shader.createMaterialUniform("material");
			shader.createUniform("specularPower");
			shader.createUniform("depthVisibility");
			shader.createUniform("ignoreLightIndex");
			shader.createUniform("useInstanceMaterial");
			shader.createDirectionalLightUniform("directionalLight");
			shader.createLightListUniform("lights", numLights);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void renderLights(DirectionalLight directionalLight, List<Light> lights, Shader shader) {
		if (numLights != lights.size()) {
			numLights = lights.size();
			updateShaders();
			try {
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		shader.setUniform("ambientLight", Constants.AMBIENT_LIGHT);
		shader.setUniform("specularPower", Constants.SPECULAR_POWER);
		shader.setUniform("directionalLight", directionalLight);
		
		for (int i = 0; i < numLights; i++) {
			shader.setUniform("lights", lights.get(i), i);
			shader.setUniform("ignoreLightIndex", i);
			renderLight(shader, lights.get(i));
		}
	}

	public void renderLight(Shader shader, Light light) {
		if (light.getIntensity() > 0) {
			light.updateMatrix();
			ObjFileModel model = light.getModel();
			bind(model);
			shader.setUniform("material", light.getMaterial());
			shader.setUniform("transformationMatrix", light.getTransformMatrix4f());
			GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);

			if (light.isSpotLight()) {
				model = light.getModelSpotLight();
				bind(model);
				shader.setUniform("material", light.getSpotLightHolder().getMaterial());
				shader.setUniform("transformationMatrix", light.getSpotLightHolder().getTransformMatrix4f());
				GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
			}
		}
	}
	
	@Override
	public void render(Camera camera, Scene scene) {
		long startedNanos = System.nanoTime();
		shader.bind();
		shader.setUniform("textureSampler", 0);
		shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
		shader.setUniform("viewMatrix", camera.getViewMatrix());
		// Inspector geometry is diagnostic and must not fade as the camera moves through
		// the normal depth-visibility field. The shader and ordinary render path remain unchanged.
		shader.setUniform("depthVisibility", scene.isInspectionMode()
				? 0.0f : project.settings.getDepthVisibilityFactor());

		renderLights(scene.getDirectionalLight(), project.getLights(), shader);
		shader.setUniform("useInstanceMaterial", 0);
		if (project.settings.getRenderingMethod() == Settings.RenderingMethod.DEFAULT_RENDERING) {
			renderDefault(scene);
		} else {
			renderEnhanced(scene);
		}

		unbind();
		shader.unbind();
		recordRenderMeasurement(startedNanos);
	}

	/** Invalidates cached visibility after the renderer entity set changes. */
	public void invalidateVisibleEntityCache() {
		visibleEntityCacheDirty = true;
		visibleEntityCache.clear();
	}

	private void recordRenderMeasurement(long startedNanos) {
		long now = System.nanoTime();
		long elapsedNanos = now - startedNanos;
		if (renderWindowStartedNanos == 0L) renderWindowStartedNanos = startedNanos;
		renderLastNanos = elapsedNanos;
		renderWindowNanos += elapsedNanos;
		renderWindowCalls++;
		if (now - renderWindowStartedNanos >= 500_000_000L) {
			double averageMillis = renderWindowNanos
					/ (double) renderWindowCalls / 1_000_000.0;
			System.out.printf("EntityRenderer.render: calls=%d last=%.3f ms average=%.3f ms%n",
					renderWindowCalls, renderLastNanos / 1_000_000.0, averageMillis);
			renderWindowStartedNanos = now;
			renderWindowNanos = 0L;
			renderWindowCalls = 0L;
		}
	}

	private void renderDefault(Scene scene) {
		for (ObjFileModel model : entities.keySet()) {
			if (model == null) continue;
			bind(model);
			shader.setUniform("ignoreLightIndex", -1);
			List<Entity> entityList = entities.get(model);
			for (Entity entity : entityList) {
				boolean effectiveVisible = scene.isInspectionMode()
						? entity.isInspectionVisible() : entity.isVisible();
				if (!effectiveVisible) continue;
				if (entity.isOverrideModelMaterial()) shader.setUniform("material", entity.getMaterial());
				shader.setUniform("transformationMatrix", entity.getTransformMatrix4f());
				GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
			}
		}
	}

	private void renderEnhanced(Scene scene) {
		ensureVisibleEntityCache(scene);

		for (ObjFileModel model : entities.keySet()) {
			if (model != null) {
				bind(model);
				
				List<Entity> entityList = entities.get(model);
				shader.setUniform("ignoreLightIndex", -1);
				List<Entity> visibleEntities = visibleEntityCache.getOrDefault(model, List.of());
				if (canInstance(visibleEntities)) {
					renderInstanced(model, visibleEntities, scene);
					continue;
				}

//				for (Entity entity : visibleEntities) {
//					if (entity.isOverrideModelMaterial()) {
//						shader.setUniform("material", entity.getMaterial());
//					}
//					shader.setUniform("transformationMatrix", entity.getTransformMatrix4f());
//					GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
//				}
			}
		}

	}

	private void ensureVisibleEntityCache(Scene scene) {
		boolean inspectionMode = scene.isInspectionMode();
		long inspectionVersion = scene.getInspectionVisibilityVersion();
		if (!visibleEntityCacheDirty
				&& cachedInspectionMode == inspectionMode
				&& cachedInspectionVisibilityVersion == inspectionVersion) return;

		visibleEntityCache.clear();
		for (Map.Entry<ObjFileModel, List<Entity>> entry : entities.entrySet()) {
			List<Entity> visible = visibleEntities(entry.getValue(), scene);
			visibleEntityCache.put(entry.getKey(), visible);
		}
		cachedInspectionMode = inspectionMode;
		cachedInspectionVisibilityVersion = inspectionVersion;
		visibleEntityCacheDirty = false;
	}

	private List<Entity> visibleEntities(List<Entity> entityList, Scene scene) {
		List<Entity> visible = new java.util.ArrayList<>(entityList.size());
		for (Entity entity : entityList) {
			if (scene.isInspectionMode() ? entity.isInspectionVisible() : entity.isVisible())
				visible.add(entity);
		}
		return visible;
	}

	private boolean canInstance(List<Entity> visibleEntities) {
		if (visibleEntities.size() < INSTANCE_THRESHOLD) return false;
		for (Entity entity : visibleEntities) {
			if (!entity.isOverrideModelMaterial() || entity.getMaterial().hasTexture()) return false;
		}
		return true;
	}

	private void renderInstanced(ObjFileModel model, List<Entity> visibleEntities) {
		renderInstanced(model, visibleEntities, null);
	}

	private void renderInstanced(ObjFileModel model, List<Entity> visibleEntities, Scene scene) {
		long startedNanos = System.nanoTime();
		int bufferId = instanceBuffers.computeIfAbsent(model, this::createInstanceBuffer);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
		long preparationStartedNanos = System.nanoTime();
		FloatBuffer data = preparedInstanceData(scene, model);
		boolean ownsData = data == null;
		int instanceCount = ownsData ? visibleEntities.size() : scene.getRenderInstanceEntityCount(model);
		if (ownsData) data = MemoryUtil.memAllocFloat(visibleEntities.size() * INSTANCE_FLOATS);
		try {
			if (ownsData) {
				for (Entity entity : visibleEntities) {
					entity.getTransformMatrix4f().get(data);
					data.position(data.position() + 16);
					put(data, entity.getMaterial().getAmbientIntensityColor());
					put(data, entity.getMaterial().getDiffuseIntensityColor());
					put(data, entity.getMaterial().getSpecularIntensityColor());
					data.put((float) entity.getMaterial().getShininess());
					data.put(1.0f);
				}
				data.flip();
			}
			long preparationElapsedNanos = System.nanoTime() - preparationStartedNanos;
			instancePreparationLastNanos = preparationElapsedNanos;
			instancePreparationWindowNanos += preparationElapsedNanos;
			instancePreparationMaxNanos = Math.max(instancePreparationMaxNanos,
					preparationElapsedNanos);
			long bufferUploadStartedNanos = System.nanoTime();
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
			long bufferUploadElapsedNanos = System.nanoTime() - bufferUploadStartedNanos;
			instanceBufferUploadLastNanos = bufferUploadElapsedNanos;
			instanceBufferUploadWindowNanos += bufferUploadElapsedNanos;
			instanceBufferUploadWindowCalls++;
			instanceBufferUploadMaxNanos = Math.max(instanceBufferUploadMaxNanos,
					bufferUploadElapsedNanos);
		} finally {
			if (ownsData) MemoryUtil.memFree(data);
		}
		shader.setUniform("useInstanceMaterial", 1);
		GL30.glBindVertexArray(model.getId());
		long drawStartedNanos = System.nanoTime();
		GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, model.getVertexCount(),
				GL11.GL_UNSIGNED_INT, 0, instanceCount);
		long drawElapsedNanos = System.nanoTime() - drawStartedNanos;
		instanceDrawLastNanos = drawElapsedNanos;
		instanceDrawWindowNanos += drawElapsedNanos;
		instanceDrawMaxNanos = Math.max(instanceDrawMaxNanos, drawElapsedNanos);
		shader.setUniform("useInstanceMaterial", 0);

		long elapsedNanos = System.nanoTime() - startedNanos;
		if (instanceRenderWindowStartedNanos == 0L)
			instanceRenderWindowStartedNanos = startedNanos;
		instanceRenderLastNanos = elapsedNanos;
		instanceRenderWindowNanos += elapsedNanos;
		instanceRenderWindowCalls++;
		instanceRenderWindowInstances += instanceCount;
		if (System.nanoTime() - instanceRenderWindowStartedNanos >= 500_000_000L) {
			double averageMillis = instanceRenderWindowNanos
					/ (double) instanceRenderWindowCalls / 1_000_000.0;
			double bufferUploadAverageMillis = instanceBufferUploadWindowNanos
					/ (double) instanceBufferUploadWindowCalls / 1_000_000.0;
			System.out.printf("EntityRenderer.renderInstanced: calls=%d instances=%d "
					+ "last=%.3f ms average=%.3f ms; preparation "
					+ "last=%.3f ms average=%.3f ms max=%.3f ms; glBufferData "
					+ "last=%.3f ms average=%.3f ms max=%.3f ms; draw "
					+ "last=%.3f ms average=%.3f ms max=%.3f ms%n",
					instanceRenderWindowCalls, instanceRenderWindowInstances,
					instanceRenderLastNanos / 1_000_000.0, averageMillis,
					instancePreparationLastNanos / 1_000_000.0,
					instancePreparationWindowNanos
							/ (double) instanceRenderWindowCalls / 1_000_000.0,
					instancePreparationMaxNanos / 1_000_000.0,
					instanceBufferUploadLastNanos / 1_000_000.0,
					bufferUploadAverageMillis,
					instanceBufferUploadMaxNanos / 1_000_000.0,
					instanceDrawLastNanos / 1_000_000.0,
					instanceDrawWindowNanos
							/ (double) instanceRenderWindowCalls / 1_000_000.0,
					instanceDrawMaxNanos / 1_000_000.0);
			instanceRenderWindowStartedNanos = System.nanoTime();
			instanceRenderWindowNanos = 0L;
			instanceRenderWindowCalls = 0L;
			instanceRenderWindowInstances = 0L;
			instanceBufferUploadWindowNanos = 0L;
			instanceBufferUploadWindowCalls = 0L;
			instanceBufferUploadMaxNanos = 0L;
			instancePreparationWindowNanos = 0L;
			instancePreparationMaxNanos = 0L;
			instanceDrawWindowNanos = 0L;
			instanceDrawMaxNanos = 0L;
		}
	}

	private FloatBuffer preparedInstanceData(Scene scene, ObjFileModel model) {
		return scene == null || scene.isInspectionMode() || model == null
				? null : scene.getRenderInstanceData(model);
	}

	private static void put(FloatBuffer data, org.joml.Vector4f value) {
		data.put(value.x).put(value.y).put(value.z).put(value.w);
	}

	private int createInstanceBuffer(ObjFileModel model) {
		int bufferId = GL15.glGenBuffers();
		GL30.glBindVertexArray(model.getId());
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
		for (int column = 0; column < 4; column++) {
			int location = 3 + column;
			GL20.glEnableVertexAttribArray(location);
			GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false,
					INSTANCE_STRIDE_BYTES, (long) column * 4 * Float.BYTES);
			GL33.glVertexAttribDivisor(location, 1);
		}
		instanceAttribute(7, 16L * Float.BYTES);
		instanceAttribute(8, 20L * Float.BYTES);
		instanceAttribute(9, 24L * Float.BYTES);
		GL20.glEnableVertexAttribArray(10);
		GL20.glVertexAttribPointer(10, 1, GL11.GL_FLOAT, false, INSTANCE_STRIDE_BYTES,
				27L * Float.BYTES);
		GL33.glVertexAttribDivisor(10, 1);
		GL20.glEnableVertexAttribArray(11);
		GL20.glVertexAttribPointer(11, 1, GL11.GL_FLOAT, false, INSTANCE_STRIDE_BYTES,
				29L * Float.BYTES);
		GL33.glVertexAttribDivisor(11, 1);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);
		return bufferId;
	}

	private static void instanceAttribute(int location, long offset) {
		GL20.glEnableVertexAttribArray(location);
		GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false,
				INSTANCE_STRIDE_BYTES, offset);
		GL33.glVertexAttribDivisor(location, 1);
	}

	@Override
	public void bind(ObjFileModel model) {
		shader.setUniform("material", model.getMaterial());
		GL30.glBindVertexArray(model.getId());
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);
		GL20.glEnableVertexAttribArray(2);

		if (model.getMaterial().isDisableCulling())
			Renderer.disableCulling();
		else
			Renderer.enableCulling();
			
		
		GL20.glActiveTexture(GL13.GL_TEXTURE0);
		if (model.getTexture() != null)
			GL30.glBindTexture(GL11.GL_TEXTURE_2D, model.getTexture().getId());
	}

	@Override
	public void unbind() {
		GL20.glDisableVertexAttribArray(0);
		GL20.glDisableVertexAttribArray(1);
		GL20.glDisableVertexAttribArray(2);
		GL30.glBindVertexArray(0);
	}

	@Override
	public void cleanup() {
		for (int bufferId : instanceBuffers.values()) {
			GL15.glDeleteBuffers(bufferId);
		}
		instanceBuffers.clear();
		shader.cleanup();
	}

}
