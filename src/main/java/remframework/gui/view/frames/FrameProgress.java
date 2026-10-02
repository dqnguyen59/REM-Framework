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

import org.lwjgl.PointerBuffer;
import org.lwjgl.nuklear.NkColor;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.helpers.Constants;
import remframework.gui.model.NuklearModel;
import remframework.opengl.model.RemModel;
import remframework.settings.Project;

public class FrameProgress extends AbstractFrame {

    private Project  project  = Project.getInstance();
    private RemModel REMModel = project.getRemModel();

    PointerBuffer progress = PointerBuffer.allocateDirect(1);

    public FrameProgress(NuklearModel frames) {
        super(frames);
        windowOptions = Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }

    @Override
    public String getTitle() {
        return "Progress";
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            NkColor color = ctx.style().window().fixed_background().data().color();

            NkColor tempColor = NkColor.malloc(stack)
                                       .r(color.r())
                                       .g(color.g())
                                       .b(color.b())
                                       .a(color.a());

            ctx.style().window().fixed_background().data().color(Constants.TRANSPARENT_BG_COLOR);

            createLayoutCentered(ctx, 300, 40);

            ctx.style().window().fixed_background().data().color(tempColor);
        }
    }

    @Override
    protected void layout(NkContext ctx, int x, int y, int width, int height) {

        progress.put(0, (long) REMModel.getProgress());

        Nuklear.nk_layout_row_dynamic(ctx, 30, 1);
        Nuklear.nk_progress(ctx, progress, 100, true);

    }

}