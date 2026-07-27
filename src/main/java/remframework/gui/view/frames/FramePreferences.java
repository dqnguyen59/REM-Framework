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
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.opengl.model.RainModel;
import remframework.settings.Project;

public class FramePreferences extends AbstractFrame {

    private final Project   project   = Project.getInstance();
	private final RainModel rainModel = project.rainModel;
	
	private final StringBuffer bufNumThreads   = new StringBuffer();
	private final StringBuffer bufSwapInterval         = new StringBuffer();
	private final StringBuffer bufForce                = new StringBuffer();
	private final StringBuffer bufAbsorptionFixedNodes = new StringBuffer();

	public FramePreferences(NuklearModel frames) {
		super(frames);
	}

	@Override
	public String getTitle() {
		return "Preferences";
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);

        int width = 300;
        int height = 240;
        createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 120, width, height + (appSettings.isUseFixedNodes() ? 0 : 120));
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {

            float leftCol = 0.6f;
            float rightCol = 0.4f;
            appSettings.setNumThreads(nk_label_edit(ctx, stack, " CPU Threads:", bufNumThreads, appSettings.getNumThreads(), leftCol,
                                                    rightCol));
			nk_spacer(ctx, spacer1, 1);
			
			appSettings.setUseFixedNodes(nk_check_label(ctx, " Use Fixed Nodes:", appSettings.isUseFixedNodes(), leftCol, rightCol));
			nk_spacer(ctx, spacer1, 1);
			
			if (!appSettings.isUseFixedNodes()) {
				appSettings.setAbsorption(nk_label_edit(ctx, stack, " Absorption:",
                                                        bufAbsorptionFixedNodes, appSettings.getAbsorption(), leftCol, rightCol));
				appSettings.setAbsorption(nk_slider(ctx, 0.0, appSettings.getAbsorption(), 1.0, 0.01));
				nk_spacer(ctx, spacer1, 1);

				appSettings.setUseLoop(nk_check_label(ctx, " Use Loop:", appSettings.isUseLoop(), leftCol, rightCol));
				nk_spacer(ctx, spacer1, 1);
			}
			
			project.setGlSwapInterval(nk_label_edit(ctx, stack, " GLSwap Interval:", bufSwapInterval, project.getGlSwapInterval(), leftCol,
                                                    rightCol));
			nk_spacer(ctx, spacer1, 1);
			
			rainModel.setForce(nk_label_edit(ctx, stack, " Force:", bufForce, rainModel.getForce(), leftCol, rightCol));
			rainModel.setForce(nk_slider(ctx, -2500, rainModel.getForce(), 2500, 0.1));
			nk_spacer(ctx, spacer1, 1);

			Nuklear.nk_layout_row_end(ctx);
		}
	}

}