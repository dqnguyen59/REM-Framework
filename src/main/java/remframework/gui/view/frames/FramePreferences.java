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
import remframework.opengl.model.RainModel;
import remframework.settings.Project;
import remframework.lattice.LatticeDescriptor;
import remframework.lattice.LatticeManager;

import java.util.List;

public class FramePreferences extends AbstractFrame {

    private final Project   project   = Project.getInstance();
	private final RainModel rainModel = project.rainModel;
	private final LatticeManager latticeManager = LatticeManager.getInstance();
	
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

        int width = 470;
        int height = 350;
        createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 120, width, height + (appSettings.isUseFixedNodes() ? 0 : 120));
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {

            float leftCol = 0.6f;
            float rightCol = 0.4f;
            List<LatticeDescriptor> descriptors = latticeManager.descriptors();
            String[] labels = descriptors.stream().map(LatticeDescriptor::uiLabel).toArray(String[]::new);
            LatticeDescriptor selected = latticeManager.selected();
            String chosen = nk_label_combo_options(ctx, stack, " Lattice Model:", labels,
                    selected.uiLabel(), 0.35f, 0.65f);
            if (!chosen.equals(selected.uiLabel())) {
                LatticeDescriptor candidate = descriptors.get(java.util.Arrays.asList(labels).indexOf(chosen));
                boolean wasSimulating = project.getRemModel().isSimulating();
                project.getRemModel().setSimulating(false);
                if (latticeManager.select(candidate.identifier())) {
                    project.getRemModel().setCustomStatus("Loading " + candidate.displayName());
                    appSettings.requestLatticeRebuild();
                    System.out.println("Runtime Lattice Model change: " + candidate.uiLabel()
                            + (wasSimulating ? " (simulation paused)" : ""));
                } else {
                    nuklearModel.showErrorDialog("Invalid Lattice Model",
                            latticeManager.lastWarning() + "\n\n"
                                    + candidate.messages().stream()
                                    .map(m -> m.severity() + ": " + m.message())
                                    .reduce((a, b) -> a + "\n" + b).orElse(""));
                    appSettings.requestLatticeRebuild();
                }
            }
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightEdit, 1);
            if (nk_button_label(ctx, "Refresh Lattice Models")) {
                String before = latticeManager.selected().identifier();
                latticeManager.refresh();
                if (!before.equals(latticeManager.selected().identifier())) {
                    project.getRemModel().setSimulating(false);
                    appSettings.requestLatticeRebuild();
                    nuklearModel.showErrorDialog("Lattice Model Refresh",
                            latticeManager.lastWarning());
                }
                project.getRemModel().setCustomStatus("Lattice model list refreshed");
            }
            if (!latticeManager.lastWarning().isBlank()) {
                Nuklear.nk_layout_row_dynamic(ctx, rowHeightEdit * 2, 1);
                Nuklear.nk_label_wrap(ctx, "Warning: " + latticeManager.lastWarning());
            }
            nk_spacer(ctx, spacer1, 1);

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
