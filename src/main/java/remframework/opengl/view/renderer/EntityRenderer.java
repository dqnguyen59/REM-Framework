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

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import remframework.helpers.Constants;
import remframework.opengl.model.Camera;
import remframework.opengl.model.ObjFileModel;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.lights.DirectionalLight;
import remframework.opengl.model.lights.Light;
import remframework.settings.Project;
import remframework.helpers.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityRenderer implements IRenderer {

	Project project = Project.getInstance();
	
	Shader shader;
	private Map<ObjFileModel, List<Entity>> entities = new  HashMap<>();
	private boolean inspectionMode;

	public void setInspectionMode(boolean inspectionMode) {
		this.inspectionMode = inspectionMode;
	}

	private int numLights = Constants.INITIAL_NUM_LIGHTS;
	private String vertexShader;
	private String fragmentShader;
	
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
		shader.bind();
		shader.setUniform("textureSampler", 0);
		shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
		shader.setUniform("viewMatrix", camera.getViewMatrix());
		// Inspector geometry is diagnostic and must not fade as the camera moves through
		// the normal depth-visibility field. The shader and ordinary render path remain unchanged.
		shader.setUniform("depthVisibility", inspectionMode
				? 0.0f : project.settings.getDepthVisibilityFactor());

		if (!inspectionMode) renderLights(scene.getDirectionalLight(), project.getLights(), shader);
		if (inspectionMode) GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		for (ObjFileModel model : entities.keySet()) {
			if (model != null) {
				bind(model);
				
				List<Entity> entityList = entities.get(model);
				shader.setUniform("ignoreLightIndex", -1);

				for (Entity entity : entityList) {
					boolean effectiveVisible = inspectionMode
							? entity.isInspectionVisible() : entity.isVisible();
					if (!effectiveVisible) continue;
					if (entity.isOverrideModelMaterial()) {
						shader.setUniform("material", entity.getMaterial());
					}
					shader.setUniform("transformationMatrix", entity.getTransformMatrix4f());
					GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
				}
			}
		}
		if (inspectionMode) GL11.glEnable(GL11.GL_DEPTH_TEST);

		unbind();

		shader.unbind();
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
		shader.cleanup();
	}

	public Map<ObjFileModel, List<Entity>> getEntities() {
		return entities;
	}

}
