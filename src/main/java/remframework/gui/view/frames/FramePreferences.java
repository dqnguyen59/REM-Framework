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
import remframework.opengl.model.Lattice;
import remframework.opengl.model.RainModel;
import remframework.settings.Project;
import remframework.settings.Settings;
import remframework.lattice.LatticeDescriptor;
import remframework.lattice.LatticeManager;

import java.util.List;

public class FramePreferences extends AbstractFrame {

	private final Project   project   = Project.getInstance();
	private final RainModel rainModel = project.rainModel;
	private final Lattice lattice = project.scene.getLattice();
	private final LatticeManager latticeManager = LatticeManager.getInstance();
	
	private final StringBuffer bufNumThreads   = new StringBuffer();
	private final StringBuffer bufEdgeAbsorptionLayers = new StringBuffer();
	private final StringBuffer bufSwapInterval         = new StringBuffer();
	private final StringBuffer bufForce                = new StringBuffer();
	private final StringBuffer bufAbsorptionFixedNodes = new StringBuffer();

	private double packetWavelength=8, packetAmplitude=.001;
    private boolean fixedPacketGeometry=true;
    private final StringBuffer packetWavelengthBuffer=new StringBuffer(),packetAmplitudeBuffer=new StringBuffer();
    private int diagnosticDirection;
    private final StringBuffer diagnosticDirectionBuffer=new StringBuffer();
    public FramePreferences(NuklearModel frames) {
		super(frames);
        windowOptions &= ~Nuklear.NK_WINDOW_NO_SCROLLBAR;
	}

	@Override
	public String getTitle() {
		return "Preferences";
	}

	@Override
	public void render(long windowHandle, NkContext ctx) {
		super.render(windowHandle, ctx);

        int width = 470;
        int height = Math.min(2 * (420 + (appSettings.isUseFixedNodes() ? 0 : 120)),
                Math.max(240, getWindowHeight() - nuklearModel.getMenuBarHeight() - remframework.helpers.Constants.STATUS_BAR_HEIGHT - 8));
        createLayout(ctx, 0, nuklearModel.getMenuBarHeight() + 4, width, height);
	}

	@Override
	protected void layout(NkContext ctx, int x, int y, int width, int height) {
		try (MemoryStack stack = MemoryStack.stackPush()) {

            float leftCol = 0.6f;
            float rightCol = 0.4f;
            List<LatticeDescriptor> descriptors = latticeManager.descriptors();
            String[] labels = descriptors.stream().map(LatticeDescriptor::uiLabel).toArray(String[]::new);
            LatticeDescriptor selected = latticeManager.selected();
            String chosen = nk_label_combo_options(ctx, stack, " Lattice Model:", labels,
                    selected.uiLabel(), 0.35f, 0.65f);
            if (!chosen.equals(selected.uiLabel())) {
                LatticeDescriptor candidate = descriptors.get(java.util.Arrays.asList(labels).indexOf(chosen));
                boolean wasSimulating = project.getRemModel().isSimulating();
                project.getRemModel().setSimulating(false);
                var requestedDimensions=new org.joml.Vector3i();project.getDimension(requestedDimensions);
                if (latticeManager.select(candidate.identifier(),requestedDimensions)) {
                    project.getRemModel().setCustomStatus("Loading " + candidate.displayName());
                    appSettings.requestLatticeRebuild();
                    System.out.println("Runtime Lattice Model change: " + candidate.uiLabel()
                            + (wasSimulating ? " (simulation paused)" : ""));
                } else {
                    project.getRemModel().setCustomStatus(latticeManager.lastWarning());
                    nuklearModel.showErrorDialog("Cannot Load Lattice Model",
                            latticeManager.lastWarning() + "\n\n"
                                    + candidate.messages().stream()
                                    .map(m -> m.severity() + ": " + m.message())
                                    .reduce((a, b) -> a + "\n" + b).orElse(""));
                    if(!latticeManager.selected().identifier().equals(selected.identifier()))appSettings.requestLatticeRebuild();
                }
            }
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightEdit, 1);
            if (nk_button_label(ctx, "Refresh Lattice Models")) {
                String before = latticeManager.selected().identifier();
                latticeManager.refresh();
                if (!before.equals(latticeManager.selected().identifier())) {
                    project.getRemModel().setSimulating(false);
                    appSettings.requestLatticeRebuild();
                    nuklearModel.showErrorDialog("Lattice Model Refresh",
                            latticeManager.lastWarning());
                }
                project.getRemModel().setCustomStatus("Lattice model list refreshed");
            }
            if (!latticeManager.lastWarning().isBlank()) {
                Nuklear.nk_layout_row_dynamic(ctx, rowHeightEdit * 2, 1);
                Nuklear.nk_label_wrap(ctx, "Warning: " + latticeManager.lastWarning());
            }

            nk_spacer(ctx, spacer1, 1);

            if(selected.model()!=null && selected.model().mechanicsOnly() && lattice.isMechanicsOnly()) {
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                Nuklear.nk_label(ctx,"Experimental Node v0.1 /4; local REM bridge",Nuklear.NK_TEXT_LEFT);
                boolean localTransport=nk_check_label(ctx," Enable local Identity transport:",lattice.isDiamondTransportEnabled(),leftCol,rightCol);
                if(localTransport!=lattice.isDiamondTransportEnabled())lattice.setDiamondTransportEnabled(localTransport);
                if(nk_button_label(ctx,"Open Isotropy / transport measurements"))nuklearModel.showFrame(NuklearModel.Frame.isotropy);
                boolean pressure=nk_check_label(ctx," Enable occupancy pressure:",lattice.isDiamondPressureEnabled(),leftCol,rightCol);
                if(pressure!=lattice.isDiamondPressureEnabled())lattice.setDiamondPressureEnabled(pressure);
                if(lattice.isDiamondPressureEnabled()) {
                    var selectedOccupancy=project.getRemModel().getSelectedOccupancy();
                    if(selectedOccupancy!=null) {
                        String value;
                        synchronized(selectedOccupancy) {
                            var p=selectedOccupancy.getPressure();
                            value=String.format(java.util.Locale.ROOT,"(%.3g, %.3g, %.3g)",p.x,p.y,p.z);
                        }
                        nk_label_value(ctx," Selected pressure",value,leftCol,rightCol);
                    } else {
                        Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                        Nuklear.nk_label(ctx,"Select an Identity to inspect its pressure.",Nuklear.NK_TEXT_LEFT);
                    }
                }
                boolean identityExcitation=nk_check_label(ctx," Enable Identity excitation:",lattice.isDiamondIdentityExcitationEnabled(),leftCol,rightCol);
                if(identityExcitation!=lattice.isDiamondIdentityExcitationEnabled())lattice.setDiamondIdentityExcitationEnabled(identityExcitation);
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit*2,1);
                Nuklear.nk_label_wrap(ctx,"Pauses on change. Configure an Identity, then Run. Local transport enables pressure. Both pause on change; configure an Identity, then Run.");
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                Nuklear.nk_label(ctx,"Fixed incomplete boundary; k="+project.getConstantFrequency()+", gamma="+project.getConstantElectricFactor(),Nuklear.NK_TEXT_LEFT);
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                packetWavelength=nk_label_edit(ctx,stack," Wavelength (bond lengths):",packetWavelengthBuffer,packetWavelength,leftCol,rightCol);
                packetAmplitude=nk_label_edit(ctx,stack," Amplitude (bond lengths):",packetAmplitudeBuffer,packetAmplitude,appSettings.getFormatScientific9(),leftCol,rightCol);
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                fixedPacketGeometry=nk_check_label(ctx," Fixed source / receivers:",fixedPacketGeometry,leftCol,rightCol);
                if(fixedPacketGeometry) {
                    Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                    Nuklear.nk_label(ctx,"Support 12; receiver radii 16 / 20; target 1%",Nuklear.NK_TEXT_LEFT);
                }
                if(nk_button_label(ctx,"Long wave + Run (reset)")) {
                    var lattice=remframework.opengl.model.Lattice.getInstance();
                    if(fixedPacketGeometry)lattice.getDisturbance().requestFixedGeometry(packetWavelength,packetAmplitude,8,0);
                    else lattice.getDisturbance().request(packetWavelength,packetAmplitude);
                    project.getRemModel().startSimulation();
                }
                var experiment=remframework.opengl.model.Lattice.getInstance().getDisturbance();
                nk_label_value(ctx," Packet steps:",experiment.steps()+" / "+experiment.safeSteps(),leftCol,rightCol);
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit*3,1);
                Nuklear.nk_label_wrap(ctx,experiment.summary());
                if(!experiment.thresholdComparisons().isEmpty())
                    nk_label_value(ctx," Results:",experiment.resultsLabel(),leftCol,rightCol);
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit*3,1);
                Nuklear.nk_label_wrap(ctx,experiment.twoRadiusSummary());
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit*4,1);
                Nuklear.nk_label_wrap(ctx,experiment.troughSummary());
                if(!experiment.running() && !experiment.pairedMeasurements().isEmpty()) {
                    diagnosticDirection=Math.max(0,Math.min(25,nk_label_edit(ctx,stack," Receiver direction (0..25):",diagnosticDirectionBuffer,diagnosticDirection,leftCol,rightCol)));
                    Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit*3,1);
                    Nuklear.nk_label_wrap(ctx,experiment.directionDiagnostic(diagnosticDirection));
                    double[] inner=experiment.innerTrace(diagnosticDirection),outer=experiment.outerTrace(diagnosticDirection);
                    nk_label_value(ctx," Recorded steps:",(inner.length-1)+" inner / "+(outer.length-1)+" outer",leftCol,rightCol);
                    nk_label_value(ctx," Pair cutoff step:",""+(Math.min(inner.length,outer.length)-1),leftCol,rightCol);
                    Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                    Nuklear.nk_label(ctx,"Inner: cyan; outer: orange; common signed scale",Nuklear.NK_TEXT_LEFT);
                    drawReceiverTraces(ctx,inner,outer);
                }
                for(var comparison:experiment.thresholdComparisons())
                    nk_label_value(ctx," Threshold "+comparison.label(),comparison.value(),leftCol,rightCol);
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightEdit,1);
                if(nk_button_label(ctx,"Pulse + Run")) {
                    remframework.opengl.model.Lattice.getInstance().requestMechanicalPulse();
                    project.getRemModel().startSimulation();
                }
            }
            appSettings.setNumThreads(nk_label_edit(ctx, stack, " CPU Threads:", bufNumThreads, appSettings.getNumThreads(), leftCol,
                                                    rightCol));

            nk_spacer(ctx, spacer1, 1);

            Settings.RenderingMethod renderingMethod = project.settings.getRenderingMethod();
            String chosenRenderingMethod = nk_label_combo_options(ctx, stack, " Rendering Method:",
                    Settings.RenderingMethod.getValues(), renderingMethod.getLabel(), leftCol, rightCol);
            if (!chosenRenderingMethod.equals(renderingMethod.getLabel())) {
                project.settings.setRenderingMethod(
                        Settings.RenderingMethod.fromLabel(chosenRenderingMethod));
            }

            nk_spacer(ctx, spacer1, 1);

            lattice.setNumEdgeAbsorptionLayers(nk_label_edit(
                    ctx, stack, " Edge Absorption Layers:", bufEdgeAbsorptionLayers,
                    lattice.getNumEdgeAbsorptionLayers(), leftCol, rightCol));

            nk_spacer(ctx, spacer1, 1);
			
			appSettings.setUseFixedNodes(nk_check_label(ctx, " Use Fixed Nodes:", appSettings.isUseFixedNodes(), leftCol, rightCol));
			nk_spacer(ctx, spacer1, 1);
			
			if (!appSettings.isUseFixedNodes()) {
				appSettings.setAbsorption(nk_label_edit(ctx, stack, " Absorption:",
                                                        bufAbsorptionFixedNodes, appSettings.getAbsorption(), leftCol, rightCol));
				appSettings.setAbsorption(nk_slider(ctx, 0.0, appSettings.getAbsorption(), 1.0, 0.01));
				nk_spacer(ctx, spacer1, 1);

				appSettings.setUseLoop(nk_check_label(ctx, " Use Loop:", appSettings.isUseLoop(), leftCol, rightCol));
				nk_spacer(ctx, spacer1, 1);
			}
			
			project.setGlSwapInterval(nk_label_edit(ctx, stack, " GLSwap Interval:", bufSwapInterval, project.getGlSwapInterval(), leftCol,
                                                    rightCol));
			nk_spacer(ctx, spacer1, 1);
			
			rainModel.setForce(nk_label_edit(ctx, stack, " Force:", bufForce, rainModel.getForce(), leftCol, rightCol));
			rainModel.setForce(nk_slider(ctx, -2500, rainModel.getForce(), 2500, 0.1));
			nk_spacer(ctx, spacer1, 1);

			Nuklear.nk_layout_row_end(ctx);
		}
	}

    private static void drawReceiverTraces(NkContext ctx,double[] inner,double[] outer) {
        Nuklear.nk_layout_row_dynamic(ctx,150,1);
        double magnitude=1e-15;
        for(double[] trace:new double[][]{inner,outer})
            for(double value:trace)magnitude=Math.max(magnitude,Math.abs(value));
        int count=Math.max(2,Math.max(inner.length,outer.length));
        try(var stack=MemoryStack.stackPush()) {
            var innerColour=org.lwjgl.nuklear.NkColor.malloc(stack).set((byte)70,(byte)210,(byte)255,(byte)255);
            var outerColour=org.lwjgl.nuklear.NkColor.malloc(stack).set((byte)255,(byte)180,(byte)70,(byte)255);
            // Native charts consume a widget and preserve the Preferences window's scrolling and clipping.
            if(Nuklear.nk_chart_begin_colored(ctx,Nuklear.NK_CHART_LINES,innerColour,innerColour,count,-1.1f,1.1f)) {
                Nuklear.nk_chart_add_slot_colored(ctx,Nuklear.NK_CHART_LINES,outerColour,outerColour,count,-1.1f,1.1f);
                // Both slots use the same time and signed amplitude scales; neither trace is extended past its cutoff.
                for(double value:inner)Nuklear.nk_chart_push_slot(ctx,(float)(value/magnitude),0);
                for(double value:outer)Nuklear.nk_chart_push_slot(ctx,(float)(value/magnitude),1);
                Nuklear.nk_chart_end(ctx);
            }
        }
    }
}