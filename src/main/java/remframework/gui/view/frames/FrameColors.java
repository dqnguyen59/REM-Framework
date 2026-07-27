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
package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.settings.Project;
import remframework.settings.Settings;

public class FrameColors extends AbstractFrame {

	private int width = 300;
	private int height = 174;

	private Project  project  = Project.getInstance();
	private Settings settings = project.settings;
	private double   leftCol  = 0.5;
	private double rightCol = 0.5;

	public FrameColors(NuklearModel frames) {
		super(frames);
	}

	@Override
	public String getTitle() {
		return "QFS Colors";
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);
		
		createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 120, width, height);
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			settings.defaultNodeColor = nk_combo_color_picker(ctx, stack, " Default Node Color:", settings.defaultNodeColor, leftCol, rightCol);
			settings.fixedColor = nk_combo_color_picker(ctx, stack, " Fixed Color:", settings.fixedColor, leftCol, rightCol);
			settings.wallColor = nk_combo_color_picker(ctx, stack, " Wall Color:", settings.wallColor, leftCol, rightCol);
			settings.selectedColor = nk_combo_color_picker(ctx, stack, " Selected Color:", settings.selectedColor, leftCol, rightCol);
			settings.hiLightColor = nk_combo_color_picker(ctx, stack, " HiLight Color:", settings.hiLightColor, leftCol, rightCol);
		}
	}

}