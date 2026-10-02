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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Renderer {

	/** Immutable membership snapshot consumed by the offscreen render context. */
	public record RenderState(Map<remframework.opengl.model.ObjFileModel, List<Entity>> entities,
			List<Entity> probeEntities, List<Entity> gradientArrowEntities,
			List<Terrain> terrains) {
		public RenderState {
			Map<remframework.opengl.model.ObjFileModel, List<Entity>> copy = new IdentityHashMap<>();
			for (Map.Entry<remframework.opengl.model.ObjFileModel, List<Entity>> entry : entities.entrySet()) {
				copy.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			entities = java.util.Collections.unmodifiableMap(copy);
			probeEntities = List.copyOf(probeEntities);
			gradientArrowEntities = List.copyOf(gradientArrowEntities);
			terrains = List.copyOf(terrains);
		}
	}
	
	private EntityRenderer nodeRenderer;
	private TerrainRenderer terrainRenderer;
	private final List<Entity> probeEntities = new ArrayList<>();
	private final List<Entity> gradientArrowEntities = new ArrayList<>();
	
	private static boolean isCulling = false;
	private final List<Entity> inspectionEntities = new ArrayList<>();
	private long stateVersion;
	
	public void init() throws Exception {
		System.out.println("Renderer.init()");
		terrainRenderer = new TerrainRenderer();
		nodeRenderer = new EntityRenderer();
		terrainRenderer.init();
		nodeRenderer.init();
	}
	
	public void render(Camera camera, Scene scene) {
		if (!scene.isInspectionMode()) terrainRenderer.render(camera, scene);
		nodeRenderer.render(camera, scene, probeEntities, gradientArrowEntities);
	}

	public synchronized void processEntity(Entity entity) {
		//if (!entity.isVisible()) return;
		
		List<Entity> nodeList = nodeRenderer.getEntities().get(entity.getModel());
		if (nodeList != null)
			nodeList.add(entity);
		else {
			List<Entity> newNodeList = new ArrayList<>();
			newNodeList.add(entity);
			nodeRenderer.getEntities().put(entity.getModel(), newNodeList);		
		}
		stateVersion++;
	}

	public synchronized void processTerrain(Terrain terrain) {
		terrainRenderer.getTerrains().add(terrain);
		stateVersion++;
	}

	public synchronized long stateVersion() {
		return stateVersion;
	}

	public synchronized RenderState snapshot() {
		return new RenderState(nodeRenderer.getEntities(), probeEntities,
				gradientArrowEntities, terrainRenderer.getTerrains());
	}

	/** Applies only membership state; entity transforms remain shared and double-buffered. */
	public void apply(RenderState state) {
		nodeRenderer.getEntities().clear();
		for (Map.Entry<remframework.opengl.model.ObjFileModel, List<Entity>> entry
				: state.entities().entrySet()) {
			nodeRenderer.getEntities().put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		probeEntities.clear();
		probeEntities.addAll(state.probeEntities());
		gradientArrowEntities.clear();
		gradientArrowEntities.addAll(state.gradientArrowEntities());
		terrainRenderer.getTerrains().clear();
		terrainRenderer.getTerrains().addAll(state.terrains());
		nodeRenderer.invalidateVisibleEntityCache();
	}
	
	public synchronized void cleanup() {
		System.out.println("Renderer.cleanup()");
		nodeRenderer.cleanup();
		terrainRenderer.cleanup();
	}

	public synchronized void cleanupNodes() {
		System.out.println("Renderer.cleanupNodes()");
		nodeRenderer.invalidateVisibleEntityCache();
		nodeRenderer.getEntities().clear();
		probeEntities.clear();
		gradientArrowEntities.clear();
		stateVersion++;
	}

	/** Synchronizes passive-probe arrows in the separate dynamic overlay batch. */
	public synchronized void syncGradientArrowEntities(List<Entity> entities) {
		if (syncOverlayEntities(gradientArrowEntities, entities)) {
			stateVersion++;
		}
	}

	/** Synchronizes passive probe markers in the separate dynamic overlay batch. */
	public synchronized void syncProbeEntities(List<Entity> entities) {
		if (syncOverlayEntities(probeEntities, entities)) {
			stateVersion++;
		}
	}

	/** Avoids rewriting stable overlay membership on every rendered frame. */
	private static boolean syncOverlayEntities(List<Entity> current, List<Entity> requested) {
		if (current.size() == requested.size()) {
			boolean unchanged = true;
			for (int index = 0; index < current.size(); index++) {
				if (current.get(index) != requested.get(index)) {
					unchanged = false;
					break;
				}
			}
			if (unchanged) return false;
		}
		current.clear();
		current.addAll(requested);
		return true;
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
