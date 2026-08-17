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

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2d;
import org.joml.Vector3i;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.lights.Light;
import remframework.settings.Project;

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
    /** One-shot request consumed by the existing Simulator update loop. */
    private volatile boolean singleStepRequested;
    /** True only while that existing update loop is executing a paused step. */
    private volatile boolean singleStepInProgress;

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
        return isSimulating() ? "Pause" : "Run";
    }

    public void startSimulation() {
        singleStepRequested = false;
        isSimulating = true;
    }

    public void stopSimulation() {
        isSimulating = false;
    }

    public void toggleSimulation() {
        if (!isSimulating) singleStepRequested = false;
        isSimulating = !isSimulating;
    }

    public void requestSingleStep() {
        if (!isSimulating && !singleStepInProgress) singleStepRequested = true;
    }

    public boolean consumeSingleStepRequest() {
        if (isSimulating || !singleStepRequested) return false;
        singleStepRequested = false;
        singleStepInProgress = true;
        return true;
    }

    public void finishSingleStep() {
        singleStepInProgress = false;
    }

    public boolean isSimulationUpdateActive() {
        return isSimulating || singleStepInProgress;
    }

    public boolean isSingleStepAvailable() {
        return !isSimulating && !singleStepInProgress;
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
