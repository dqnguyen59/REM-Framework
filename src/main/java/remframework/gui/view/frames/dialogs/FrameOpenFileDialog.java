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
package remframework.gui.view.frames.dialogs;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.helpers.Constants;
import remframework.gui.model.AbstractDialogModel.ConfirmState;
import remframework.gui.model.DialogFileModel;
import remframework.gui.model.NuklearModel;

public class FrameOpenFileDialog extends FrameDialog {

	private int width = 400;
	private int height = 370;

	private DialogFileModel dlgFileModel;

	public FrameOpenFileDialog(NuklearModel frames) {
		super(frames);
		dlgFileModel = (DialogFileModel) getDialogModel();
	}

	@Override
	public String getTitle() {
		return dlgFileModel.getTitle();
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);
		
		createLayoutCentered(ctx, width, height);
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			
			Nuklear.nk_layout_row_dynamic(ctx, 250, 1);
			String directory = dlgFileModel.getDirectory().isBlank()
					? Constants.BASE_PATH + appSettings.getProjectFilePath()
					: dlgFileModel.getDirectory();
			String extension = dlgFileModel.getExtension().isBlank()
					? Constants.PROJECT_FILE_EXT : dlgFileModel.getExtension();
			boolean isOpenFilePerformed = nk_file_list_view(
					ctx, directory, extension, dlgFileModel);

			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
    		Nuklear.nk_label(ctx, " File: " + dlgFileModel.getSelectedFileName(), Nuklear.NK_TEXT_LEFT);
    		
    		nk_spacer(ctx, spacer1, 1);
    		Nuklear.nk_layout_row_dynamic(ctx, rowHeight, dlgFileModel.getConfirmStates().length + 2);
    		Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_LEFT);
    		for (ConfirmState confirmState : dlgFileModel.getConfirmStates()) {
    			if (nk_button_label(ctx, confirmState.value())) {
    				if (confirmState == ConfirmState.open)
    					isOpenFilePerformed = true;
    				else {
    					dlgFileModel.setConfirmState(confirmState);
    					close();
    				}
                }
			}
			nk_spacer(ctx, spacer1, 1);

			if (isOpenFilePerformed)
				open();
		}
	}
	
	private void open() {
		dlgFileModel.setConfirmState(ConfirmState.open);
		close();
	}

}
