/*
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
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
package remframework.gui.view.frames;

import org.joml.Vector3i;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.settings.Project;
import remframework.settings.Settings;

public class FrameDimension extends AbstractFrame {

	private Project  project  = Project.getInstance();
	private Settings settings = project.settings;

	private StringBuffer dimensionx = new StringBuffer();
	private StringBuffer dimensionY = new StringBuffer();
	private StringBuffer dimensionZ = new StringBuffer();

	private int width = 400;
	private int height = 280;
	private double leftCol = 0.3;
	private double rightCol = 0.7;

	private Vector3i dimension = new Vector3i();

	public FrameDimension(NuklearModel frames) {
		super(frames);
		dimension.set(project.getDimensionX(), project.getDimensionY(), project.getDimensionZ());
	}

	@Override
	public String getTitle() {
		return "Quantum Field Simulator";
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);

		createLayoutCentered(ctx, width, height);
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
		dimension.x = nk_label_edit(ctx, stack, " Dimension.X:", dimensionx, dimension.x, leftCol, rightCol);
		dimension.y = nk_label_edit(ctx, stack, " Dimension.Y:", dimensionY, dimension.y, leftCol, rightCol);
		dimension.z = nk_label_edit(ctx, stack, " Dimension.Z:", dimensionZ, dimension.z, leftCol, rightCol);

		if (remframework.opengl.model.Lattice.getInstance().isMechanicsOnly())
                nk_label_value(ctx, "Diamond size", "Even values; memory limited", leftCol, rightCol);
            nk_label_value(ctx, " #Nodes", "" + ((long)dimension.x * dimension.y * dimension.z), leftCol, rightCol);

		if (remframework.opengl.model.Lattice.getInstance().isMechanicsOnly()) {
                var estimate=remframework.lattice.DiamondMemoryBudget.estimate(dimension,Long.MAX_VALUE);
                nk_label_value(ctx,"Peak estimate",String.format("%.0f MiB",estimate.requiredBytes()/1048576.0),leftCol,rightCol);
                nk_label_value(ctx,"Memory check","After old lattice is released",leftCol,rightCol);
            }
            nk_spacer(ctx, spacer1 , 1);
		Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 3);
		Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_CENTERED);
		if (nk_button_label(ctx, "Update")) {
			if (remframework.opengl.model.Lattice.getInstance().isMechanicsOnly()) {
                    var estimate=remframework.lattice.DiamondMemoryBudget.estimate(dimension,Long.MAX_VALUE);
                    if (estimate.allowed()) update();
                    else {
                        remframework.opengl.model.Lattice.getInstance().reportDiamondSizeFailure(estimate);
                        nuklearModel.showErrorDialog("Dimension Error",estimate.error());
                    }
                }
                else if (dimension.x < 3|| dimension.y < 3 || dimension.z < 1) {
					nuklearModel.showErrorDialog("Dimension Error", "Invalid dimension range.\n\nChoose a valid dimension: \nx >= 3\ny >= 3\nz >= 1");
			}
				else
					update();
		}
		nk_spacer(ctx, spacer1 , 1);
        }
	}

	private void update() {
		project.setDimension(dimension);
		settings.setVisibleIndexZ(project.getDimensionZ() / 2);
        project.reset();
		close();
	}

}