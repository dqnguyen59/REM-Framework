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
import org.smartblackbox.qfs.Constants;
import org.smartblackbox.qfs.gui.model.NuklearModel;
import org.smartblackbox.qfs.settings.Project;
import org.smartblackbox.qfs.settings.Settings;
import org.smartblackbox.qfs.settings.Settings.*;

import java.util.Objects;

public class FrameSettings extends AbstractFrame {

    private final Project project = Project.getInstance();

    private final Settings     settings         = project.settings;
    private final StringBuffer bufElectricField = new StringBuffer();
    private final StringBuffer bufMagneticField = new StringBuffer();

    private final StringBuffer bufScale             = new StringBuffer();
    private final StringBuffer bufDepthVisibility   = new StringBuffer();
    private final StringBuffer bufAlphaSlice        = new StringBuffer();
    private final StringBuffer bufIntensitySlice    = new StringBuffer();
    private final StringBuffer bufVisibleIndexX     = new StringBuffer();
    private final StringBuffer bufVisibleIndexY     = new StringBuffer();
    private final StringBuffer bufVisibleIndexZ     = new StringBuffer();
    private final StringBuffer bufAlphaAll          = new StringBuffer();
    private final StringBuffer bufIntensityAll      = new StringBuffer();
    private final StringBuffer bufShininess         = new StringBuffer();
    private final StringBuffer bufConstantFrequency = new StringBuffer();

    public FrameSettings(NuklearModel frames) {
        super(frames);
    }

    @Override
    public String getTitle() {
        return "Settings";
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);

        int heightAdjust = switch (settings.getSliceType()) {
            case none -> -60;
            case sliceXZ, sliceYZ, sliceXY -> 70;
            default -> 0;
        };
        if (Objects.requireNonNull(settings.getNodeFormulaVersion()) == NodeFormulaVersion.v_0_2) {
            heightAdjust += 70;
        }

        int width  = 300;
        int height = 720;
        createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 120, width, height + heightAdjust);
    }

    @Override
    protected void layout(NkContext ctx, int x, int y, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            float leftCol  = 0.6f;
            float rightCol = 0.4f;
            settings.scale = nk_label_edit(ctx, stack, " Scale:", bufScale, settings.scale, leftCol,
                                           rightCol);
            settings.scale = nk_slider(ctx, 0.2, settings.scale, 8.0, 0.1);
            nk_spacer(ctx, spacer1, 1);

            settings.setDepthVisibility(
                    nk_label_edit(ctx, stack, " Depth Visibility:", bufDepthVisibility,
                                  settings.getDepthVisibility(), leftCol, rightCol));
            settings.setDepthVisibility(
                    nk_slider(ctx, 0.0, settings.getDepthVisibility(), 1.0, 0.01));
            nk_spacer(ctx, spacer1, 1);

            settings.setNodeFormulaVersion(NodeFormulaVersion.valueOf(
                    nk_label_combo_options(ctx, stack, " Node Formula Version:",
                                           NodeFormulaVersion.getValues(),
                                           settings.getNodeFormulaVersion().name(), leftCol,
                                           rightCol)));

            settings.setColorMode(ColorMode.valueOf(
                    nk_label_combo_options(ctx, stack, " Color Mode:", ColorMode.getValues(),
                                           settings.getColorMode().name(), leftCol, rightCol)));

            if (project.getDimensionZ() > 0) {
                settings.setSliceType(SliceType.valueOf(
                        nk_label_combo_options(ctx, stack, " Slice Type:", SliceType.getValues(),
                                               settings.getSliceType().name(), leftCol, rightCol)));
            }

            nk_spacer(ctx, spacer1, 1);

            switch (settings.getSliceType()) {
                case sliceX:
                    settings.setVisibleIndexX(
                            nk_label_edit(ctx, stack, " Slice X:", bufVisibleIndexX,
                                          settings.getVisibleIndexX(), leftCol, rightCol));
                    settings.setVisibleIndexX((int) nk_slider(ctx, 1, settings.getVisibleIndexX(),
                                                              project.getDimensionX() - 2, 1.0));
                    break;
                case sliceY:
                    settings.setVisibleIndexY(
                            nk_label_edit(ctx, stack, " Slice Y:", bufVisibleIndexY,
                                          settings.getVisibleIndexY(), leftCol, rightCol));
                    settings.setVisibleIndexY((int) nk_slider(ctx, 1, settings.getVisibleIndexY(),
                                                              project.getDimensionY() - 2, 1.0));
                    break;
                case sliceZ:
                    settings.setVisibleIndexZ(
                            nk_label_edit(ctx, stack, " Slice Z:", bufVisibleIndexZ,
                                          settings.getVisibleIndexZ(), leftCol, rightCol));
                    settings.setVisibleIndexZ((int) nk_slider(ctx, 1, settings.getVisibleIndexZ(),
                                                              project.getDimensionZ() - 2, 1.0));
                    break;
                case sliceXZ:
                    height = 786;
                    settings.setVisibleIndexX(
                            nk_label_edit(ctx, stack, " Slice X:", bufVisibleIndexX,
                                          settings.getVisibleIndexX(), leftCol, rightCol));
                    settings.setVisibleIndexX((int) nk_slider(ctx, 1, settings.getVisibleIndexX(),
                                                              project.getDimensionX() - 2, 1.0));
                    nk_spacer(ctx, spacer1, 1);

                    settings.setVisibleIndexZ(
                            nk_label_edit(ctx, stack, " Slice Z:", bufVisibleIndexZ,
                                          settings.getVisibleIndexZ(), leftCol, rightCol));
                    settings.setVisibleIndexZ((int) nk_slider(ctx, 1, settings.getVisibleIndexZ(),
                                                              project.getDimensionZ() - 2, 1.0));
                    break;
                case sliceYZ:
                    height = 786;
                    settings.setVisibleIndexY(
                            nk_label_edit(ctx, stack, " Slice Y:", bufVisibleIndexY,
                                          settings.getVisibleIndexY(), leftCol, rightCol));
                    settings.setVisibleIndexY((int) nk_slider(ctx, 1, settings.getVisibleIndexY(),
                                                              project.getDimensionY() - 2, 1.0));
                    nk_spacer(ctx, spacer1, 1);

                    settings.setVisibleIndexZ(
                            nk_label_edit(ctx, stack, " Slice Z:", bufVisibleIndexZ,
                                          settings.getVisibleIndexZ(), leftCol, rightCol));
                    settings.setVisibleIndexZ((int) nk_slider(ctx, 1, settings.getVisibleIndexZ(),
                                                              project.getDimensionZ() - 2, 1.0));
                    break;
                case sliceXY:
                    height = 786;
                    settings.setVisibleIndexX(
                            nk_label_edit(ctx, stack, " Slice X:", bufVisibleIndexX,
                                          settings.getVisibleIndexX(), leftCol, rightCol));
                    settings.setVisibleIndexX((int) nk_slider(ctx, 1, settings.getVisibleIndexX(),
                                                              project.getDimensionX() - 2, 1.0));
                    nk_spacer(ctx, spacer1, 1);

                    settings.setVisibleIndexY(
                            nk_label_edit(ctx, stack, " Slice Y:", bufVisibleIndexY,
                                          settings.getVisibleIndexY(), leftCol, rightCol));
                    settings.setVisibleIndexY((int) nk_slider(ctx, 1, settings.getVisibleIndexY(),
                                                              project.getDimensionY() - 2, 1.0));
                    break;
                default:
                    break;

            }
            nk_spacer(ctx, spacer1, 1);

            if (Objects.requireNonNull(settings.getSliceType()) == SliceType.none) {
                settings.setAlphaAll(nk_label_edit(ctx, stack, " Alpha Color:", bufAlphaAll,
                                                   settings.getAlphaAll(), leftCol, rightCol));
                settings.setAlphaAll(nk_slider(ctx, 0.0, settings.getAlphaAll(), 1.0, 0.01));
                nk_spacer(ctx, spacer1, 1);

                settings.setIntensityAll(
                        nk_label_edit(ctx, stack, " Intensity Color:", bufIntensityAll,
                                      settings.getIntensityAll(), leftCol, rightCol));
                settings.setIntensityAll(
                        nk_slider(ctx, 0.0, settings.getIntensityAll(), Constants.MAX_INTENSITY,
                                  1.0));
                nk_spacer(ctx, spacer1, 1);
            } else {
                settings.setAlphaSlice(nk_label_edit(ctx, stack, " Alpha Color:", bufAlphaSlice,
                                                     settings.getAlphaSlice(), leftCol, rightCol));
                settings.setAlphaSlice(nk_slider(ctx, 0.0, settings.getAlphaSlice(), 1.0, 0.01));
                nk_spacer(ctx, spacer1, 1);

                settings.setIntensitySlice(
                        nk_label_edit(ctx, stack, " Intensity Color:", bufIntensitySlice,
                                      settings.getIntensitySlice(), leftCol, rightCol));
                settings.setIntensitySlice(
                        nk_slider(ctx, 0.0, settings.getIntensitySlice(), Constants.MAX_INTENSITY,
                                  1.0));
                nk_spacer(ctx, spacer1, 1);
            }

            settings.setShininess(
                    nk_label_edit(ctx, stack, " Shininess:", bufShininess, settings.getShininess(),
                                  leftCol, rightCol));
            settings.setShininess(nk_slider(ctx, 0, settings.getShininess(), 1.0, 0.01));
            nk_spacer(ctx, spacer1, 1);

            project.setConstantFrequency(
                    nk_label_edit(ctx, stack, " Constant Frequency:", bufConstantFrequency,
                                  project.getConstantFrequency(), leftCol, rightCol));
            project.setConstantFrequency(
                    nk_slider(ctx, 0, project.getConstantFrequency(), 1.0, 0.0001));
            nk_spacer(ctx, spacer1, 1);

            project.setConstantElectricFactor(
                    nk_label_edit(ctx, stack, " Electric Field:", bufElectricField,
                                  project.getConstantElectricFactor(),
                                  appSettings.getFormatScientific8(), leftCol, rightCol));
            project.setConstantElectricFactor(
                    nk_slider(ctx, 0, project.getConstantElectricFactor(), 1.0, 0.0001));
            nk_spacer(ctx, spacer1, 1);

            if (settings.getNodeFormulaVersion() == NodeFormulaVersion.v_0_2) {
                project.setConstantMagneticFactor(
                        nk_label_edit(ctx, stack, " Magnetic Field:", bufMagneticField,
                                      project.getConstantMagneticFactor(),
                                      appSettings.getFormatScientific8(), leftCol, rightCol));
                project.setConstantMagneticFactor(
                        nk_slider(ctx, 0, project.getConstantMagneticFactor(), 0.9999, 0.0001));
                nk_spacer(ctx, spacer1, 1);
            }

            Nuklear.nk_layout_row_end(ctx);
        }
    }

}