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
import org.smartblackbox.qfs.opengl.model.RemModel;
import org.smartblackbox.qfs.opengl.model.RemModel.NodeSelectionMode;
import org.smartblackbox.qfs.opengl.model.entity.Identity;
import org.smartblackbox.qfs.opengl.model.entity.Occupancy;
import org.smartblackbox.qfs.opengl.utils.OscillatorType;
import org.smartblackbox.qfs.settings.Project;

public class FrameIdentities extends AbstractFrame {

    private final Project  project  = Project.getInstance();
    private final RemModel remModel = project.getRemModel();

    private final int optionsNoScroll = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_NO_SCROLLBAR;
    private final int groupNoScroll   = optionsNoScroll | Nuklear.NK_WINDOW_BORDER;
    private final int autoHideScroll  = Nuklear.NK_WINDOW_SCROLL_AUTO_HIDE;

    private final double leftCol     = 0.55;
    private final double rightCol    = 0.45;
    private final int    groupHeight = rowHeightEdit * 7;

    private final StringBuffer bufStartAngleX = new StringBuffer();
    private final StringBuffer bufStartAngleY = new StringBuffer();
    private final StringBuffer bufStartAngleZ = new StringBuffer();
    private final StringBuffer bufAngleIncX   = new StringBuffer();
    private final StringBuffer bufAngleIncY   = new StringBuffer();
    private final StringBuffer bufAngleIncZ   = new StringBuffer();
    private final StringBuffer bufAmplitudeX  = new StringBuffer();
    private final StringBuffer bufAmplitudeY  = new StringBuffer();
    private final StringBuffer bufAmplitudeZ  = new StringBuffer();
    private       int          index;
//    private       Occupancy    occupancyEdit;
//    private       Occupancy    lastSelectedOccupancy;

    public FrameIdentities(NuklearModel frames) {
        super(frames);
    }

    @Override
    public String getTitle() {
        return "Identities";
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);

        index = project.occupancies.size();
        int width = 510;
        int height = 640;
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
    // LEFT LAYOUT
    // ////////////////////////////////////////////////////////////////////////////////////////////////////

    /// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void layoutLeftSide(NkContext ctx, MemoryStack stack) {
        if (Nuklear.nk_group_begin(ctx, "Identities", groupNoScroll)) {

            Nuklear.nk_layout_row_dynamic(ctx, 516, 1);
            if (Nuklear.nk_group_begin(ctx, "Identities View", autoHideScroll)) {

                Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
                for (Occupancy occupancy : project.occupancies) {
                    boolean selected = remModel.getSelectedOccupancy() == occupancy;
                    selected = Nuklear.nk_select_label(ctx, occupancy.getIdentity().getName(),
                                                       Nuklear.NK_TEXT_ALIGN_LEFT,
                                                       selected);
                    if (selected) {
                        remModel.setSelectedOccupancy(occupancy);
//                        if (lastSelectedOccupancy != occupancy) {
//                            occupancyEdit = null;
//                        }
//                        lastSelectedOccupancy = occupancy;
                    }
                }

                Nuklear.nk_group_end(ctx);
            }

            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 3);
            if (nk_button_label(ctx, "Add")) {
                remModel.setSelectedOccupancy(new Occupancy(new Identity("Identities " + index), null));
                project.occupancies.add(remModel.getSelectedOccupancy());
                index++;
            }

            if (nk_button_label(ctx, "Remove")) {
                int i = project.occupancies.indexOf(remModel.getSelectedOccupancy());
                project.occupancies.remove(remModel.getSelectedOccupancy());

                if (i >= project.occupancies.size())
                    i = project.occupancies.size() - 1;

                remModel.setSelectedOccupancy(
                        !project.occupancies.isEmpty() ? project.occupancies.get(i) : null);
            }

            if (nk_button_label(ctx, "Close"))
                close();

            Nuklear.nk_group_end(ctx);
        }

    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // LEFT LAYOUT
    // ////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RIGHT LAYOUT
    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    /// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void layoutOscillatorX(NkContext ctx, MemoryStack stack) {
        Nuklear.nk_layout_row_dynamic(ctx, groupHeight, 1);
        if (Nuklear.nk_group_begin(ctx, "X-Oscillator", optionsNoScroll)) {
            Identity identity = remModel.getSelectedOccupancy().getIdentity();
//            Identity identity = occupancyEdit.getIdentity();
            identity.getActive().x         = nk_check_label(ctx, "Active", identity.getActive().x, leftCol,
                                                            rightCol);
            identity.getOscillatorType().x = OscillatorType.valueOf(nk_label_combo_options(ctx, stack,
                                                                                           " Type: ",
                                                                                           OscillatorType.getValues(),
                                                                                           identity.getOscillatorType().x.name(),
                                                                                           leftCol,
                                                                                           rightCol
                                                                                          ));

            identity.getPhaseAngle().x     = nk_label_edit(ctx, stack, " Start Angle:", bufStartAngleX,
                                                           identity.getPhaseAngle().x, leftCol,
                                                           rightCol);
            identity.getPhaseIncrement().x = nk_label_edit(ctx, stack, " Angle Increment:", bufAngleIncX,
                                                           identity.getPhaseIncrement().x, leftCol,
                                                           rightCol);
            identity.getAmplitude().x      = nk_label_edit(ctx, stack, " Amplitude:", bufAmplitudeX,
                                                           identity.getAmplitude().x, leftCol,
                                                           rightCol);
            Nuklear.nk_group_end(ctx);
        }
        Nuklear.nk_layout_row_dynamic(ctx, spacer2, 1);
    }

    private void layoutOscillatorY(NkContext ctx, MemoryStack stack) {
        Nuklear.nk_layout_row_dynamic(ctx, groupHeight, 1);
        if (Nuklear.nk_group_begin(ctx, "Y-Oscillator", optionsNoScroll)) {
            Identity identity = remModel.getSelectedOccupancy().getIdentity();
//            Identity identity = occupancyEdit.getIdentity();
            identity.getActive().y         = nk_check_label(ctx, "Active", identity.getActive().y,
                                                            leftCol, rightCol);
            identity.getOscillatorType().y = OscillatorType.valueOf(nk_label_combo_options(ctx, stack,
                                                                                           " Type: ",
                                                                                           OscillatorType.getValues(),
                                                                                           identity.getOscillatorType().y.name(),
                                                                                           leftCol,
                                                                                           rightCol
                                                                                          ));
            identity.getPhaseAngle().y     = nk_label_edit(ctx, stack, " Start Angle:", bufStartAngleY,
                                                           identity.getPhaseAngle().y, leftCol,
                                                           rightCol);
            identity.getPhaseIncrement().y = nk_label_edit(ctx, stack, " Angle Increment:", bufAngleIncY,
                                                           identity.getPhaseIncrement().y, leftCol,
                                                           rightCol);
            identity.getAmplitude().y      = nk_label_edit(ctx, stack, " Amplitude:", bufAmplitudeY,
                                                           identity.getAmplitude().y, leftCol,
                                                           rightCol);
            Nuklear.nk_group_end(ctx);
        }
        Nuklear.nk_layout_row_dynamic(ctx, spacer2, 1);
    }

    private void layoutOscillatorZ(NkContext ctx, MemoryStack stack) {
        Nuklear.nk_layout_row_dynamic(ctx, groupHeight, 1);
        if (Nuklear.nk_group_begin(ctx, "Z-Oscillator", optionsNoScroll)) {
            Identity identity = remModel.getSelectedOccupancy().getIdentity();
//            Identity identity = occupancyEdit.getIdentity();
            identity.getActive().z         = nk_check_label(ctx, "Active", identity.getActive().z,
                                                            leftCol, rightCol);
            identity.getOscillatorType().z = OscillatorType.valueOf(nk_label_combo_options(ctx, stack,
                                                                                           " Type: ",
                                                                                           OscillatorType.getValues(),
                                                                                           identity.getOscillatorType().z.name(),
                                                                                           leftCol,
                                                                                           rightCol
                                                                                          ));
            identity.getPhaseAngle().z     = nk_label_edit(ctx, stack, " Start Angle:", bufStartAngleZ,
                                                           identity.getPhaseAngle().z, leftCol,
                                                           rightCol);
            identity.getPhaseIncrement().z = nk_label_edit(ctx, stack, " Angle Increment:", bufAngleIncZ,
                                                           identity.getPhaseIncrement().z, leftCol,
                                                           rightCol);
            identity.getAmplitude().z      = nk_label_edit(ctx, stack, " Amplitude:", bufAmplitudeZ,
                                                           identity.getAmplitude().z, leftCol,
                                                           rightCol);
            Nuklear.nk_group_end(ctx);
        }
        Nuklear.nk_layout_row_dynamic(ctx, spacer2, 1);
    }

    private void layoutRightSide(NkContext ctx, MemoryStack stack) {
        Occupancy occupancy = remModel.getSelectedOccupancy();
        if (occupancy != null
            &&
            Nuklear.nk_group_begin(ctx, "Oscillator", groupNoScroll)) {

            Vector3i  nodeIndex = occupancy.getNode() == null? new Vector3i(-1) : occupancy.getNode().getIndex();

            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
            Nuklear.nk_label(ctx, String.format(" Add '%s' on selected node",
                                                remModel.getSelectedOccupancy().getIdentity().getName()),
                             Nuklear.NK_TEXT_LEFT);

            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 2);
            if (nodeIndex.x != -1 && nodeIndex.y != -1 && nodeIndex.z != -1) {
                Nuklear.nk_label(ctx, String.format(" Node: [%d, %d, %d]",
                                                    nodeIndex.x,
                                                    nodeIndex.y,
                                                    nodeIndex.z), Nuklear.NK_TEXT_LEFT);
                remModel.getCurrentMouseNodeIndex().x = nodeIndex.x;
                remModel.getCurrentMouseNodeIndex().y = nodeIndex.y;
                remModel.getCurrentMouseNodeIndex().z = nodeIndex.z;
            } else
                Nuklear.nk_label(ctx, " Node: [?, ?, ?]", Nuklear.NK_TEXT_LEFT);

            if (nk_button_label(ctx, "Select Node")) {
                remModel.setNodeSelectionMode(NodeSelectionMode.occupancy);
                close();
            }

//            if (occupancyEdit == null) {
//                //occupancyEdit = new Occupancy(remModel.getSelectedOccupancy().getIdentity().clone(), occupancy.getNode());
//                occupancyEdit = remModel.getSelectedOccupancy().getIdentity().clone(), occupancy.getNode());
//            }

            Nuklear.nk_layout_row_dynamic(ctx, spacer1, 1);
            if (occupancy != null) {
                Nuklear.nk_layout_row_dynamic(ctx, 450, 1);
                if (Nuklear.nk_group_begin(ctx, "Scrollable", 0)) {
                    layoutOscillatorX(ctx, stack);
                    layoutOscillatorY(ctx, stack);
                    layoutOscillatorZ(ctx, stack);

                    Nuklear.nk_group_end(ctx);
                }

                Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 3);
                Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_LEFT);
                if (nk_button_label(ctx, "Apply")) {
                    if (remModel.getSelectedOccupancy().getNode().getIndex().x >= 0) {
                        int i = project.occupancies.indexOf(remModel.getSelectedOccupancy());
                        //remModel.setSelectedOccupancy(new Occupancy(occupancyEdit.getIdentity().clone(), occupancy.getNode()));
                        remModel.setSelectedOccupancy(occupancy);
                        project.occupancies.set(i, remModel.getSelectedOccupancy());
                    } else {
                        nuklearModel.showWarningDialog("Node Selection",
                                                       "You haven't select a node, please select a node " +
                                                       "first.");
                    }
                }
            }
            Nuklear.nk_group_end(ctx);
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RIGHT LAYOUT
    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}