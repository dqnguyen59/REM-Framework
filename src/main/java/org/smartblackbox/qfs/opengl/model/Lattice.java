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
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.smartblackbox.qfs.Constants;
import org.smartblackbox.qfs.opengl.model.entity.Entity;
import org.smartblackbox.qfs.opengl.model.entity.Node;
import org.smartblackbox.qfs.opengl.utils.Neighbor;
import org.smartblackbox.qfs.opengl.utils.OBJFormatLoader;
import org.smartblackbox.qfs.settings.AppSettings;
import org.smartblackbox.qfs.settings.Project;

public class Lattice {

	private final AppSettings appSettings = AppSettings.getInstance();
	private final Project  project  = Project.getInstance();
	private final RemModel REMModel = project.getQfsModel();
	private final Scene    scene;

    @Getter
    private Entity baseField;

	private Node[][][] nodeMatrix;

	private final OBJFormatLoader loader;

	public Lattice(Scene scene) {
		loader = new OBJFormatLoader();
		this.scene = scene;  
	}

	public void drawArrows(Entity baseField, float step, ObjFileModel modelSphere, int sizeI, int sizeJ, int sizeK) {
		ObjFileModel modelArrow = loader.loadOBJModel(Constants.MODEL_ARROW);
		float scale = 0.5f;
		
		Entity arrowC = scene.createEntity(baseField, modelSphere,
				new Vector3d(-(sizeI + 1.0) / 2 * step, -(sizeJ / 2.0 + 1) * step, -(sizeK / 2.0 + 1) * step),
				new Vector3d(0, 0, 0), scale);
		arrowC.setOverrideModelMaterial(true);
		arrowC.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
		arrowC.getMaterial().setAmbientColor(new Vector4f(10.0f, 10.0f, 10.0f, 1.0f));
		arrowC.getMaterial().setShininess(0.0f);

		Entity arrowX = scene.createEntity(baseField, modelArrow,
				new Vector3d(-(sizeI + 1.0) / 2 * step, -(sizeJ / 2.0 + 1) * step, -(sizeK / 2.0 + 1) * step),
				new Vector3d(0, 90, 0), scale);
		arrowX.setOverrideModelMaterial(true);
		arrowX.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
		arrowX.getMaterial().setAmbientColor(new Vector4f(10.0f, 4.5f, 4.5f, 1.0f));
		arrowX.getMaterial().setShininess(0.0f);
		
		Entity arrowY = scene.createEntity(baseField, modelArrow, 
				new Vector3d(-(sizeI + 1.0) / 2 * step, -(sizeJ / 2.0 + 1) * step, -(sizeK / 2.0 + 1) * step),
				new Vector3d(0, 0, 0), scale);
		arrowY.setOverrideModelMaterial(true);
		arrowY.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
		arrowY.getMaterial().setAmbientColor(new Vector4f(4.5f, 4.5f, 10.0f, 1.0f));
		arrowY.getMaterial().setShininess(0.0f);
		
		Entity arrowZ = scene.createEntity(baseField, modelArrow,
				new Vector3d(-(sizeI + 1.0) / 2 * step, -(sizeJ / 2.0 + 1) * step, -(sizeK / 2.0 + 1) * step),
				new Vector3d(-90, 0, 0), scale);
		arrowZ.setOverrideModelMaterial(true);
		arrowZ.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
		arrowZ.getMaterial().setAmbientColor(new Vector4f(4.5f, 10.0f, 4.5f, 1.0f));
		arrowZ.getMaterial().setShininess(0.0f);
	}
	
	public void build(Vector3i dimension, Vector3d rotation) {
		int sizeI = dimension.x;
		int sizeJ = dimension.y;
		int sizeK = dimension.z;

		Performance.numNodes = sizeI * sizeJ * sizeK;
		Performance.numNodesX = sizeI;
		Performance.numNodesY = sizeJ;
		Performance.numNodesZ = sizeK;

		ObjFileModel modelSphere = loader.loadOBJModel(Constants.MODEL_NODE);

		baseField = new Entity(null, null, new Vector3d(), new Vector3d(), 1.0f);
		baseField.setPosition(0, 0, 0);
		baseField.setRotation(rotation.x, rotation.y, rotation.z);
		scene.addEntity(baseField);

		float step = Constants.EP_DEFAULT_DISTANCE;
		
		// Draw xyz arrows on entity baseField.
		//drawArrows(baseField, step, modelSphere, sizeI, sizeJ, sizeK);

		nodeMatrix = new Node[sizeK][sizeJ][sizeI];

		int NumNodes = sizeI * sizeJ * sizeK;
		
		REMModel.setLoadingReady(false);
		REMModel.setProgress(0.0);
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				int countNode = 0;
				for (int k = 0; k < sizeK; k++) {
					for (int j = 0; j < sizeJ; j++) {
						for (int i = 0; i < sizeI; i++) {
							Node n = scene.createNode(baseField, modelSphere,
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

							n.setFixed(isEdgeX || isEdgeY || isEdgeZ);
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
							REMModel.setProgress((double) countNode / (double) NumNodes * 100);
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
				REMModel.setLoadingReady(true);
			}
		}).start();
		
	}
	
	public void build() {
		Vector3i dimension = new Vector3i();
		project.getDimension(dimension);
		build(dimension, project.baseRotation);
	}

    public Node getNode(int x, int y, int z) {
		return nodeMatrix[z][y][x];
	}

	public Node getNode(Vector3i node) {
		return getNode(node.x, node.y, node.z);
	}
	
}
