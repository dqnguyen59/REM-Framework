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
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.opengl.controller.LogType;
import remframework.opengl.controller.TransportSystem;

public class FrameTransportSystem extends AbstractFrame {

    private final TransportSystem transportSystem = TransportSystem.getInstance();


    private final StringBuffer bufGradientGain     = new StringBuffer();
    private final StringBuffer bufAccelerationGain = new StringBuffer();
    private final StringBuffer bufImpulseGain      = new StringBuffer();

    private final StringBuffer bufTransportMomentumGain = new StringBuffer();
    private final StringBuffer bufTransportMomentumMax  = new StringBuffer();
    private final StringBuffer bufTransferThreshold     = new StringBuffer();
    private final StringBuffer bufCycleToMove           = new StringBuffer();


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
        int height = 680;
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

            transportSystem.setAccelerationGain(
                    nk_label_edit(ctx, stack, " Acceleration Gain:", bufAccelerationGain,
                                  transportSystem.getAccelerationGain(),
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setAccelerationGain(
                    nk_slider(ctx, 0, transportSystem.getAccelerationGain(), 1.0, 0.000001));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setImpulseGain(
                    nk_label_edit(ctx, stack, " Impulse Gain:", bufImpulseGain,
                                  transportSystem.getImpulseGain(),
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setImpulseGain(
                    nk_slider(ctx, 0, transportSystem.getImpulseGain(), 1.0, 0.000001));
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

            transportSystem.setCyclesToMove(
                    (int) nk_label_edit(ctx, stack, " Cycles To Move:", bufCycleToMove,
                                        transportSystem.getCyclesToMove(),
                                        appSettings.getFormatScientific9(), leftCol, rightCol));
            transportSystem.setCyclesToMove(
                    (int) nk_slider(ctx, 0, transportSystem.getCyclesToMove(), 100, 1));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setEnableTransport(
                    nk_check_label(ctx, " Enable Transport:", transportSystem.isEnableTransport(), leftCol,
                                   rightCol));
            nk_spacer(ctx, spacer1, 1);

            transportSystem.setLogType(LogType.valueOf(
                    nk_label_combo_options(ctx, stack, " Slice Type:", LogType.getValues(),
                                           transportSystem.getLogType().name(), leftCol, rightCol)));

            nk_spacer(ctx, spacer1, 1);


            transportSystem.setLogToConsole(
                    nk_check_label(ctx, " Log to console:", transportSystem.isLogToConsole(), leftCol,
                                   rightCol));
            nk_spacer(ctx, spacer1, 1);
        }
    }

}