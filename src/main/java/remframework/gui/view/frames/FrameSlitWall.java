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

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.opengl.model.RemModel;
import remframework.settings.Project;
import remframework.settings.SlitWallSettings;

public class FrameSlitWall extends AbstractFrame {

	private Project  project  = Project.getInstance();
	private RemModel REMModel = project.getRemModel();

	private int width = 300;
	private int height = 310;
	private double leftCol = 0.50;
	private double rightCol = 0.50;

	private SlitWallSettings slitWall = Project.getInstance().slitWall;

	public FrameSlitWall(NuklearModel frames) {
		super(frames);
	}

	@Override
	public String getTitle() {
		return "Slit Wall";
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);
		
		createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 558, width, height);
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {

			slitWall.setActive(nk_check_label(ctx, "Active", slitWall.isActive(), leftCol, rightCol));

			Nuklear.nk_layout_row_dynamic(ctx, spacer1 , 1);
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			slitWall.setNumSlits(Nuklear.nk_propertyi(ctx, "Number of slits", 1, slitWall.getNumSlits(), 10, 1, 1));
			Nuklear.nk_layout_row_dynamic(ctx, spacer1 , 1);
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			slitWall.setSlitWidth(Nuklear.nk_propertyi(ctx, "Slit width", 1, slitWall.getSlitWidth(), 100, 1, 1));
			Nuklear.nk_layout_row_dynamic(ctx, spacer1 , 1);
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			slitWall.setSlitHeight(Nuklear.nk_propertyi(ctx, "Slit height", 1, slitWall.getSlitHeight(), 100, 1, 1));
			Nuklear.nk_layout_row_dynamic(ctx, spacer1 , 1);
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			slitWall.setSlitDistance(Nuklear.nk_propertyi(ctx, "Slit distance", 1, slitWall.getSlitDistance(), 100, 1, 1));
			Nuklear.nk_layout_row_dynamic(ctx, spacer1 , 1);
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			slitWall.setPosition(Nuklear.nk_propertyf(ctx, "Wall position", 0, slitWall.getPosition(), 1f, 0.001f, 0.001f));
			
			Nuklear.nk_layout_row_dynamic(ctx, spacer1 , 1);
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 2);
			if (nk_button_label(ctx, "Clear All Walls")) {
				REMModel.clearWalls();
			}
			if (nk_button_label(ctx, "Add")) {
				slitWall.setUpdated(true);
			}
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_LEFT);
		}
	}

}