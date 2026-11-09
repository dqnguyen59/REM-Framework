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

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.helpers.Constants;
import remframework.settings.Project;
import remframework.settings.Settings;
import remframework.settings.Settings.ColorMode;
import remframework.settings.Settings.NodeFormulaVersion;
import remframework.settings.Settings.SliceType;

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

    private final StringBuffer bufPhysicalThickness = new StringBuffer();
    private boolean surveyDisplayPage;
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
            case physicalXY, physicalXZ, physicalYZ -> settings.isShowOutsideSlab()?150:90;
            case none, identitiesOnly -> -60;
            case sliceXZ, sliceYZ, sliceXY -> 70;
            default -> 0;
        };
        if (Objects.requireNonNull(settings.getNodeFormulaVersion()) == NodeFormulaVersion.v_0_2) {
            heightAdjust += 70;
        }

        int width  = 400;
        int height = 720;
        createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 120, width, height + heightAdjust);
    }

    @Override
    protected void layout(NkContext ctx, int x, int y, int width, int height) {
        Nuklear.nk_layout_row_dynamic(ctx,26,1);
        if(Nuklear.nk_button_label(ctx,surveyDisplayPage?"Back to general settings":"Probe / path display"))surveyDisplayPage=!surveyDisplayPage;
        if(surveyDisplayPage){surveyDisplaySettings(ctx);return;}
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
                    nk_label_combo_options(ctx, stack, " Color TranslationMode:", ColorMode.getValues(),
                                           settings.getColorMode().name(), leftCol, rightCol)));

            if (project.getDimensionZ() > 0) {
                settings.setSliceType(SliceType.fromLabel(
                        nk_label_combo_options(ctx, stack, " Rendering:", SliceType.getValues(),
                                               settings.getSliceType().getLabel(), leftCol, rightCol)));
            }

            nk_spacer(ctx, spacer1, 1);

            switch (settings.getSliceType()) {
                case physicalXY, physicalXZ, physicalYZ:
                    settings.setPhysicalSliceThickness(nk_label_edit(ctx,stack," Slab thickness:",bufPhysicalThickness,
                        settings.getPhysicalSliceThickness(),leftCol,rightCol));
                    settings.setPhysicalSliceThickness(nk_slider(ctx,0.05,settings.getPhysicalSliceThickness(),5.0,0.05));
                    settings.setShowOutsideSlab(nk_check_label(ctx," Show other layers transparently",settings.isShowOutsideSlab(),leftCol,rightCol));
                    if(settings.isShowOutsideSlab()) {
                        nk_label_value(ctx," Other-layer opacity:",String.format(java.util.Locale.ROOT,"%.0f%%",100*settings.getOutsideSlabOpacity()),leftCol,rightCol);
                        settings.setOutsideSlabOpacity(nk_slider(ctx,0,settings.getOutsideSlabOpacity(),1,.01));
                    }
                    nk_label_value(ctx," Center:","Lattice center",leftCol,rightCol);
                    nk_label_value(ctx," Selection:","Reference positions",leftCol,rightCol);
                    break;
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
                                  appSettings.getFormatScientific9(), leftCol, rightCol));
            project.setConstantElectricFactor(
                    nk_slider(ctx, 0, project.getConstantElectricFactor(), 1.0, 0.0001));
            nk_spacer(ctx, spacer1, 1);

            if (settings.getNodeFormulaVersion() == NodeFormulaVersion.v_0_2) {
                project.setConstantMagneticFactor(
                        nk_label_edit(ctx, stack, " Magnetic Field:", bufMagneticField,
                                      project.getConstantMagneticFactor(),
                                      appSettings.getFormatScientific9(), leftCol, rightCol));
                project.setConstantMagneticFactor(
                        nk_slider(ctx, 0, project.getConstantMagneticFactor(), 0.9999, 0.0001));
                nk_spacer(ctx, spacer1, 1);
            }

            Nuklear.nk_layout_row_end(ctx);
        }
    }

    private void surveyDisplaySettings(NkContext ctx) {
        Nuklear.nk_layout_row_dynamic(ctx,48,1);
        Nuklear.nk_label_wrap(ctx,"Opacity: 0 hides overlays; 100% is opaque. Changes save automatically.");
        double probes=surveyOpacity(ctx,"Other probes",appSettings.getSurveyProbeOpacity());
        double highlights=surveyOpacity(ctx,"Highlighted nodes / probes",appSettings.getSurveyHighlightOpacity());
        double source=surveyOpacity(ctx,"Source node (yellow)",appSettings.getSurveySourceOpacity());
        double lines=surveyOpacity(ctx,"Selected path lines",appSettings.getSurveyLineOpacity());
        appSettings.setSurveyDisplayOpacity(probes,highlights,source,lines);
        boolean connections=nk_check_label(ctx,"Show connected-node lines",appSettings.isShowLatticeConnections(),.8,.2);
        double connectionOpacity=surveyOpacity(ctx,"Connection opacity",appSettings.getLatticeConnectionOpacity());
        appSettings.setLatticeConnectionDisplay(connections,connectionOpacity);
        Nuklear.nk_layout_row_dynamic(ctx,60,1);
        Nuklear.nk_label_wrap(ctx,"Connections join visible neighboring nodes and blend their endpoint colors. Slab visibility applies; enable other layers in general settings to see their connections too.");
    }

    private double surveyOpacity(NkContext ctx,String label,double value) {
        Nuklear.nk_layout_row_dynamic(ctx,24,1);
        Nuklear.nk_label(ctx,label+String.format(java.util.Locale.ROOT," : %.0f%%",100*value),Nuklear.NK_TEXT_LEFT);
        return nk_slider(ctx,0,value,1,.01);
    }

}
