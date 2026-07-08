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
package org.smartblackbox.qfs.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import org.smartblackbox.qfs.gui.model.NuklearModel;
import org.smartblackbox.qfs.opengl.model.REMModel;
import org.smartblackbox.qfs.opengl.model.REMModel.EditMode;
import org.smartblackbox.qfs.settings.QFSProject;

public class FrameEditWall extends AbstractFrame {

	private QFSProject qfsProject = QFSProject.getInstance();
	private REMModel   REMModel   = qfsProject.getQfsModel();

	private int width = 300;
	private int height = 312;

	public FrameEditWall(NuklearModel frames) {
		super(frames);
	}

	@Override
	public String getTitle() {
		return "Edit Wall";
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);

		createLayout(ctx, 0, nuklearModel.getMenuBarHeight(), width, height);
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			REMModel.setWallVisible(Nuklear.nk_check_label(ctx, "Show Wall", REMModel.isWallVisible()));

			Nuklear.nk_layout_row_static(ctx, rowHeight, 120, 1);
			if (Nuklear.nk_option_label(ctx, "None", REMModel.getEditMode() == EditMode.none)) {
				REMModel.setEditMode(EditMode.none);
			}

			if (Nuklear.nk_option_label(ctx, "Swap Node", REMModel.getEditMode() == EditMode.swapNode)) {
				REMModel.setEditMode(EditMode.swapNode);
			}

			if (Nuklear.nk_option_label(ctx, "Swap Nodes Z", REMModel.getEditMode() == EditMode.swapNodesZ)) {
				REMModel.setEditMode(EditMode.swapNodesZ);
			}

			if (Nuklear.nk_option_label(ctx, "Line", REMModel.getEditMode() == EditMode.drawLine)) {
				REMModel.setEditMode(EditMode.drawLine);
			}

			if (Nuklear.nk_option_label(ctx, "Wall", REMModel.getEditMode() == EditMode.drawWall)) {
				REMModel.setEditMode(EditMode.drawWall);
			}

			if (Nuklear.nk_option_label(ctx, "Erase Node", REMModel.getEditMode() == EditMode.eraseNode)) {
				REMModel.setEditMode(EditMode.eraseNode);
			}

			if (Nuklear.nk_option_label(ctx, "Erase Wall", REMModel.getEditMode() == EditMode.eraseWall)) {
				REMModel.setEditMode(EditMode.eraseWall);
			}

			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_LEFT);
			
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 4);
			Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_LEFT);
			if (nk_button_label(ctx, "Clear all")) {
				REMModel.clearWalls();
			}
			if (nk_button_label(ctx, "Close")) {
				close();
			}
		}
	}

}