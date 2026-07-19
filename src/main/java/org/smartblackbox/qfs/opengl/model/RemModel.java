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
package org.smartblackbox.qfs.opengl.model;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2d;
import org.joml.Vector3i;
import org.smartblackbox.qfs.opengl.model.entity.Node;
import org.smartblackbox.qfs.opengl.model.entity.Occupancy;
import org.smartblackbox.qfs.opengl.model.lights.Light;
import org.smartblackbox.qfs.settings.Project;

@Getter
@Setter
public class RemModel {

    public enum TranslationMode {
        normal,
        rain,
        alpha,
        intensity,
        light,
        occupancy,
    }

    ;

    public enum EditMode {
        none,
        swapNode,
        swapNodesZ,
        drawLine,
        drawWall,
        eraseNode,
        eraseWall,
    }

    public enum NodeSelectionMode {
        none,
        occupancy,
        ignitionNode,
        detectorNode,
    }

    private String customStatus = "";

    private boolean isSimulating;

    private boolean isLoadingReady = false;

    private boolean isWallVisible = false;
    private boolean isChanged     = false;

    private TranslationMode translationMode = TranslationMode.normal;
    private EditMode        editMode        = EditMode.none;

    private double   progress;
    private Vector2d mousePos              = new Vector2d();
    private Node     currentMouseNode;
    private Vector3i currentMouseNodeIndex = new Vector3i();

    private NodeSelectionMode nodeSelectionMode = NodeSelectionMode.none;
    private Occupancy         selectedOccupancy;
    private Light             selectedLight;

    public String getBtnSimulatingLabel() {
        return isSimulating() ? "Stop" : "Run";
    }

    public void startSimulation() {
        isSimulating = true;
    }

    public void stopSimulation() {
        isSimulating = false;
    }

    public void toggleSimulation() {
        isSimulating = !isSimulating;
    }

    public void setWallVisible(boolean isWallVisible) {
        if (this.isWallVisible != isWallVisible)
            isChanged = true;
        this.isWallVisible = isWallVisible;
    }

    public void clearWalls() {
        Project.getInstance().scene.clearAllWalls();
        isChanged = true;
    }


}
