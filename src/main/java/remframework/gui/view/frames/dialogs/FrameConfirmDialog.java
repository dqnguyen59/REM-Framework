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
package remframework.gui.view.frames.dialogs;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.AbstractDialogModel.ConfirmState;
import remframework.gui.model.DialogDefaultModel;
import remframework.gui.model.NuklearModel;

public class FrameConfirmDialog extends FrameDialog {

	private int width = 600;
	private int height = 360;
	private DialogDefaultModel dlgModel;

	public FrameConfirmDialog(NuklearModel frames) {
		super(frames);
		dlgModel = (DialogDefaultModel) getDialogModel();
	}

	@Override
	public String getTitle() {
		return dlgModel.getTitle();
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);

		createLayoutCentered(ctx, Math.min(width, Math.max(240, getWindowWidth()-32)),
                Math.min(height, Math.max(180, getWindowHeight()-80)));
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // A clipped, scrollable message area keeps all confirmation buttons visible.
            Nuklear.nk_layout_row_dynamic(ctx, Math.max(rowHeight*2,height-rowHeight*5), 1);
            if(Nuklear.nk_group_begin(ctx,"Dialog message",0)) {
                int charactersPerLine=Math.max(1,(width-80)/rowHeight);
                for(String line:dlgModel.getMessage().split("\n",-1)) {
                    // Conservative height allows native font-based wrapping; long messages scroll.
                    int rows=Math.max(1,(line.length()+charactersPerLine-1)/charactersPerLine);
                    Nuklear.nk_layout_row_dynamic(ctx,rows*rowHeight,1);
                    Nuklear.nk_label_wrap(ctx,line);
                }
                Nuklear.nk_group_end(ctx);
            }

            ConfirmState[] confirmStates = dlgModel.getConfirmStates();

    		Nuklear.nk_layout_row_dynamic(ctx, rowHeight, confirmStates.length + 2);
    		Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_CENTERED);

    		for (ConfirmState confirmState : confirmStates) {
    			if (nk_button_label(ctx, confirmState.value())) {
    				dlgModel.setConfirmState(confirmState);
    				close();
                }
			}

    		Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
    		Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_CENTERED);
        }

	}

}