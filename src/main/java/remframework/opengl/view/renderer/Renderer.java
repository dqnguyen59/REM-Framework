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
import lombok.Setter;
import org.lwjgl.opengl.GL11;
import remframework.opengl.model.Camera;
import remframework.opengl.model.Scene;
import remframework.opengl.model.Terrain;
import remframework.opengl.model.entity.Entity;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Renderer {
	
	private EntityRenderer nodeRenderer;
	private TerrainRenderer terrainRenderer;
	private final List<Entity> probeEntities = new ArrayList<>();
	private final List<Entity> gradientArrowEntities = new ArrayList<>();
	
	private static boolean isCulling = false;
	private final List<Entity> inspectionEntities = new ArrayList<>();
	
	public void init() throws Exception {
		System.out.println("Renderer.init()");
		terrainRenderer = new TerrainRenderer();
		nodeRenderer = new EntityRenderer();
		terrainRenderer.init();
		nodeRenderer.init();
	}
	
	public void render(Camera camera, Scene scene) {
		if (!scene.isInspectionMode()) terrainRenderer.render(camera, scene);
		nodeRenderer.render(camera, scene);
	}

	public void processEntity(Entity entity) {
		//if (!entity.isVisible()) return;
		
		List<Entity> nodeList = nodeRenderer.getEntities().get(entity.getModel());
		if (nodeList != null)
			nodeList.add(entity);
		else {
			List<Entity> newNodeList = new ArrayList<>();
			newNodeList.add(entity);
			nodeRenderer.getEntities().put(entity.getModel(), newNodeList);		
		}
	}

	public void processTerrain(Terrain terrain) {
		terrainRenderer.getTerrains().add(terrain);
	}
	
	public void cleanup() {
		System.out.println("Renderer.cleanup()");
		nodeRenderer.cleanup();
		terrainRenderer.cleanup();
	}

	public void cleanupNodes() {
		System.out.println("Renderer.cleanupNodes()");
		nodeRenderer.getEntities().clear();
		probeEntities.clear();
		gradientArrowEntities.clear();
	}

	/** Synchronizes cached passive-probe arrows without adding them to the lattice entity set. */
	public void syncGradientArrowEntities(List<Entity> entities) {
		for (Entity entity : gradientArrowEntities) {
			List<Entity> rendered = nodeRenderer.getEntities().get(entity.getModel());
			if (rendered != null) rendered.remove(entity);
		}
		gradientArrowEntities.clear();
		for (Entity entity : entities) {
			processEntity(entity);
			gradientArrowEntities.add(entity);
		}
	}

	/** Synchronizes passive probe markers without adding them to the lattice entity set. */
	public void syncProbeEntities(List<Entity> entities) {
		for (Entity entity : probeEntities) {
			List<Entity> rendered = nodeRenderer.getEntities().get(entity.getModel());
			if (rendered != null) rendered.remove(entity);
		}
		probeEntities.clear();
		for (Entity entity : entities) {
			processEntity(entity);
			probeEntities.add(entity);
		}
	}

	public static void enableCulling() {
		if (!isCulling) {
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glCullFace(GL11.GL_BACK);
			isCulling = true;
		}
	}

	public static void disableCulling() {
		if (isCulling) {
			GL11.glDisable(GL11.GL_CULL_FACE);
			isCulling = false;
		}
	}

}
