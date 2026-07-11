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
 * File created on 09/07/2026
 */
package org.smartblackbox.qfs.opengl.model;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.smartblackbox.qfs.Constants;
import org.smartblackbox.qfs.opengl.model.entity.Entity;
import org.smartblackbox.qfs.opengl.model.entity.EntityType;
import org.smartblackbox.qfs.opengl.model.entity.Node;
import org.smartblackbox.qfs.opengl.utils.Neighbor;
import org.smartblackbox.qfs.opengl.utils.OBJFormatLoader;
import org.smartblackbox.qfs.settings.AppSettings;
import org.smartblackbox.qfs.settings.Project;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Lattice {

	private final AppSettings appSettings = AppSettings.getInstance();
	private final Project  project  = Project.getInstance();
	private final RemModel remModel = project.getRemModel();

    private Entity baseEntity;
	private ObjFileModel modelSphere;

	private Node[][][]   nodeMatrix;
	private List<Entity> entities;

	private Object[] nodeArray;
	private Object[] entityArray;

	private static Lattice latticeInstance;

	public static Lattice getInstance() {
		if (latticeInstance == null) {
			latticeInstance = new Lattice();
		}
		return latticeInstance;
	}

	public Lattice() {
		entities = new ArrayList<>();
	}

	public Node getNode(int x, int y, int z) {
		return nodeMatrix[z][y][x];
	}

	public Node getNode(Vector3i node) {
		return getNode(node.x, node.y, node.z);
	}

	private Entity addEntity(Entity entity) {
		entities.add(entity);
		return entity;
	}

	public Entity createEntity(
			Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale) {
		return addEntity(new Entity(parent, model, position, rotation, scale, EntityType.ENTITY));
	}

	public Entity createEntity(Vector3d position, Vector3d rotation, double scale) {
		if (baseEntity == null) {
			System.out.println("Warning, baseEntity is null!");
			return null;
		}
		if (modelSphere == null) {
			System.out.println("Warning, modelSphere is null!");
			return null;
		}
		return addEntity(new Entity(baseEntity, modelSphere, position, rotation, scale, EntityType.ENTITY));
	}

	public Node createNode(Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale) {
		return (Node) addEntity(new Node(parent, model, position, rotation, scale));
	}

	public void deleteEntity(Node node) {
		if (node != null) {
			entities.remove(node);
		}
	}

	public List<Entity> getNodes() {
		List<Entity> nodes = new ArrayList<>();
		for (Entity entity : getEntities()) {
			if (Node.class.isAssignableFrom(entity.getClass())) {
				nodes.add(entity);
			}
		}
		return nodes;
	}

	public Node getNodeByIndex(Vector3i nodeIndex) {
		int index = nodeIndex.z * project.getDimensionY() * project.getDimensionX() +
		            nodeIndex.y * project.getDimensionX() + nodeIndex.x;
		if (index >= 0 && index < getNodeArray().length)
			return (Node) getNodeArray()[index];
		else
			return null;

	}

	public Entity build() {
		Vector3i dimension = new Vector3i();
		project.getDimension(dimension);

		int sizeI = dimension.x;
		int sizeJ = dimension.y;
		int sizeK = dimension.z;

		Performance.numNodes = sizeI * sizeJ * sizeK;
		Performance.numNodesX = sizeI;
		Performance.numNodesY = sizeJ;
		Performance.numNodesZ = sizeK;

		modelSphere = new OBJFormatLoader().loadOBJModel(Constants.MODEL_NODE);

		baseEntity = createEntity(null, null, new Vector3d(), new Vector3d(), 1.0f);
		baseEntity.setPosition(0, 0, 0);
		baseEntity.setRotation(project.baseRotation.x, project.baseRotation.y, project.baseRotation.z);

		float step = Constants.EP_DEFAULT_DISTANCE;
		
		// Draw xyz arrows on entity baseField.
		//drawArrows(baseField, Constants.EP_DEFAULT_DISTANCE, modelSphere, sizeI, sizeJ, sizeK);

		nodeMatrix = new Node[sizeK][sizeJ][sizeI];

		int NumNodes = sizeI * sizeJ * sizeK;
		
		remModel.setLoadingReady(false);
		remModel.setProgress(0.0);
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				int countNode = 0;
				for (int k = 0; k < sizeK; k++) {
					for (int j = 0; j < sizeJ; j++) {
						for (int i = 0; i < sizeI; i++) {
							Node n = createNode(baseEntity, modelSphere,
							                    new Vector3d((i - sizeI / 2.0) * step, (j - sizeJ / 2.0) * step, (k - sizeK / 2.0) * step),
							                    new Vector3d(0, 0, 0), Constants.EP_DEFAULT_DISTANCE);
							n.setIndex(i, j, k);
							
							n.setOverrideModelMaterial(true);
							n.getMaterial().setDiffuseColor(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
							n.getMaterial().setAmbientColor(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
							nodeMatrix[k][j][i] = n;
							
							boolean isEdgeX = (sizeI > 1 && (i == 0 || i == sizeI - 1)); 
							boolean isEdgeY = (sizeJ > 1 && (j == 0 || j == sizeJ - 1)); 
							boolean isEdgeZ = (sizeK > 1 && (k == 0 || k == sizeK - 1)); 

							if (isEdgeX || isEdgeY || isEdgeZ) n.setType(EntityType.FIXED);
//							n.setFixed(isEdgeX || isEdgeY || isEdgeZ);
							n.setXFixed(isEdgeX);
							n.setYFixed(isEdgeY);
							n.setZFixed(isEdgeZ);

							// experimentalMode, when only the edges are fixed. This creates a cool effect.
//							if (!(
//								(i > 0 && i < sizeI - 1) && (j > 0 && j < sizeJ - 1)
//								 || (i > 0 && i < sizeI - 1) && (k > 0 && k < sizeK - 1)
//								 || (j > 0 && j < sizeJ - 1) && (k > 0 && k < sizeK - 1)
//								)) {
//								n.setFixed(true);
//							}


							countNode++;
							remModel.setProgress((double) countNode / (double) NumNodes * 100);
						}
					}
				}

				// Connect all neighbors in fieldMatrix.
				for (int k = 0; k < sizeK; k++) {
					for (int j = 0; j < sizeJ; j++) {
						for (int i = 0; i < sizeI; i++) {
							if (i > 0)
								nodeMatrix[k][j][i].setNeighbor(Neighbor.LEFT, nodeMatrix[k][j][i - 1]);
							else if (appSettings.isUseLoop())
								nodeMatrix[k][j][i].setNeighbor(Neighbor.LEFT, nodeMatrix[k][j][sizeI - 1]);
							if (i < sizeI - 1)
								nodeMatrix[k][j][i].setNeighbor(Neighbor.RIGHT, nodeMatrix[k][j][i + 1]);
							else if (appSettings.isUseLoop())
								nodeMatrix[k][j][i].setNeighbor(Neighbor.RIGHT, nodeMatrix[k][j][0]);

							if (j > 0)
								nodeMatrix[k][j][i].setNeighbor(Neighbor.BOTTOM, nodeMatrix[k][j - 1][i]);
							else if (appSettings.isUseLoop())
								nodeMatrix[k][j][i].setNeighbor(Neighbor.BOTTOM, nodeMatrix[k][sizeJ - 1][i]);
							if (j < sizeJ - 1)
								nodeMatrix[k][j][i].setNeighbor(Neighbor.TOP, nodeMatrix[k][j + 1][i]);
							else if (appSettings.isUseLoop())
								nodeMatrix[k][j][i].setNeighbor(Neighbor.TOP, nodeMatrix[k][0][i]);

							if (k > 0)
								nodeMatrix[k][j][i].setNeighbor(Neighbor.BACK, nodeMatrix[k - 1][j][i]);
							else if (appSettings.isUseLoop())
								nodeMatrix[k][j][i].setNeighbor(Neighbor.BACK, nodeMatrix[sizeK - 1][j][i]);
							if (k < sizeK - 1)
								nodeMatrix[k][j][i].setNeighbor(Neighbor.FRONT, nodeMatrix[k + 1][j][i]);
							else if (appSettings.isUseLoop())
								nodeMatrix[k][j][i].setNeighbor(Neighbor.FRONT, nodeMatrix[0][j][i]);
						}
					}
				}
				remModel.setLoadingReady(true);
			}
		}).start();

		return baseEntity;
	}
	
	public void update() {
		setNodeArray(getNodes().toArray());
		setEntityArray(entities.toArray());
	}

}
