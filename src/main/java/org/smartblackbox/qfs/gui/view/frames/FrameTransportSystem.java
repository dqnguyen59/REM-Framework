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
import org.lwjgl.system.MemoryStack;
import org.smartblackbox.qfs.gui.model.NuklearModel;
import org.smartblackbox.qfs.opengl.controller.TransportSystem;

public class FrameTransportSystem extends AbstractFrame {

    private final TransportSystem transportSystem = TransportSystem.getInstance();


    private final StringBuffer bufGradientGain   = new StringBuffer();
    private final StringBuffer bufGradientFilter = new StringBuffer();

    private final StringBuffer bufTransportMomentumGain = new StringBuffer();
    private final StringBuffer bufTransportMomentumMax  = new StringBuffer();
    private final StringBuffer bufTransferThreshold     = new StringBuffer();


    public FrameTransportSystem(NuklearModel frames) {
        super(frames);
    }

    @Override
    public String getTitle() {
        return "Transport System";
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);

        int width  = 300;
        int height = 440;
        createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 120, width, height);
    }

    @Override
    protected void layout(NkContext ctx, int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            float leftCol  = 0.6f;
            float rightCol = 0.4f;

            transportSystem.setGradientGain(
                    nk_label_edit(ctx, stack, " Gradient Gain:", bufGradientGain,
                                  transportSystem.getGradientGain(),
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setGradientGain(
                    nk_slider(ctx, 0, transportSystem.getGradientGain(), 1.0, 0.000001));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setGradientFilter(
                    nk_label_edit(ctx, stack, " Gradient Filter:", bufGradientFilter,
                                  transportSystem.getGradientFilter(),
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setGradientFilter(
                    nk_slider(ctx, 0, transportSystem.getGradientFilter(), 1.0, 0.00001));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setTransportMomentumGain(
                    nk_label_edit(ctx, stack, " Momentum Gain:", bufTransportMomentumGain,
                                  transportSystem.getTransportMomentumGain(),
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setTransportMomentumGain(
                    nk_slider(ctx, 0, transportSystem.getTransportMomentumGain(), 1.0, 0.00001));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setTransportMomentumMax(
                    nk_label_edit(ctx, stack, " Momentum Max:", bufTransportMomentumMax,
                                  transportSystem.getTransportMomentumMax(),
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setTransportMomentumMax(
                    nk_slider(ctx, 0, transportSystem.getTransportMomentumMax(), 1.0, 0.000001));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setTransferThreshold(
                    nk_label_edit(ctx, stack, " Transfer Threshold:", bufTransferThreshold,
                                  transportSystem.getTransferThreshold(),
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setTransferThreshold(
                    nk_slider(ctx, 0, transportSystem.getTransferThreshold(), 1.0, 0.000001));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setLogToConsole(
                    nk_check_label(ctx, " Log to console:", transportSystem.isLogToConsole(), leftCol,
                                   rightCol));
            nk_spacer(ctx, spacer1, 1);
        }
    }

}