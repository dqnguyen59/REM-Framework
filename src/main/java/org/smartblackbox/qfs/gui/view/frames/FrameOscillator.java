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

import org.joml.Vector3i;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import org.smartblackbox.qfs.Constants;
import org.smartblackbox.qfs.gui.model.NuklearModel;
import org.smartblackbox.qfs.opengl.model.entity.Identity;
import org.smartblackbox.qfs.opengl.model.REMModel;
import org.smartblackbox.qfs.opengl.model.REMModel.NodeSelectionMode;
import org.smartblackbox.qfs.opengl.utils.OscillatorType;
import org.smartblackbox.qfs.settings.QFSProject;

public class FrameOscillator extends AbstractFrame {

	private QFSProject qfsProject = QFSProject.getInstance();
	private REMModel   REMModel   = qfsProject.getQfsModel();

	private int optionsNoScroll = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_NO_SCROLLBAR;
	private int groupNoScroll = optionsNoScroll | Nuklear.NK_WINDOW_BORDER;
	private int autoHideScroll = Nuklear.NK_WINDOW_SCROLL_AUTO_HIDE;

	private int width = 510;
	private int height = 640;
	private double leftCol = 0.55;
	private double rightCol = 0.45;
	private int      groupHeight = rowHeightEdit * 7;
	private Identity identityEdit;

	private StringBuffer bufStartAngleX = new StringBuffer();
	private StringBuffer bufStartAngleY = new StringBuffer();
	private StringBuffer bufStartAngleZ = new StringBuffer();
	private StringBuffer bufAngleIncX = new StringBuffer();
	private StringBuffer bufAngleIncY = new StringBuffer();
	private StringBuffer bufAngleIncZ = new StringBuffer();
	private StringBuffer bufAmplitudeX = new StringBuffer();
	private StringBuffer bufAmplitudeY = new StringBuffer();
	private StringBuffer bufAmplitudeZ = new StringBuffer();
	private int      index;
	private Identity lastSelectedIdentity;

	public FrameOscillator(NuklearModel frames) {
		super(frames);
	}

	@Override
	public String getTitle() {
		return "Oscillator";
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);

		index = qfsProject.identities.size();
		createLayout(ctx, 0, getWindowHeight() - (Constants.STATUS_BAR_HEIGHT + height), width, height);
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, 590, 2);
			
			Nuklear.nk_layout_row_push(ctx, 0.45f);
			layoutLeftSide(ctx, stack);
			
			Nuklear.nk_layout_row_push(ctx, 0.54999f);
			layoutRightSide(ctx, stack);
			
			Nuklear.nk_layout_row_end(ctx);
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// LEFT LAYOUT //////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private void layoutLeftSide(NkContext ctx, MemoryStack stack) {
		if (Nuklear.nk_group_begin(ctx, "Oscillator List", groupNoScroll)) {
			
			Nuklear.nk_layout_row_dynamic(ctx, 516, 1);
			if (Nuklear.nk_group_begin(ctx, "Oscillator List View", autoHideScroll)) {

				Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
				for (Identity identity : qfsProject.identities) {
					boolean selected = REMModel.getSelectedOscillator() == identity;
					selected = Nuklear.nk_select_label(ctx, identity.getName(), Nuklear.NK_TEXT_ALIGN_LEFT, selected);
					if (selected) {
						REMModel.setSelectedOscillator(identity);
						if (lastSelectedIdentity != identity) {
							identityEdit = null;
						}
						lastSelectedIdentity = identity;
					}
				}
				
				Nuklear.nk_group_end(ctx);
			}
			
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 3);
			if (nk_button_label(ctx, "Add")) {
				REMModel.setSelectedOscillator(new Identity("Oscillator " + index, new Vector3i(-1, -1, -1)));
				qfsProject.identities.add(REMModel.getSelectedOscillator());
				index++;
			}
			
			if (nk_button_label(ctx, "Remove")) {
				int i = qfsProject.identities.indexOf(REMModel.getSelectedOscillator());
				qfsProject.identities.remove(REMModel.getSelectedOscillator());
				
				if (i >= qfsProject.identities.size())
					i = qfsProject.identities.size() - 1;
				
				REMModel.setSelectedOscillator(qfsProject.identities.size() > 0 ? qfsProject.identities.get(i) : null);
			}

			if (nk_button_label(ctx, "Close"))
				close();

			Nuklear.nk_group_end(ctx);
		}

	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// LEFT LAYOUT //////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// RIGHT LAYOUT /////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private void layoutOscillatorX(NkContext ctx, MemoryStack stack) {
		Nuklear.nk_layout_row_dynamic(ctx, groupHeight, 1);
		if (Nuklear.nk_group_begin(ctx, "X-Oscillator", optionsNoScroll)) {
			identityEdit.getActive().x         = nk_check_label(ctx, "Active", identityEdit.getActive().x, leftCol, rightCol);
			identityEdit.getOscillatorType().x = OscillatorType.valueOf(nk_label_combo_options(ctx, stack,
			                                                                                   " Type: ",
			                                                                                   OscillatorType.getValues(),
			                                                                                   identityEdit.getOscillatorType().x.name(),
			                                                                                   leftCol, rightCol
			                                                                                  ));

			identityEdit.getStartangle().x     = nk_label_edit(ctx, stack, " Start Angle:", bufStartAngleX, identityEdit.getStartangle().x, leftCol, rightCol);
			identityEdit.getAngleIncrement().x = nk_label_edit(ctx, stack, " Angle Increment:", bufAngleIncX, identityEdit.getAngleIncrement().x, leftCol, rightCol);
			identityEdit.getAmplitude().x      = nk_label_edit(ctx, stack, " Amplitude:", bufAmplitudeX, identityEdit.getAmplitude().x, leftCol, rightCol);
			Nuklear.nk_group_end(ctx);
		}
		Nuklear.nk_layout_row_dynamic(ctx, spacer2, 1);
	}
	
	private void layoutOscillatorY(NkContext ctx, MemoryStack stack) {
		Nuklear.nk_layout_row_dynamic(ctx, groupHeight, 1);
		if (Nuklear.nk_group_begin(ctx, "Y-Oscillator", optionsNoScroll)) {
			identityEdit.getActive().y         = nk_check_label(ctx, "Active", identityEdit.getActive().y, leftCol, rightCol);
			identityEdit.getOscillatorType().y = OscillatorType.valueOf(nk_label_combo_options(ctx, stack,
			                                                                                   " Type: ",
			                                                                                   OscillatorType.getValues(),
			                                                                                   identityEdit.getOscillatorType().y.name(),
			                                                                                   leftCol, rightCol
			                                                                                  ));
			identityEdit.getStartangle().y     = nk_label_edit(ctx, stack, " Start Angle:", bufStartAngleY, identityEdit.getStartangle().y, leftCol, rightCol);
			identityEdit.getAngleIncrement().y = nk_label_edit(ctx, stack, " Angle Increment:", bufAngleIncY, identityEdit.getAngleIncrement().y, leftCol, rightCol);
			identityEdit.getAmplitude().y      = nk_label_edit(ctx, stack, " Amplitude:", bufAmplitudeY, identityEdit.getAmplitude().y, leftCol, rightCol);
			Nuklear.nk_group_end(ctx);
		}
		Nuklear.nk_layout_row_dynamic(ctx, spacer2, 1);
	}
	
	private void layoutOscillatorZ(NkContext ctx, MemoryStack stack) {
		Nuklear.nk_layout_row_dynamic(ctx, groupHeight, 1);
		if (Nuklear.nk_group_begin(ctx, "Z-Oscillator", optionsNoScroll)) {
			identityEdit.getActive().z         = nk_check_label(ctx, "Active", identityEdit.getActive().z, leftCol, rightCol);
			identityEdit.getOscillatorType().z = OscillatorType.valueOf(nk_label_combo_options(ctx, stack,
			                                                                                   " Type: ",
			                                                                                   OscillatorType.getValues(),
			                                                                                   identityEdit.getOscillatorType().z.name(),
			                                                                                   leftCol, rightCol
			                                                                                  ));
			identityEdit.getStartangle().z     = nk_label_edit(ctx, stack, " Start Angle:", bufStartAngleZ, identityEdit.getStartangle().z, leftCol, rightCol);
			identityEdit.getAngleIncrement().z = nk_label_edit(ctx, stack, " Angle Increment:", bufAngleIncZ, identityEdit.getAngleIncrement().z, leftCol, rightCol);
			identityEdit.getAmplitude().z      = nk_label_edit(ctx, stack, " Amplitude:", bufAmplitudeZ, identityEdit.getAmplitude().z, leftCol, rightCol);
			Nuklear.nk_group_end(ctx);
		}
		Nuklear.nk_layout_row_dynamic(ctx, spacer2, 1);
	}
	
	private void layoutRightSide(NkContext ctx, MemoryStack stack) {
		if (REMModel.getSelectedOscillator() != null
            &&
            Nuklear.nk_group_begin(ctx, "Oscillator", groupNoScroll)) {
			
			Identity identity = REMModel.getSelectedOscillator();
			
			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
			Nuklear.nk_label(ctx, String.format(" Add '%s' on selected node", REMModel.selectedIdentity.getName()), Nuklear.NK_TEXT_LEFT);

			Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 2);
			if (identity.nodeIndex.x != -1 && identity.nodeIndex.y != -1 && identity.nodeIndex.z != -1) {
				Nuklear.nk_label(ctx, String.format(" Node: [%d, %d, %d]",
				                                    identity.nodeIndex.x,
				                                    identity.nodeIndex.y,
				                                    identity.nodeIndex.z), Nuklear.NK_TEXT_LEFT);
				REMModel.getCurrentMouseNodeIndex().x = identity.nodeIndex.x;
				REMModel.getCurrentMouseNodeIndex().y = identity.nodeIndex.y;
				REMModel.getCurrentMouseNodeIndex().z = identity.nodeIndex.z;
			}
			else
				Nuklear.nk_label(ctx, " Node: [?, ?, ?]", Nuklear.NK_TEXT_LEFT);
			
	        if (nk_button_label(ctx, "Select Node")) {
	        	REMModel.setNodeSelectionMode(NodeSelectionMode.oscillator);
				close();
			}
	        
	        if (identityEdit == null) {
				identityEdit = REMModel.getSelectedOscillator().clone();
	        }
	        
			Nuklear.nk_layout_row_dynamic(ctx, spacer1, 1);
			if (identityEdit != null) {
				Nuklear.nk_layout_row_dynamic(ctx, 450, 1);
				if(Nuklear.nk_group_begin(ctx, "Scrollable", 0)) {
					layoutOscillatorX(ctx, stack);
					layoutOscillatorY(ctx, stack);
					layoutOscillatorZ(ctx, stack);
					
					Nuklear.nk_group_end(ctx);
				}
				
				Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 3);
				Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_LEFT);
				if (nk_button_label(ctx, "Apply")) {
					if (REMModel.getSelectedOscillator().nodeIndex.x >= 0) {
						int i = qfsProject.identities.indexOf(REMModel.getSelectedOscillator());
						qfsProject.identities.set(i, REMModel.selectedIdentity = identityEdit.clone());
					}
					else {
						nuklearModel.showWarningDialog("Node Selection", "You haven't select a node, please select a node first.");
					}
				}
			}
			Nuklear.nk_group_end(ctx);
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// RIGHT LAYOUT /////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}