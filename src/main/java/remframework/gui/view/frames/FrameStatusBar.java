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

import org.lwjgl.nuklear.NkColor;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.helpers.Constants;
import remframework.gui.model.NuklearModel;
import remframework.gui.model.NuklearModel.Frame;
import remframework.gui.model.Theme;
import remframework.opengl.model.RemModel;
import remframework.opengl.model.RemModel.TranslationMode;
import remframework.settings.Project;

public class FrameStatusBar extends AbstractFrame {

    private final Project  project  = Project.getInstance();
    private final RemModel remModel = project.getRemModel();

    private final float[] col = {230, 90, 60, 60, 10, 50, 40, 40, 40, 40, 40, 40, 1400};

    public FrameStatusBar(NuklearModel frames) {
        super(frames);
        windowOptions = Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }

    @Override
    public String getTitle() {
        return "Statusbar";
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);

        int width = getWindowWidth();
        int height = Constants.STATUS_BAR_HEIGHT;

        NkColor savedColor = NkColor.create().set(ctx.style().window().fixed_background().data().color());

        ctx.style().window().fixed_background().data().color(Constants.TRANSPARENT_BG_COLOR);

        createLayout(ctx, 0, getWindowHeight() - height, width, height);
        ctx.style().window().fixed_background().data().color(savedColor);
    }

    @Override
    public void layout(NkContext ctx, int x, int y, int width, int height) {
        NkColor savedCol = NkColor.create().set(ctx.style().button().normal().data().color());

        int i = 0;
        Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, 30, 13);
        Nuklear.nk_layout_row_push(ctx, col[i++] / width);
        Nuklear.nk_label(ctx, String.format(" CurrentNode.xyz: (%d, %d, %d) (%.1f);",
                                            remModel.getCurrentMouseNodeIndex().x,
                                            remModel.getCurrentMouseNodeIndex().y,
                                            remModel.getCurrentMouseNodeIndex().z,
                                            remModel.getCurrentMouseNode() == null ? 0 : remModel.getCurrentMouseNode().getAbsorptionFactor()
                                            ), Nuklear.NK_TEXT_LEFT);

        Nuklear.nk_layout_row_push(ctx, col[i++] / width);
        Nuklear.nk_label(ctx, " Simulation: ", Nuklear.NK_TEXT_LEFT);

        ctx.style().button().normal().data().color(remModel.isSimulating() ? Theme.blue1 : savedCol);

        Nuklear.nk_layout_row_push(ctx, col[i++] / width);
        if (nk_button_label(ctx, remModel.getBtnSimulatingLabel()))
            remModel.toggleSimulation();

        ctx.style().button().normal().data().color(Theme.blue1);
        Nuklear.nk_layout_row_push(ctx, col[i++] / width);
        if (nk_button_label(ctx, "Reset")) {
            project.reset();
        }

        Nuklear.nk_layout_row_push(ctx, col[i++] / width);
        Nuklear.nk_label(ctx, "", Nuklear.NK_TEXT_LEFT);

        Nuklear.nk_layout_row_push(ctx, col[i++] / width);
        Nuklear.nk_label(ctx, " TranslationMode: ", Nuklear.NK_TEXT_LEFT);

        String s;
        String tip = "";
        for (TranslationMode m : TranslationMode.values()) {
            Nuklear.nk_layout_row_push(ctx, col[i++] / width);

            ctx.style().button().normal().data().color(m == remModel.getTranslationMode() ? Theme.blue1 : savedCol);

            switch (m) {
                case normal:
                    s = "N";
                    tip = "Normal mode";
                    break;
                case rain:
                    s = "R";
                    tip = "Rain mode: click on a node to simulate a rain drop";
                    break;
                case alpha:
                    s = "A";
                    tip = "Alpha mode: keys [up | down ]";
                    break;
                case intensity:
                    s = "I";
                    tip = "Intensity mode: keys [up | down]";
                    break;
                case light:
                    s = "L";
                    tip = "Light mode: Ctrl-{Shift}->[up | down | left | right | page-up | page-down]";
                    break;
                case occupancy:
                    s = "O";
                    tip = "Occupancy mode: keys [up | down | left | right]";
                    break;
                default:
                    s = "";
                    break;
            }

            tooltip(ctx, tip);
            if (nk_button_label(ctx, s)) {
                remModel.setTranslationMode(m);
                if (m == TranslationMode.rain)
                    nuklearModel.toggleFrame(Frame.rain);
            }
        }

        Nuklear.nk_layout_row_push(ctx, col[i] / width);
        Nuklear.nk_label(ctx, String.format(" %s", remModel.getCustomStatus()), Nuklear.NK_TEXT_LEFT);

        Nuklear.nk_layout_row_end(ctx);

        ctx.style().button().normal().data().color(savedCol);
    }

}