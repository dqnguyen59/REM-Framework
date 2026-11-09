package remframework.gui.view.frames;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.NkCommandBuffer;
import org.lwjgl.nuklear.NkRect;
import org.lwjgl.nuklear.NkVec2;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.gui.model.Theme;
import remframework.opengl.model.PassiveObserver;
import remframework.opengl.model.InterpolatedProbe;
import remframework.opengl.model.Probe;
import remframework.opengl.model.ProbeArrivalMeasurement;
import remframework.opengl.model.ProbeShellArrivalReport;
import remframework.opengl.model.ProbeDefinition;
import remframework.opengl.model.ProbeDetectorType;
import remframework.opengl.model.ProbeComparison;
import remframework.opengl.model.Scene;
import remframework.opengl.model.TransverseIdentitySurvey;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.Node;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.Project;

import java.util.List;
import java.util.Locale;

/** Propagation dashboard for mechanics, with the original inspector for other models. */
public final class FrameIsotropy extends AbstractFrame {

    static final String HISTORY_BUTTON_LABEL = "▥";

    private static final int DEFAULT_WIDTH = 720;
    private static final int DEFAULT_HEIGHT = 720;
    private static final int MIN_WIDTH = 520;
    private static final int MIN_HEIGHT = 420;
    private static final int INSPECTOR_ROW_HEIGHT = 22;

    private final Project project = Project.getInstance();
    private boolean showSource = true;
    private boolean showNeighbors = true;
    private boolean showVectors = true;
    private boolean showGradient = true;
    private int selectedInterpolationSource;
    private int selectedTab;
    private int selectedArrivalShell = -1;
    private double positionThresholdExponent = -12.0;
    private double gradientThresholdExponent = -12.0;
    private double speedSpreadTolerance = ProbeArrivalMeasurement.DEFAULT_SPEED_SPREAD_TOLERANCE_PERCENT;
    private final StringBuffer toleranceBuffer = new StringBuffer();
    private double controlledWavelength=8;
    private final StringBuffer wavelengthBuffer=new StringBuffer();
    private final StringBuffer maximumUpdatesBuffer=new StringBuffer();
    private final StringBuffer amplitudeBuffer=new StringBuffer();
    private final StringBuffer sweepPeriodBuffer=new StringBuffer();
    private final StringBuffer sweepTurnsBuffer=new StringBuffer();
    private final StringBuffer sweepCyclesBuffer=new StringBuffer();
    private String shellReportExportStatus="";
    private boolean showShellArrivalTimes;
    private boolean showRawThresholdReport;
    private boolean showReversalTiming;
    private boolean showCycleTiming = true;
    private boolean inspectorPositionedForSession;
    private boolean diamondViewInitialized;
    private boolean surveyInitialized;
    private boolean surveyLive;
    private boolean surveyGeometryView;
    private boolean surveyDistanceFromLatticeCenter;
    private int surveyGeometryShell=-1;
    private int surveyGeometryOrientation;
    private int surveyGeometryRay;
    private int surveyInspectionNode;
    private remframework.opengl.model.SurveyLineInspection.Selection surveyInspection;
    private java.util.concurrent.CompletableFuture<remframework.opengl.model.SurveyLineInspection.Selection> surveyInspectionLoad;
    private Object[] surveyInspectionNodes;
    private String surveyInspectionKey="";
    private String surveyInspectionError="";
    private String surveyOverlayKey="";
    private boolean surveyShortestPath;
    private boolean surveyFollowStraightLine=true;
    private remframework.opengl.model.SurveyLineInspection.PathFinder surveyPathFinder;
    private java.util.concurrent.CompletableFuture<remframework.opengl.model.SurveyLineInspection.PathFinder> surveyPathLoad;
    private Object[] surveyPathNodes;
    private Vector3d surveyPathSource;
    private String surveyPathError="";
    private static final org.lwjgl.nuklear.NkColor SURVEY_ORANGE=Theme.createColor(255,150,35,255);
    private boolean surveyWasRunning;
    private int surveyMetric;
    private int surveyShell;
    private String surveyMessage="";

    public FrameIsotropy(NuklearModel model) {
        super(model);
        resizeCapability = ResizeCapability.enabled(MIN_WIDTH, MIN_HEIGHT, 12);
        windowOptions &= ~Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }

    @Override
    public String getTitle() {
        return "Isotropy";
    }

    @Override
    public void close() {
        project.scene.clearSurveyLine();
        project.scene.exitNeighborhoodInspection();
        super.close();
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);
        var savedRect = appSettings.getFrameRectMap().get(getTitle());
        int width = savedRect == null
                ? DEFAULT_WIDTH
                : Math.max(MIN_WIDTH, Math.round(savedRect.w()));
        int height = savedRect == null
                ? DEFAULT_HEIGHT
                : Math.max(MIN_HEIGHT, Math.round(savedRect.h()));
        if (savedRect != null) {
            savedRect.w(width);
            savedRect.h(height);
        }
        boolean inspectionActive = project.scene.isNeighborhoodInspectionActive()
                || project.scene.isNeighborhoodInspectionTransitionActive();
        if (!inspectionActive) {
            inspectorPositionedForSession = false;
        } else if (!inspectorPositionedForSession) {
            if (savedRect == null) {
                savedRect = NkRect.create();
                appSettings.getFrameRectMap().put(getTitle(), savedRect);
            }
            positionNearInspectionCluster(savedRect, width, height);
            inspectorPositionedForSession = true;
        }
        createLayout(ctx, 40, nuklearModel.getMenuBarHeight() + 50, width, height);
    }

    @Override
    protected void layout(NkContext ctx, int x, int y, int width, int height) {
        if(project.scene.getLattice().isMechanicsOnly()) {
            transverseSurveySection(ctx);
            return;
        } else if(diamondViewInitialized) {diamondViewInitialized=false;selectedTab=0;}
        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 2);
        boolean hasGeneratedProbes = project.getProbeDefinitions().stream()
                .anyMatch(ProbeDefinition::isGenerated);
        if (nk_button_label(ctx, hasGeneratedProbes ? "Edit Probes..." : "Add Probes...")) {
            nuklearModel.showFrame(NuklearModel.Frame.addProbes);
        }
        Nuklear.nk_label(ctx, "Probe center is the lattice geometric center", Nuklear.NK_TEXT_LEFT);

        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        if (Nuklear.nk_button_label(ctx, "Gradient visualization")) selectedTab = 0;
        if (Nuklear.nk_button_label(ctx, "Calibration")) selectedTab = 1;
        if (Nuklear.nk_button_label(ctx, "Gradient Imbalance Comparison")) selectedTab = 2;
        if (Nuklear.nk_button_label(ctx, "Propagation Arrival")) selectedTab = 3;

        boolean inspectionActive = project.scene.isNeighborhoodInspectionActive()
                || project.scene.isNeighborhoodInspectionTransitionActive();
        if (inspectionActive && selectedTab == 0) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
            Nuklear.nk_label(ctx, "Neighborhood Inspector", Nuklear.NK_TEXT_LEFT);
            if (Nuklear.nk_button_label(ctx, "Exit Inspector (Esc)")) {
                project.scene.exitNeighborhoodInspection();
            }
        }

        NkVec2 contentSize = NkVec2.create();
        Nuklear.nk_window_get_content_region_size(ctx, contentSize);
        int fixedHeight = rowHeight + rowHeightFor(ctx)
                + (inspectionActive && selectedTab == 0 ? rowHeightFor(ctx) : 0);
        int tabHeight = Math.max(rowHeight,
                                Math.round(contentSize.y()) - fixedHeight);
        Nuklear.nk_layout_row_dynamic(ctx, tabHeight, 1);
        String tabContentTitle = switch (selectedTab) {
            case 1 -> "Calibration content";
            case 2 -> "Gradient Imbalance Comparison content";
            case 3 -> "Propagation Arrival content";
            default -> "Gradient visualization content";
        };
        if (!Nuklear.nk_group_begin(ctx, tabContentTitle,
                                    Nuklear.NK_WINDOW_SCROLL_AUTO_HIDE)) return;

        if (selectedTab == 1) {
            calibrationSection(ctx);
            Nuklear.nk_group_end(ctx);
            return;
        }
        if (selectedTab == 2) {
            comparisonSection(ctx);
            Nuklear.nk_group_end(ctx);
            return;
        }
        if (selectedTab == 3) {
            arrivalSection(ctx);
            Nuklear.nk_group_end(ctx);
            return;
        }

        section(ctx, "Probe arrows");
        project.scene.setShowGradientArrows(nk_check_label(ctx, "Show probe arrows",
                                                            project.scene.isShowGradientArrows(), 0.7, 0.3));
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
        Nuklear.nk_label(ctx,"Arrow quantity",Nuklear.NK_TEXT_LEFT);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var quantities=remframework.opengl.model.ProbeArrowQuantity.values();
            var options=java.util.Arrays.stream(quantities).map(remframework.opengl.model.ProbeArrowQuantity::label).toArray(String[]::new);
            var selected=nk_combo_options(ctx,stack,options,project.scene.getProbeArrowQuantity().label());
            for(var quantity:quantities)if(quantity.label().equals(selected))project.scene.setProbeArrowQuantity(quantity);
        }
        scalar(ctx, "Arrow scale exponent",
               Integer.toString((int) project.scene.getGradientArrowScaleExponent()));
        project.scene.setGradientArrowScaleExponent(
                Math.rint(nk_slider(ctx, 0.0, project.scene.getGradientArrowScaleExponent(), 120.0, 1)));
        scalar(ctx, "Visual scale", "10^" + (int) project.scene.getGradientArrowScaleExponent()
                + " = " + format(project.scene.getGradientArrowScale()));

        PassiveObserver probe = project.scene.getSelectedProbe();
        section(ctx, "Selected passive probe");
        if (probe == null) {
            scalar(ctx, "Probe", "None");
        } else {
            var arrowVector=project.scene.getProbeArrowQuantity().read(probe,new Vector3d());
            scalar(ctx,"Arrow vector (local XYZ)",String.format(Locale.ROOT,"%.5g, %.5g, %.5g",arrowVector.x,arrowVector.y,arrowVector.z));
            scalar(ctx,"Arrow vector magnitude",format(arrowVector.length()));
            Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 3);
            Nuklear.nk_layout_row_push(ctx, 0.62f);
            Nuklear.nk_label(ctx, "Probe", Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.19f);
            if (Nuklear.nk_button_label(ctx, HISTORY_BUTTON_LABEL))
                nuklearModel.showFrame(NuklearModel.Frame.occupancyHistory);
            Nuklear.nk_layout_row_push(ctx, 0.19f);
            if (Nuklear.nk_button_label(ctx, "Inspect Neighborhood"))
                project.scene.enterNeighborhoodInspection();
            Nuklear.nk_layout_row_end(ctx);
            scalar(ctx, "Probe ID", probe.getId());
            scalar(ctx, "Detector type", probe.getDetectorType().name());
            ProbeDefinition.Placement placement = probe.getDefinition().getPlacement();
            if (placement != null) {
                scalar(ctx, "Represented targets", placement.targets().stream()
                        .map(target -> "Shell " + (target.shell() + 1) + " " + target.rayPlane())
                        .reduce((left, right) -> left + ", " + right).orElse(""));
                scalar(ctx, "Requested radii", placement.targets().stream()
                        .map(target -> Integer.toString(target.requestedRadius()))
                        .reduce((left, right) -> left + ", " + right).orElse(""));
                if (probe.getDetectorType() == ProbeDetectorType.RAW_NODE) {
                    ProbeDefinition.Target firstTarget = placement.targets().getFirst();
                    scalar(ctx, "Realized radius", String.format(Locale.ROOT, "%.6f",
                                                                  firstTarget.realizedRadius()));
                    scalar(ctx, "Graph distance (diagnostic)",
                           Integer.toString(firstTarget.graphDistance()));
                } else {
                    ProbeDefinition.Target target = placement.targets().getFirst();
                    vector(ctx, "Exact requested coordinate", target.requestedCoordinate());
                    vector(ctx, "Requested direction", target.requestedDirection());
                    for (int sourceIndex = 0;
                         sourceIndex < probe.getDefinition().getInterpolationSources().size();
                         sourceIndex++) {
                        ProbeDefinition.InterpolationSource source = probe.getDefinition()
                                .getInterpolationSources().get(sourceIndex);
                        scalar(ctx, "Interpolation source " + (sourceIndex + 1),
                               String.format(Locale.ROOT, "(%d, %d, %d), weight %.9f",
                                       source.nodeIndex().x, source.nodeIndex().y,
                                       source.nodeIndex().z, source.weight()));
                    }
                }
            }
            if (probe instanceof InterpolatedProbe interpolatedProbe) {
                interpolationSourcesSection(ctx, interpolatedProbe);
            }
            PassiveObserver.Observation observation = probe.getObservation();
            if (probe instanceof Probe rawProbe) {
                Node node = rawProbe.getNode();
                scalar(ctx, "Measurement", "Raw node measurement");
                vector(ctx, "Node coordinate", rawProbe.getNodeCoordinate());
                vector(ctx, "Node position", node.getPosition());
                vector(ctx, "Stable gradient imbalance", probe.getStableGradientImbalance());
                scalar(ctx, "Gradient strength |g|", format(probe.getStableGradientImbalance().length()));
                vector(ctx, "Acceleration", node.getAcceleration());
                vector(ctx, "Node Velocity", node.getVelocity());
            } else if (probe instanceof InterpolatedProbe interpolatedProbe) {
                scalar(ctx, "Measurement", "Interpolated estimate — not a node measurement");
                vector(ctx, "Interpolated live position", observation.position());
                vector(ctx, "Weighted stable gradient imbalance", probe.getStableGradientImbalance());
                scalar(ctx, "Gradient strength |g|", format(probe.getStableGradientImbalance().length()));
                vector(ctx, "Interpolated acceleration", observation.acceleration());
                vector(ctx, "Interpolated velocity", observation.velocity());
                interpolationSourceDetails(ctx, interpolatedProbe);
            }
            inspectorSection(ctx, probe);
            for (int index = 0; index < probe.getOccupancies().size(); index++) {
                Occupancy occupancy = probe.getOccupancies().get(index);
                double weight = probe.getDefinition().getDetectorType() == ProbeDetectorType.RAW_NODE
                        ? 1.0
                        : probe.getDefinition().getInterpolationSources().get(index).weight();
                scalar(ctx, "Source Occupancy " + (index + 1),
                       String.format(Locale.ROOT, "weight %.9f", weight));
                vector(ctx, "Source stable gradient " + (index + 1),
                       occupancy.getGradientImbalance());
                scalar(ctx, sourceSamplesLabel(index),
                       Integer.toString(occupancy.getSamplesPerCycle()));
                scalar(ctx, "Source cycle completed " + (index + 1),
                       Boolean.toString(occupancy.wasCycleCompletedThisUpdate()));
            }
            naVector(ctx, "Transport Momentum");
            naVector(ctx, "Pressure");
            scalar(ctx, "Cycle state", "N/A (Occupancy only)");
        }

        section(ctx, "Receiver Occupancy reference");
        Occupancy receiver = project.occupancies.size() > 1 ? project.occupancies.get(1) : null;
        if (receiver == null) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
            Nuklear.nk_label(ctx, "Receiver unavailable", Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_group_end(ctx);
            return;
        }

        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 4);
        Nuklear.nk_label(ctx, "Value", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, "X", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "Y", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "Z", Nuklear.NK_TEXT_RIGHT);

        scalar(ctx, "Identity", receiver.getIdentity() == null ? "" : receiver.getIdentity().getName());
        vector(ctx, "Current node coordinate", receiver.getNodeIndex());
        vector(ctx, "Gradient Imbalance", receiver.getGradientImbalance());
        vector(ctx, "Cycle gradients: G−", receiver.getLastMaxNegativeGradient());
        vector(ctx, "Cycle gradients: G+", receiver.getLastMaxPositiveGradient());
        vector(ctx, "Cycle gradients: G", receiver.getGradientImbalance());
        vector(ctx, "Acceleration", receiver.getAcceleration());
        vector(ctx, "Transport Momentum", receiver.getTransportMomentum());
        vector(ctx, "Pressure", receiver.getPressure());
        vector(ctx, "Node Velocity", receiver.getNode() == null ? new Vector3d() : receiver.getNode().getVelocity());
        scalar(ctx, "Samples in current cycle", Integer.toString(receiver.getCycleSampleCountForAudit()));
        scalar(ctx, "Samples in last completed cycle", Integer.toString(receiver.getSamplesPerCycleForAudit()));
        scalar(ctx, "Cycle completed", Boolean.toString(receiver.wasCycleCompletedThisUpdate()));
        Nuklear.nk_group_end(ctx);
    }

    /** Focused mechanics dashboard; earlier orbit/threshold experiments are no longer shown here. */
    private void transverseSurveySection(NkContext ctx) {
        var survey=project.scene.getTransverseIdentitySurvey();
        var root=java.nio.file.Path.of("measurements","diamond-shells");
        if(!surveyInitialized) {
            surveyInitialized=true;Nuklear.nk_window_set_scroll(ctx,0,0);
            if(survey.view().results().isEmpty()&&!survey.running())try{survey.loadLatestResults(root);}
            catch(java.io.IOException error){surveyMessage=error.getMessage();}
        }
        var view=survey.view();
        if(view.running())project.scene.clearSurveyLine();
        if(view.running()&&!surveyWasRunning)surveyLive=true;
        if(!view.running()&&surveyWasRunning)surveyLive=false;
        surveyWasRunning=view.running();
        section(ctx,"Transverse propagation: direction and distance");
        Nuklear.nk_layout_row_dynamic(ctx,44,1);
        Nuklear.nk_label_wrap(ctx,"The same Identity pulse excites 26 directions. Receivers measure displacement in each perpendicular plane. Results accumulate after each orientation.");
        Nuklear.nk_layout_row_dynamic(ctx,26,3);
        if(nk_button_label(ctx,"Run survey")&&!survey.running()){survey.request();surveyMessage="";}
        if(nk_button_label(ctx,"Load latest results")&&!survey.running())try{survey.loadLatestResults(root);surveyMessage="";surveyLive=false;}
        catch(java.io.IOException error){surveyMessage=error.getMessage();}
        if(nk_button_label(ctx,"Stop")&&survey.running())survey.requestCancel();
        scalar(ctx,"Survey",survey.status());
        scalar(ctx,"Current lattice / absorber",project.getDimensionX()+" x "+project.getDimensionY()+" x "+project.getDimensionZ()
                +" / "+(project.scene.getLattice().isDiamondSpongeEnabled()?"ON":"OFF"));
        if(!survey.running()) {
            var lattice=project.scene.getLattice();
            lattice.setDiamondSpongeEnabled(nk_check_label(ctx,"Edge absorption for next survey",lattice.isDiamondSpongeEnabled(),.8,.2));
            drawExperimentPreset(ctx);
        }
        if(!surveyMessage.isEmpty()){Nuklear.nk_layout_row_dynamic(ctx,44,1);Nuklear.nk_label_wrap(ctx,surveyMessage);}
        view=survey.view();
        if(view.limit()>0) {
            scalar(ctx,"Recorded setup",view.shells()+" shells; "+view.rays()+" rays/plane; absorber "+(view.absorber()?"ON":"OFF")
                    +"; up to "+view.limit()+" updates/orientation");
            scalar(ctx,"Pulse",view.cycles()+" cycles; period "+view.period()+" updates; force amplitude "+format(view.amplitude()));
            Nuklear.nk_layout_row_dynamic(ctx,44,1);
            Nuklear.nk_label_wrap(ctx,"New surveys wait for every receiver's tail below 1% of its peak for quietUpdates consecutive samples (preset setting; defaults to pulse duration, minimum 8 updates), within the boundary-safe recording limit.");
            surveyProgress(ctx,view);
        }
        Nuklear.nk_layout_row_dynamic(ctx,26,3);
        if(nk_button_label(ctx,"Distance results")){surveyLive=false;surveyGeometryView=false;}
        if(nk_button_label(ctx,"Live pulse")){surveyLive=true;surveyGeometryView=false;}
        if(nk_button_label(ctx,"Distances by direction")){surveyLive=false;surveyGeometryView=true;}
        if(surveyLive) {drawSurveyLive(ctx,view);return;}
        if(surveyGeometryView){drawSurveyDirectionalDistances(ctx,view);return;}
        if(view.results().isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx,48,1);
            Nuklear.nk_label_wrap(ctx,"Run a survey to see arrival differences and amplitude spread versus distance. The live view shows amplified receiver signals during each pulse.");return;
        }
        scalar(ctx,"Compared orientations",view.completed()+" / 26; pooled over all saved perpendicular rays");
        Nuklear.nk_layout_row_dynamic(ctx,26,3);
        if(nk_button_label(ctx,"Arrival difference"))surveyMetric=0;
        if(nk_button_label(ctx,"Arrival spread (%)"))surveyMetric=1;
        if(nk_button_label(ctx,"Amplitude spread (%)"))surveyMetric=2;
        Nuklear.nk_layout_row_dynamic(ctx,26,2);
        if(nk_button_label(ctx,"Distance difference"))surveyMetric=3;
        if(nk_button_label(ctx,"Bond path difference"))surveyMetric=4;
        var title=switch(surveyMetric){case 1->"Arrival spread (%) vs radius (bonds)";case 2->"Peak amplitude spread (%) vs radius (bonds)";case 3->"Straight-line distance max - min (bonds) vs radius";case 4->"Weighted shortest-path max - min (bond hops) vs radius";default->"Arrival max - min (updates) vs radius (bonds)";};
        section(ctx,title);drawSurveyDistanceChart(ctx,view.results());
        Nuklear.nk_layout_row_dynamic(ctx,44,1);
        Nuklear.nk_label_wrap(ctx,surveyMetric==4?"Shortest paths count connected bonds from the source to sampling nodes. Weighted means use the probe interpolation weights; they do not represent a unique wave trajectory.":surveyMetric==3?"Straight-line distance to the weighted probe coordinate at rest. Geometry is independent of pulse-tail completion. Tiny ranges near floating-point precision can be rounding.":
                "Filled dots: complete pulses. Open dots and dashed lines: pulse tails cut off. Missing values stay blank. Charts start at zero.");
        var first=view.results().getFirst();var last=view.results().getLast();
        if(Double.isFinite(first.range())&&Double.isFinite(last.range()))scalar(ctx,"Near to far arrival difference",
                String.format(Locale.ROOT,"%.3f to %.3f updates%s",first.range(),last.range(),last.provisional()?" (far shell provisional)":""));
        Nuklear.nk_layout_row_dynamic(ctx,24,7);
        for(String label:List.of("Radius","Min t","Max t","Difference","Arrival %","Peak %","Full pulse"))Nuklear.nk_label(ctx,label,Nuklear.NK_TEXT_LEFT);
        for(var row:view.results()) {
            Nuklear.nk_layout_row_dynamic(ctx,24,7);
            for(String value:List.of(String.format(Locale.ROOT,"%.0f",row.radius()),surveyNumber(row.minimum()),surveyNumber(row.maximum()),
                    surveyNumber(row.range()),surveyNumber(row.arrivalSpread()),surveyNumber(row.peakSpread()),row.complete()+"/"+row.total()))
                Nuklear.nk_label(ctx,value,Nuklear.NK_TEXT_LEFT);
        }
        section(ctx,"Connected bond paths from the center (hop counts)");
        Nuklear.nk_layout_row_dynamic(ctx,24,7);
        for(String label:List.of("Radius","Min hops","Max hops","Hop diff","Mean min","Mean max","Mean diff"))Nuklear.nk_label(ctx,label,Nuklear.NK_TEXT_LEFT);
        for(var row:view.results()) {
            Nuklear.nk_layout_row_dynamic(ctx,24,7);
            for(double value:new double[]{row.radius(),row.minimumHops(),row.maximumHops(),row.hopRange(),row.weightedHopMinimum(),row.weightedHopMaximum(),row.weightedHopRange()})
                Nuklear.nk_label(ctx,surveyNumber(value),Nuklear.NK_TEXT_LEFT);
        }
        Nuklear.nk_layout_row_dynamic(ctx,60,1);
        Nuklear.nk_label_wrap(ctx,"Min/max hops cover the positive-weight sampling nodes. Mean min/max compare the weighted hop count of each virtual probe across directions. New surveys record this automatically; historical audits reconstruct geometry without changing saved arrivals.");
        section(ctx,"Source-to-probe distances at rest (bonds)");
        Nuklear.nk_layout_row_dynamic(ctx,24,7);
        for(String label:List.of("Shell radius","Target min","Target max","Target diff","Probe min","Probe max","Probe diff"))Nuklear.nk_label(ctx,label,Nuklear.NK_TEXT_LEFT);
        for(var row:view.results()) {
            Nuklear.nk_layout_row_dynamic(ctx,24,7);
            for(double value:new double[]{row.radius(),row.targetDistanceMinimum(),row.targetDistanceMaximum(),row.targetDistanceRange(),
                    row.distanceMinimum(),row.distanceMaximum(),row.distanceRange()})
                Nuklear.nk_label(ctx,Double.isFinite(value)?String.format(Locale.ROOT,"%.7g",value):"--",Nuklear.NK_TEXT_LEFT);
        }
        Nuklear.nk_layout_row_dynamic(ctx,60,1);
        Nuklear.nk_label_wrap(ctx,"Distance difference = maximum - minimum within each shell, across the tested directions. Targets have equal radii by construction. Probe distance uses the weighted equilibrium coordinate; it is not a shortest bond path or the mean distance to stencil nodes.");
        if(view.results().stream().anyMatch(r->r.distances()==0)) {
            Nuklear.nk_layout_row_dynamic(ctx,44,1);
            Nuklear.nk_label_wrap(ctx,"This saved run contains target radii only. Measured probe distances will be recorded by new surveys; unavailable values are shown as --.");
        }
        Nuklear.nk_layout_row_dynamic(ctx,44,1);
        Nuklear.nk_label_wrap(ctx,"Spread = 100 x (max - min) / |mean|. Arrival uses an interpolated half-peak crossing. A decreasing range describes these measurements; amplitude is measured separately.");
        scalar(ctx,"Saved results",java.nio.file.Path.of(view.location()).getFileName().toString());
    }

    private static String surveyNumber(double value){return Double.isFinite(value)?String.format(Locale.ROOT,"%.3f",value):"--";}

    private void drawSurveyDirectionalDistances(NkContext ctx,TransverseIdentitySurvey.ViewState view) {
        section(ctx,"Actual straight-line distances at rest");
        if(view.receiverDistances().isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx,60,1);
            Nuklear.nk_label_wrap(ctx,"No per-receiver distances are available for this result. New surveys save them; a historical geometry audit can supply them without rerunning the physics.");return;
        }
        surveyDistanceFromLatticeCenter=nk_check_label(ctx,"Measure from geometric lattice center",surveyDistanceFromLatticeCenter,.8,.2);
        java.util.function.ToDoubleFunction<TransverseIdentitySurvey.ReceiverDistance> measure=surveyDistanceFromLatticeCenter
                ?TransverseIdentitySurvey.ReceiverDistance::latticeCenterDistance:TransverseIdentitySurvey.ReceiverDistance::actualDistance;
        if(surveyGeometryShell<0||surveyGeometryShell>=view.shells())surveyGeometryShell=view.results().stream()
                .filter(r->!r.provisional()).mapToInt(TransverseIdentitySurvey.ShellSummary::shell).max().orElse(0);
        scalar(ctx,"Selected shell",Integer.toString(surveyGeometryShell+1));
        try(var stack=MemoryStack.stackPush()) {
            var index=stack.ints(surveyGeometryShell);Nuklear.nk_layout_row_dynamic(ctx,24,1);
            Nuklear.nk_slider_int(ctx,0,index,Math.max(0,view.shells()-1),1);surveyGeometryShell=index.get(0);
        }
        var shell=view.receiverDistances().stream().filter(r->r.shell()==surveyGeometryShell).toList();
        if(shell.isEmpty()){section(ctx,"This shell has no saved receiver geometry yet");return;}
        drawSurveyLineInspector(ctx,view);
        double radius=shell.getFirst().targetRadius();
        var all=shell.stream().mapToDouble(measure).filter(Double::isFinite).summaryStatistics();
        scalar(ctx,"Target radius from source node",String.format(Locale.ROOT,"%.12f bonds",radius));
        scalar(ctx,"Measurement center",surveyDistanceFromLatticeCenter?"Geometric lattice center":"Occupied source node");
        scalar(ctx,"All-direction max - min",all.getCount()==0?"Unavailable":String.format(Locale.ROOT,"%.6e bonds (%d receivers)",all.getMax()-all.getMin(),all.getCount()));
        Nuklear.nk_layout_row_dynamic(ctx,60,1);
        Nuklear.nk_label_wrap(ctx,"Each row below covers one excitation direction and its perpendicular receiver plane. Min/max distances are shown to 12 decimals; differences use unrounded values. Tiny differences near machine precision are numerical rounding.");
        Nuklear.nk_layout_row_dynamic(ctx,24,4);
        for(String label:List.of("Excitation XYZ","Actual min d","Actual max d","Max - min d"))Nuklear.nk_label(ctx,label,Nuklear.NK_TEXT_LEFT);
        var directions=remframework.geometry.discovery.research.diamond.DiamondDirectionalComparison.directions();
        for(int orientation=0;orientation<26;orientation++) {
            int chosen=orientation;var group=shell.stream().filter(r->r.orientation()==chosen).toList();
            var stats=group.stream().mapToDouble(measure).filter(Double::isFinite).summaryStatistics();
            var p=directions.get(orientation);
            Nuklear.nk_layout_row_dynamic(ctx,24,4);
            if(nk_button_label(ctx,String.format(Locale.ROOT,"%d: (%d,%d,%d)",orientation+1,(int)Math.signum(p.x),(int)Math.signum(p.y),(int)Math.signum(p.z))))surveyGeometryOrientation=orientation;
            for(String value:stats.getCount()==0?List.of("--","--","--"):List.of(String.format(Locale.ROOT,"%.12f",stats.getMin()),
                    String.format(Locale.ROOT,"%.12f",stats.getMax()),String.format(Locale.ROOT,"%.6e",stats.getMax()-stats.getMin())))
                Nuklear.nk_label(ctx,value,Nuklear.NK_TEXT_LEFT);
        }
        scalar(ctx,"Receiver details for excitation group",Integer.toString(surveyGeometryOrientation+1));
        try(var stack=MemoryStack.stackPush()) {
            var index=stack.ints(surveyGeometryOrientation);Nuklear.nk_layout_row_dynamic(ctx,24,1);
            Nuklear.nk_slider_int(ctx,0,index,25,1);surveyGeometryOrientation=index.get(0);
        }
        var group=shell.stream().filter(r->r.orientation()==surveyGeometryOrientation).sorted(java.util.Comparator.comparingInt(TransverseIdentitySurvey.ReceiverDistance::ray)).toList();
        section(ctx,"Each receiver: distance and signed difference from the source shell radius");
        for(int row=-1;row<group.size();row++) {
            String[] values;
            if(row<0)values=new String[]{"Ray","Receiver unit XYZ","Actual d","d - target"};
            else {
                var r=group.get(row);values=new String[]{Integer.toString(r.ray()+1),String.format(Locale.ROOT,"%.3f, %.3f, %.3f",r.nx(),r.ny(),r.nz()),
                        Double.isFinite(measure.applyAsDouble(r))?String.format(Locale.ROOT,"%.12f",measure.applyAsDouble(r)):"--",
                        Double.isFinite(measure.applyAsDouble(r))?String.format(Locale.ROOT,"%+.6e",measure.applyAsDouble(r)-r.targetRadius()):"--"};
            }
            Nuklear.nk_layout_row_begin(ctx,Nuklear.NK_DYNAMIC,24,4);
            float[] widths={.08f,.36f,.30f,.26f};
            for(int col=0;col<4;col++){
                Nuklear.nk_layout_row_push(ctx,widths[col]);
                if(row>=0&&col==0){if(nk_button_label(ctx,values[col]))surveyGeometryRay=group.get(row).ray();}
                else Nuklear.nk_label(ctx,values[col],Nuklear.NK_TEXT_LEFT);
            }
            Nuklear.nk_layout_row_end(ctx);
        }
        Nuklear.nk_layout_row_dynamic(ctx,60,1);
        boolean reconstructed=shell.stream().anyMatch(TransverseIdentitySurvey.ReceiverDistance::reconstructed);
        Nuklear.nk_label_wrap(ctx,(reconstructed?"Geometry reconstructed from the saved lattice and source; original arrivals are unchanged. ":"Geometry recorded during the survey. ")
                +"d = length(sum(weight x (sampling-node equilibrium position - selected center))). The source node can be offset from the geometric lattice center; the shell radius is defined from the source node.");
    }

    private void drawSurveyLineInspector(NkContext ctx,TransverseIdentitySurvey.ViewState view) {
        section(ctx,"Select a receiver line to highlight in the lattice and waveform");
        Nuklear.nk_layout_row_dynamic(ctx,24,1);
        if(nk_button_label(ctx,"Display settings (Settings > Probe / path display)"))nuklearModel.showFrame(NuklearModel.Frame.settings);
        Nuklear.nk_layout_row_dynamic(ctx,26,2);
        if(nk_button_label(ctx,"Radial lines"))surveyShortestPath=false;
        if(nk_button_label(ctx,"Shortest bond path"))surveyShortestPath=true;
        scalar(ctx,"Excitation group / receiver ray",(surveyGeometryOrientation+1)+" / "+(surveyGeometryRay+1));
        try(var stack=MemoryStack.stackPush()) {
            Nuklear.nk_layout_row_dynamic(ctx,24,2);
            var orientation=stack.ints(surveyGeometryOrientation);var ray=stack.ints(surveyGeometryRay);
            Nuklear.nk_slider_int(ctx,0,orientation,25,1);Nuklear.nk_slider_int(ctx,0,ray,Math.max(0,view.rays()-1),1);
            surveyGeometryOrientation=orientation.get(0);surveyGeometryRay=ray.get(0);
        }
        Nuklear.nk_layout_row_dynamic(ctx,24,2);
        if(nk_button_label(ctx,"Previous ray"))surveyGeometryRay=Math.floorMod(surveyGeometryRay-1,Math.max(1,view.rays()));
        if(nk_button_label(ctx,"Next ray"))surveyGeometryRay=(surveyGeometryRay+1)%Math.max(1,view.rays());
        if(view.running()) {
            project.scene.clearSurveyLine();surveyOverlayKey="";
            section(ctx,"Stop or finish the survey to inspect saved receiver lines");return;
        }
        var nodes=project.scene.getLattice().getNodeArray();
        if(nodes==null||nodes.length==0){project.scene.clearSurveyLine();section(ctx,"Load the saved lattice before highlighting receiver nodes");return;}
        String key=view.location()+":"+surveyGeometryOrientation+":"+surveyGeometryRay;
        if((!key.equals(surveyInspectionKey)||nodes!=surveyInspectionNodes)&&surveyInspectionLoad!=null&&!surveyInspectionLoad.isDone()) {
            project.scene.clearSurveyLine();section(ctx,"Loading receiver geometry... choose a line to inspect");return;
        }
        if(!key.equals(surveyInspectionKey)||nodes!=surveyInspectionNodes) {
            surveyInspectionKey=key;surveyInspectionNodes=nodes;surveyInspection=null;surveyInspectionError="";
            surveyInspectionNode=0;surveyOverlayKey="";project.scene.clearSurveyLine();
            int orientation=surveyGeometryOrientation,ray=surveyGeometryRay;
            surveyInspectionLoad=java.util.concurrent.CompletableFuture.supplyAsync(()-> {
                try{return remframework.opengl.model.SurveyLineInspection.load(view,orientation,ray,nodes);}
                catch(Exception ex){throw new java.util.concurrent.CompletionException(ex);}
            });
        }
        if(surveyInspectionLoad!=null&&surveyInspectionLoad.isDone()) {
            try{surveyInspection=surveyInspectionLoad.join();}
            catch(Exception ex){surveyInspectionError=ex.getCause()==null?ex.toString():ex.getCause().getMessage();}
            surveyInspectionLoad=null;
        }
        if(surveyInspection==null){section(ctx,surveyInspectionError.isEmpty()?"Loading this line's saved traces and sampling nodes...":surveyInspectionError);return;}
        var probe=surveyInspection.probes().get(surveyGeometryShell);
        var sources=probe.getInterpolationSources().stream().filter(s->s.weight()>0).toList();
        surveyInspectionNode=Math.min(surveyInspectionNode,sources.size()-1);
        List<org.joml.Vector3i> path=List.of();
        if(surveyShortestPath) {
            surveyFollowStraightLine=nk_check_label(ctx,"Follow straight line among shortest routes",surveyFollowStraightLine,.8,.2);
            if((nodes!=surveyPathNodes||!surveyInspection.source().equals(surveyPathSource))&&surveyPathLoad==null) {
                surveyPathNodes=nodes;surveyPathSource=new Vector3d(surveyInspection.source());surveyPathFinder=null;surveyPathError="";
                var origin=new Vector3d(surveyPathSource);
                surveyPathLoad=java.util.concurrent.CompletableFuture.supplyAsync(()->new remframework.opengl.model.SurveyLineInspection.PathFinder(nodes,origin));
            }
            if(surveyPathLoad!=null&&surveyPathLoad.isDone()) {
                try {surveyPathFinder=surveyPathLoad.join();}
                catch(Exception ex){surveyPathError=ex.getCause()==null?ex.toString():ex.getCause().getMessage();}
                surveyPathLoad=null;surveyOverlayKey="";
            }
            if(surveyPathFinder!=null&&nodes==surveyPathNodes&&surveyInspection.source().equals(surveyPathSource))
                path=surveyFollowStraightLine
                        ?surveyPathFinder.pathTo(sources.get(surveyInspectionNode).nodeIndex(),probe.getPlacement().targets().getFirst().requestedCoordinate())
                        :surveyPathFinder.pathTo(sources.get(surveyInspectionNode).nodeIndex());
            section(ctx,surveyPathLoad!=null?"Finding shortest connected paths...":!surveyPathError.isEmpty()?surveyPathError:
                    path.isEmpty()?"Selected sampling node is unreachable through movable nodes":"One shortest route to the selected sampling node (ties may have other routes)");
            if(!path.isEmpty()) {
                double length=0,minTurn=Double.POSITIVE_INFINITY,maxTurn=0;Vector3d previous=null,previousStep=null;
                for(var index:path) {
                    var position=project.scene.getLattice().getNodeByIndex(index).getEquilibriumPosition();
                    if(previous!=null) {
                        var step=new Vector3d(position).sub(previous);length+=step.length();
                        if(previousStep!=null){double turn=Math.toDegrees(previousStep.angle(step));minTurn=Math.min(minTurn,turn);maxTurn=Math.max(maxTurn,turn);}
                        previousStep=step;
                    }
                    previous=position;
                }
                scalar(ctx,"Connected bonds / path length",(path.size()-1)+" hops / "+surveyNumber(length)+" bonds");
                var endpoint=sources.get(surveyInspectionNode).nodeIndex();
                scalar(ctx,"Endpoint index XYZ",endpoint.x+", "+endpoint.y+", "+endpoint.z);
                if(Double.isFinite(minTurn))scalar(ctx,"Individual bond turns (min / max)",String.format(Locale.ROOT,"%.2f / %.2f degrees",minTurn,maxTurn));
            }
            Nuklear.nk_layout_row_dynamic(ctx,48,1);
            Nuklear.nk_label_wrap(ctx,surveyFollowStraightLine
                    ?"Among minimum-hop routes, this minimizes the sum of squared distances to the straight source-to-probe segment. Bonds still zigzag, and the endpoint is the selected sampling node. This is not a measured wave trajectory."
                    :"Equal-length routes are resolved by neighbor order. This can group steps into a bent route; camera projection changes its apparent angles. This is not a measured wave trajectory.");
            Nuklear.nk_layout_row_dynamic(ctx,24,1);
            if(nk_button_label(ctx,"Recompute paths after wall changes")&&surveyPathLoad==null){surveyPathNodes=null;surveyPathFinder=null;surveyOverlayKey="";}
        }
        String overlay=key+":"+surveyGeometryShell+":"+surveyInspectionNode+":"+surveyShortestPath+":"+surveyFollowStraightLine+":"+path.size()
                +":"+appSettings.getSurveyProbeOpacity()+":"+appSettings.getSurveyHighlightOpacity()+":"+appSettings.getSurveySourceOpacity();
        if(!overlay.equals(surveyOverlayKey)||project.scene.getSurveyLineOverlays().isEmpty()) {
            project.scene.showSurveyLine(surveyInspection,surveyGeometryShell,surveyInspectionNode,view.receiverDistances(),path,surveyShortestPath);surveyOverlayKey=overlay;
        }
        scalar(ctx,"Source equilibrium XYZ",surveyVector(surveyInspection.source()));
        scalar(ctx,"Selected probe equilibrium XYZ",surveyVector(probe.getPlacement().targets().getFirst().requestedCoordinate()));
        var measurement=surveyInspection.measurements().stream().filter(m->m.shell()==surveyGeometryShell).findFirst();
        if(measurement.isPresent()) {
            var m=measurement.get();scalar(ctx,"Saved arrival / peak",surveyNumber(m.arrival())+" updates / "+String.format(Locale.ROOT,"%.6g bonds",m.peak()));
            scalar(ctx,"Saved measurement status",m.status());
        }
        section(ctx,"Saved signed displacement: X updates | Y bonds; orange = selected shell");
        drawSurveySelectedWaveforms(ctx);
        section(ctx,"Sampling nodes (click an index to highlight it in magenta)");
        Nuklear.nk_layout_row_dynamic(ctx,24,3);
        for(String label:List.of("Node index XYZ","Weight","Distance to source"))Nuklear.nk_label(ctx,label,Nuklear.NK_TEXT_LEFT);
        for(int i=0;i<sources.size();i++) {
            var source=sources.get(i);var node=project.scene.getLattice().getNodeByIndex(source.nodeIndex());
            Nuklear.nk_layout_row_dynamic(ctx,24,3);
            var index=source.nodeIndex();
            if(nk_button_label(ctx,(i==surveyInspectionNode?"> ":"")+index.x+", "+index.y+", "+index.z))surveyInspectionNode=i;
            Nuklear.nk_label(ctx,String.format(Locale.ROOT,"%.9f",source.weight()),Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_label(ctx,String.format(Locale.ROOT,"%.9f bonds",node.getEquilibriumPosition().distance(surveyInspection.source())),Nuklear.NK_TEXT_LEFT);
        }
        var node=project.scene.getLattice().getNodeByIndex(sources.get(surveyInspectionNode).nodeIndex());
        scalar(ctx,"Selected node equilibrium XYZ",surveyVector(node.getEquilibriumPosition()));
        scalar(ctx,"Current node displacement XYZ",surveyVector(new Vector3d(node.getPosition()).sub(node.getEquilibriumPosition())));
        scalar(ctx,"Current node velocity XYZ",surveyVector(node.getVelocity()));
        scalar(ctx,"Current node imbalance XYZ",surveyVector(node.getGradientImbalance()));
        Nuklear.nk_layout_row_dynamic(ctx,66,1);
        Nuklear.nk_label_wrap(ctx,"Cyan = virtual probes at rest; magenta/yellow = actual lattice nodes with normal depth occlusion. Connected path lines follow current node positions. A route ends at the selected sampling node, not the virtual probe. This is geometry, not a recorded wave trajectory; current node values are not historical measurements.");
    }

    private static String surveyVector(Vector3d v){return String.format(Locale.ROOT,"%.6g, %.6g, %.6g",v.x,v.y,v.z);}

    private void drawSurveySelectedWaveforms(NkContext ctx) {
        var measurements=surveyInspection.measurements();
        var points=measurements.stream().flatMap(m->m.trace().stream()).toList();
        if(points.isEmpty()){section(ctx,"No saved waveform samples for this line");return;}
        double xmax=points.stream().mapToDouble(remframework.opengl.model.SurveyLineInspection.Point::update).max().orElse(1);
        double ymax=points.stream().mapToDouble(p->Math.abs(p.value())).filter(Double::isFinite).max().orElse(0);
        if(ymax<=0)ymax=1; if(xmax<=0)xmax=1;
        Nuklear.nk_layout_row_dynamic(ctx,200,1);var bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        var canvas=Nuklear.nk_window_get_canvas(ctx);var prior=NkRect.create().set(canvas.clip());
        Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));Nuklear.nk_fill_rect(canvas,bounds,0,Theme.grey03);
        var plot=NkRect.create().set(bounds.x()+62,bounds.y()+12,bounds.w()-76,bounds.h()-45);
        surveyAxes(canvas,plot,0,xmax,-ymax,ymax);
        // Draw the selected shell last so its trace remains visible at intersections.
        var ordered=measurements.stream().sorted(java.util.Comparator.comparing(m->m.shell()==surveyGeometryShell)).toList();
        for(var m:ordered) {
            boolean selected=m.shell()==surveyGeometryShell;float px=0,py=0;boolean previous=false;
            for(var point:m.trace()) {
                if(!Double.isFinite(point.value())){previous=false;continue;}
                float x=plot.x()+plot.w()*(float)(point.update()/xmax),y=plot.y()+plot.h()*(float)((ymax-point.value())/(2*ymax));
                if(previous)Nuklear.nk_stroke_line(canvas,px,py,x,y,selected?3:1,selected?SURVEY_ORANGE:Theme.grey07);
                if(selected)Nuklear.nk_fill_circle(canvas,NkRect.create().set(x-2,y-2,4,4),SURVEY_ORANGE);
                px=x;py=y;previous=true;
            }
        }
        Nuklear.nk_push_scissor(canvas,prior);
    }

    private void surveyProgress(NkContext ctx,TransverseIdentitySurvey.ViewState view) {
        String label=view.running()?String.format(Locale.ROOT,"Orientation %d/26 | update %d/%d | %.1f%%",view.orientation()+1,view.step(),view.limit(),100*view.progress())
                :view.completed()+"/26 orientations recorded";
        scalar(ctx,"Progress",label);
        Nuklear.nk_layout_row_dynamic(ctx,22,1);var bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        var canvas=Nuklear.nk_window_get_canvas(ctx);
        Nuklear.nk_fill_rect(canvas,bounds,3,Theme.grey04);
        Nuklear.nk_fill_rect(canvas,NkRect.create().set(bounds.x(),bounds.y(),bounds.w()*(float)view.progress(),bounds.h()),3,Theme.blue2);
    }

    private double surveyMetricValue(TransverseIdentitySurvey.ShellSummary row) {
        return switch(surveyMetric){case 1->row.arrivalSpread();case 2->row.peakSpread();case 3->row.distanceRange();case 4->row.weightedHopRange();default->row.range();};
    }
    private void drawSurveyDistanceChart(NkContext ctx,List<TransverseIdentitySurvey.ShellSummary> rows) {
        Nuklear.nk_layout_row_dynamic(ctx,210,1);var bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        var canvas=Nuklear.nk_window_get_canvas(ctx);var prior=NkRect.create().set(canvas.clip());
        Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));
        Nuklear.nk_fill_rect(canvas,bounds,0,Theme.grey03);
        var plot=NkRect.create().set(bounds.x()+60,bounds.y()+16,Math.max(1,bounds.w()-82),bounds.h()-50);
        double xmin=rows.getFirst().radius(),xmax=Math.max(xmin+1,rows.getLast().radius());
        double ymax=rows.stream().mapToDouble(this::surveyMetricValue).filter(Double::isFinite).max().orElse(1);
        ymax=Math.max(surveyMetric==3?1e-15:.01,ymax*1.15);
        surveyAxes(canvas,plot,xmin,xmax,0,ymax);
        var color=surveyMetric==2?Theme.yellow:Theme.blue4;
        float px=0,py=0;boolean previous=false,previousProvisional=false;
        for(var row:rows) {
            double value=surveyMetricValue(row);
            if(!Double.isFinite(value)){previous=false;continue;}
            boolean provisional=surveyMetric==4?row.paths()<row.total():surveyMetric==3?row.distances()<row.total():row.provisional();
            float x=plot.x()+(float)((row.radius()-xmin)/(xmax-xmin))*plot.w(),y=plot.y()+plot.h()*(1-(float)(value/ymax));
            if(previous) {
                if(previousProvisional||provisional)for(int dash=0;dash<12;dash+=2)Nuklear.nk_stroke_line(canvas,px+(x-px)*dash/12,py+(y-py)*dash/12,px+(x-px)*(dash+1)/12,py+(y-py)*(dash+1)/12,2,color);
                else Nuklear.nk_stroke_line(canvas,px,py,x,y,2,color);
            }
            var dot=NkRect.create().set(x-4,y-4,8,8);
            if(provisional)Nuklear.nk_stroke_circle(canvas,dot,2,color);else Nuklear.nk_fill_circle(canvas,dot,color);
            px=x;py=y;previous=true;previousProvisional=provisional;
        }
        Nuklear.nk_push_scissor(canvas,prior);
    }

    private void surveyAxes(NkCommandBuffer canvas,NkRect plot,double xmin,double xmax,double ymin,double ymax) {
        var label=NkRect.create();
        for(int i=0;i<=4;i++) {
            float x=plot.x()+plot.w()*i/4,y=plot.y()+plot.h()*(1-i/4f);
            Nuklear.nk_stroke_line(canvas,plot.x(),y,plot.x()+plot.w(),y,1,Theme.grey05);
            Nuklear.nk_draw_text(canvas,label.set(plot.x()-58,y-8,54,18),String.format(Locale.ROOT,"%.2g",ymin+(ymax-ymin)*i/4),nuklearModel.getDefaultFont(),Theme.grey03,Theme.grey12);
            Nuklear.nk_draw_text(canvas,label.set(x-20,plot.y()+plot.h()+8,46,18),String.format(Locale.ROOT,"%.0f",xmin+(xmax-xmin)*i/4),nuklearModel.getDefaultFont(),Theme.grey03,Theme.grey12);
        }
    }

    private void drawSurveyLive(NkContext ctx,TransverseIdentitySurvey.ViewState view) {
        if(!view.running()||view.traces().isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx,48,1);Nuklear.nk_label_wrap(ctx,"The live display is available while a survey runs. Use Distance results to inspect completed measurements.");return;
        }
        scalar(ctx,"Excitation direction (XYZ)",String.format(Locale.ROOT,"%.3f, %.3f, %.3f",view.px(),view.py(),view.pz()));
        scalar(ctx,"Live shell (bond radius)","Shell "+(surveyShell+1));
        try(var stack=MemoryStack.stackPush()) {
            var index=stack.ints(surveyShell);Nuklear.nk_slider_int(ctx,0,index,Math.max(0,view.shells()-1),1);surveyShell=index.get(0);
        }
        var selected=view.traces().stream().filter(t->t.shell()==surveyShell).toList();
        double max=view.traces().stream().flatMap(t->t.samples().stream()).mapToDouble(Math::abs).max().orElse(0);
        scalar(ctx,"Shared receiver display scale",max>0?String.format(Locale.ROOT,"peak %.3g bonds (visually amplified)",max):"Waiting for pulse");
        section(ctx,"Perpendicular receiver plane: cyan positive, yellow negative");
        Nuklear.nk_layout_row_dynamic(ctx,180,1);var bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)!=Nuklear.NK_WIDGET_INVALID) {
            var canvas=Nuklear.nk_window_get_canvas(ctx);var prior=NkRect.create().set(canvas.clip());Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));
            Nuklear.nk_fill_rect(canvas,bounds,0,Theme.grey03);
            float cx=bounds.x()+bounds.w()/2,cy=bounds.y()+bounds.h()/2;double far=view.traces().getLast().radius();
            for(var trace:view.traces()) {
                float radius=(float)(72*trace.radius()/far),a=(float)(2*Math.PI*trace.ray()/view.rays());
                if(trace.ray()==0)Nuklear.nk_stroke_circle(canvas,NkRect.create().set(cx-radius,cy-radius,2*radius,2*radius),1,Theme.grey05);
                double value=trace.samples().getLast();float size=4+(float)(max>0?10*Math.abs(value)/max:0);
                float x=cx+radius*(float)Math.cos(a),y=cy-radius*(float)Math.sin(a);
                Nuklear.nk_fill_circle(canvas,NkRect.create().set(x-size/2,y-size/2,size,size),value>=0?Theme.blue4:Theme.yellow);
            }
            double t=view.step(),duration=view.period()*view.cycles();
            double force=t<=duration?Math.sin(2*Math.PI*t/view.period())*Math.pow(Math.sin(Math.PI*t/duration),2):0;
            float size=8+12*(float)Math.abs(force);
            Nuklear.nk_fill_circle(canvas,NkRect.create().set(cx-size/2,cy-size/2,size,size),force>=0?Theme.blue4:Theme.yellow);
            Nuklear.nk_push_scissor(canvas,prior);
        }
        section(ctx,selected.isEmpty()?"Signed receiver displacement":"Signed displacement at radius "+selected.getFirst().radius()+" bonds; X = updates");
        Nuklear.nk_layout_row_dynamic(ctx,180,1);bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        var canvas=Nuklear.nk_window_get_canvas(ctx);var prior=NkRect.create().set(canvas.clip());Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));
        Nuklear.nk_fill_rect(canvas,bounds,0,Theme.grey03);
        var plot=NkRect.create().set(bounds.x()+60,bounds.y()+14,Math.max(1,bounds.w()-82),bounds.h()-48);
        double scale=selected.stream().flatMap(t->t.samples().stream()).mapToDouble(Math::abs).max().orElse(0);scale=Math.max(scale,1e-15)*1.1;
        surveyAxes(canvas,plot,0,view.limit(),-scale,scale);
        var colors=List.of(Theme.blue4,Theme.yellow,Theme.blue3,Theme.grey14);
        for(int ray=0;ray<selected.size();ray++) {
            var trace=selected.get(ray);
            for(int i=1;i<trace.samples().size();i++)Nuklear.nk_stroke_line(canvas,
                    plot.x()+plot.w()*(i-1)/view.limit(),plot.y()+plot.h()*(float)(.5-trace.samples().get(i-1)/(2*scale)),
                    plot.x()+plot.w()*i/view.limit(),plot.y()+plot.h()*(float)(.5-trace.samples().get(i)/(2*scale)),1.5f,colors.get(ray%4));
        }
        Nuklear.nk_push_scissor(canvas,prior);
        Nuklear.nk_layout_row_dynamic(ctx,44,1);Nuklear.nk_label_wrap(ctx,"Four equally spaced receiver rays per shell are shown. Dot size and waveform scaling amplify measured signals for display; the simulation forces are unchanged.");
    }

    private void calibrationSection(NkContext ctx) {
        section(ctx, "Arrow calibration");
        project.scene.setShowCalibrationArrow(nk_check_label(ctx, "Show calibration arrow",
                                                             project.scene.isShowCalibrationArrow(), 0.7, 0.3));
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        Nuklear.nk_label(ctx, "Direction:", Nuklear.NK_TEXT_LEFT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] directions = {"-X", "+Y", "+Z"};
            Scene.CalibrationDirection currentDirection = project.scene.getCalibrationDirection();
            String currentLabel = switch (currentDirection) {
                case NEGATIVE_X -> "-X";
                case POSITIVE_Y -> "+Y";
                case POSITIVE_Z -> "+Z";
            };
            String selected = nk_combo_options(ctx, stack, directions, currentLabel);
            Scene.CalibrationDirection selectedDirection = switch (selected) {
                case "+Y" -> Scene.CalibrationDirection.POSITIVE_Y;
                case "+Z" -> Scene.CalibrationDirection.POSITIVE_Z;
                default -> Scene.CalibrationDirection.NEGATIVE_X;
            };
            project.scene.setCalibrationDirection(selectedDirection);
        }
        Vector3d calibrationVector = project.scene.getCalibrationDirection().vector();
        scalar(ctx, "Calibration", calibrationDirectionLabel(project.scene.getCalibrationDirection())
                + "  (" + (int) calibrationVector.x + ", "
                + (int) calibrationVector.y + ", " + (int) calibrationVector.z + ")");
    }

    private void inspectorSection(NkContext ctx, PassiveObserver probe) {
        section(ctx, "Neighborhood Inspector (read-only)");
        if (project.scene.isNeighborhoodInspectionActive()
                || project.scene.isNeighborhoodInspectionTransitionActive()) {
            scalar(ctx, "Inspected object", "Existing selected probe marker");
            scalar(ctx, "Camera target", probe instanceof InterpolatedProbe
                    ? "Selected interpolation source position" : "Live probe marker position");
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
            Nuklear.nk_label(ctx, "Inspection alpha", Nuklear.NK_TEXT_LEFT);
            project.scene.setInspectionSourceAlpha(
                    nk_slider(ctx, 0.0, project.scene.getInspectionSourceAlpha(), 1.0, 0.05));
            project.scene.setShowInspectionProbe(
                    nk_check_label(ctx, "Show probe", project.scene.isShowInspectionProbe(), 0.7, 0.3));
            section(ctx, "Inspection legend");
            scalar(ctx, "Selected source", "Opaque green");
            scalar(ctx, "Direct neighbor", "Grey / configured alpha");
            scalar(ctx, "Other source", "Orange / configured alpha");
            scalar(ctx, "Probe marker", project.scene.isShowInspectionProbe()
                    ? "Shown / configured alpha" : "Hidden");
        } else {
            scalar(ctx, "Status", "Select a probe, then inspect (live or paused)");
        }
    }

    private void interpolationSourcesSection(NkContext ctx, InterpolatedProbe probe) {
        section(ctx, "Interpolation Sources");
        int selectedIndex = project.scene.getInspectedSourceIndex();
        for (int index = 0; index < probe.getSourceNodes().size(); index++) {
            Node source = probe.getSourceNodes().get(index);
            ProbeDefinition.InterpolationSource definitionSource = probe.getDefinition()
                    .getInterpolationSources().get(index);
            Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 3);
            Nuklear.nk_layout_row_push(ctx, 0.26f);
            if (Nuklear.nk_button_label(ctx,
                    (index == selectedIndex ? "> " : "") + "Source " + (index + 1))) {
                project.scene.selectInspectionSource(index);
            }
            Nuklear.nk_layout_row_push(ctx, 0.49f);
            Nuklear.nk_label(ctx, formatCoordinate(source.getIndex()), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.25f);
            Nuklear.nk_label(ctx, "weight " + format(definitionSource.weight()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_end(ctx);
        }
    }

    private void interpolationSourceDetails(NkContext ctx, InterpolatedProbe probe) {
        int selectedIndex = Math.clamp(project.scene.getInspectedSourceIndex(),
                                       0, probe.getSourceNodes().size() - 1);
        Node source = probe.getSourceNodes().get(selectedIndex);
        ProbeDefinition.InterpolationSource definitionSource = probe.getDefinition()
                .getInterpolationSources().get(selectedIndex);
        section(ctx, "Selected interpolation source");
        scalar(ctx, "Source", "Source " + (selectedIndex + 1));
        vector(ctx, "Source coordinate", source.getIndex());
        scalar(ctx, "Interpolation weight", format(definitionSource.weight()));
        vector(ctx, "Source position", source.getPosition());
        vector(ctx, "Source velocity", source.getVelocity());
        vector(ctx, "Source acceleration", source.getAcceleration());
        vector(ctx, "Source stable gradient", probe.getOccupancies().get(selectedIndex)
                .getGradientImbalance());

        section(ctx, "Direct neighbors of selected source");
        for (Neighbor direction : Neighbor.values()) {
            Node neighbor = source.getNeighbor(direction);
            if (neighbor == null) {
                scalar(ctx, directionLabel(direction), "Unavailable");
                continue;
            }
            vector(ctx, directionLabel(direction) + " coordinate", neighbor.getIndex());
            vector(ctx, directionLabel(direction) + " position", neighbor.getPosition());
            vector(ctx, directionLabel(direction) + " velocity", neighbor.getVelocity());
            vector(ctx, directionLabel(direction) + " acceleration", neighbor.getAcceleration());
            vector(ctx, directionLabel(direction) + " stable gradient",
                   neighbor.getGradientImbalance());
        }
    }

    private static String formatCoordinate(org.joml.Vector3i coordinate) {
        return "(" + coordinate.x + ", " + coordinate.y + ", " + coordinate.z + ")";
    }

    private static String directionLabel(Neighbor direction) {
        return switch (direction) {
            case RIGHT -> "+X";
            case TOP -> "+Y";
            case FRONT -> "+Z";
            case LEFT -> "-X";
            case BOTTOM -> "-Y";
            case BACK -> "-Z";
        };
    }

    static String sourceSamplesLabel(int index) {
        return "Source samples per completed cycle " + (index + 1);
    }

    private void comparisonSection(NkContext ctx) {
        section(ctx, "Gradient Imbalance Comparison");
        if (Nuklear.nk_button_label(ctx, "Clear Comparison")) project.scene.clearComparison();
        ProbeComparison comparison = project.scene.getComparison();
        PassiveObserver a = comparison.get(ProbeComparison.Slot.A);
        PassiveObserver b = comparison.get(ProbeComparison.Slot.B);
        scalar(ctx, "Comparison A", a == null ? "Waiting for A" : a.getId());
        scalar(ctx, "Comparison B", b == null ? "Waiting for B" : b.getId());
        if (a == null || b == null) return;
        comparisonValues(ctx, "A", a);
        comparisonValues(ctx, "B", b);
        double delta = comparison.deltaRadial();
        scalar(ctx, "Δg_parallel", format(delta));
        scalar(ctx, "|Δg_parallel|", format(Math.abs(delta)));
    }

    private void comparisonValues(NkContext ctx, String label, PassiveObserver probe) {
        ProbeComparison.Measurement measurement = ProbeComparison.calculate(probe);
        scalar(ctx, label + " ID", probe.getId());
        scalar(ctx, label + " detector type", probe.getDetectorType().name());
        scalar(ctx, label + " requested radius", Integer.toString(measurement.requestedRadius()));
        vector(ctx, label + " requested direction", measurement.requestedDirection());
        vector(ctx, label + " g", measurement.gradient());
        scalar(ctx, label + " |g|", format(measurement.gradient().length()));
        scalar(ctx, label + " g_parallel", format(measurement.radial()));
        scalar(ctx, label + " g_perp", format(measurement.sideways()));
    }

    private void arrivalSection(NkContext ctx) {
        if(project.scene.getLattice().isMechanicsOnly()) {
            controlledWavelength=appSettings.getDiamondProbeSourceWidth();
            positionThresholdExponent=appSettings.getDiamondProbePositionExponent();
            gradientThresholdExponent=appSettings.getDiamondProbeGradientExponent();
            speedSpreadTolerance=appSettings.getDiamondProbeTolerance();
        }
        section(ctx, "Propagation arrival by probe shell");
        if(project.scene.getLattice().isMechanicsOnly()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,"Bulk runs stop before reflections. Thin XY slabs include fixed surface effects and use the configurable maximum updates below; only in-plane response is assessed. Arrival thresholds/interpolation need convergence checks; this is not an isotropy proof.");
            if(project.scene.getLattice().isDiamondTransportEnabled())return;
        }
        List<Integer> shells = ProbeArrivalMeasurement.availableShells(project.scene.getProbes());
        if (shells.isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 1);
            Nuklear.nk_label_wrap(ctx,
                    "No generated probe shell is available. Add passive probes first.");
            return;
        }
        if (!shells.contains(selectedArrivalShell)) selectedArrivalShell = shells.getFirst();
        if(project.scene.getLattice().isMechanicsOnly()) {
            final int shellIndex=selectedArrivalShell;
            boolean planar=project.scene.getProbes().stream().anyMatch(p->p.getDefinition().getPlacement()!=null
                && p.getDefinition().getPlacement().measurementSpace().equals("Diamond physical XY")
                && p.getDefinition().getPlacement().targets().stream().anyMatch(t->t.shell()==shellIndex));
            long directionCount=project.scene.getProbes().stream().filter(p->p.getDefinition().getPlacement()!=null
                && p.getDefinition().getPlacement().targets().stream().anyMatch(t->t.shell()==shellIndex)).count();
            scalar(ctx,"Direction coverage",planar?"XY only: "+directionCount+" directions":"XYZ: 26 directions");
        }

        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        Nuklear.nk_label(ctx, "Shell", Nuklear.NK_TEXT_LEFT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] options = shells.stream()
                    .map(shell -> "Shell " + (shell + 1))
                    .toArray(String[]::new);
            String current = "Shell " + (selectedArrivalShell + 1);
            String selected = nk_combo_options(ctx, stack, options, current);
            int option = java.util.Arrays.asList(options).indexOf(selected);
            if (option >= 0 && selectedArrivalShell != shells.get(option)) {
                selectedArrivalShell = shells.get(option);
                project.scene.getProbeArrivalMeasurement().clear();
            }
        }

        scalar(ctx, "Position change threshold", "10^" + (int) positionThresholdExponent);
        positionThresholdExponent = Math.rint(nk_slider(
                ctx, -18.0, positionThresholdExponent, 0.0, 1.0));
        scalar(ctx, "Gradient change threshold", "10^" + (int) gradientThresholdExponent);
        gradientThresholdExponent = Math.rint(nk_slider(
                ctx, -18.0, gradientThresholdExponent, 0.0, 1.0));

        ProbeArrivalMeasurement measurement = project.scene.getProbeArrivalMeasurement();
        if(project.scene.getLattice().isMechanicsOnly()) {
            try(MemoryStack stack=MemoryStack.stackPush()) {
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
                Nuklear.nk_label(ctx,"Excitation mode",Nuklear.NK_TEXT_LEFT);
                String mode=nk_combo_options(ctx,stack,new String[]{"Axis packet","Spherical sweep"},appSettings.isDiamondProbeSphericalSweep()?"Spherical sweep":"Axis packet");
                appSettings.setDiamondProbeSphericalSweep(mode.equals("Spherical sweep"));
                Nuklear.nk_label(ctx,"Packet excitation axis",Nuklear.NK_TEXT_LEFT);
                var axes=remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.values();
                var selectedAxis=nk_combo_options(ctx,stack,new String[]{"X","Y","Z"},appSettings.getDiamondProbeExcitationAxis().name());
                for(var axis:axes)if(axis.name().equals(selectedAxis))appSettings.setDiamondProbeExcitationAxis(axis);
                double entered=nk_label_edit(ctx,stack,"Speed spread tolerance (%)",toleranceBuffer,speedSpreadTolerance);
                if(Double.isFinite(entered) && entered>0) speedSpreadTolerance=entered;
                double wavelength=nk_label_edit(ctx,stack,appSettings.isDiamondProbeSphericalSweep()?"Source width (bond lengths)":"Packet wavelength (bond lengths)",wavelengthBuffer,controlledWavelength);
                if(Double.isFinite(wavelength) && wavelength>=2)controlledWavelength=wavelength;
                double amplitude=nk_label_edit(ctx,stack,appSettings.isDiamondProbeSphericalSweep()?"Sweep radius / peak amplitude (bonds)":"Initial peak amplitude (bond lengths)",amplitudeBuffer,appSettings.getDiamondProbeAmplitude());
                if(Double.isFinite(amplitude) && amplitude>0)appSettings.setDiamondProbeAmplitude(amplitude);
                double maximumUpdates=nk_label_edit(ctx,stack,"Recording cap (sweep / XY slab)",maximumUpdatesBuffer,appSettings.getDiamondProbeMaximumUpdates());
                if(Double.isFinite(maximumUpdates) && maximumUpdates>=1 && maximumUpdates<=Integer.MAX_VALUE
                        && maximumUpdates==Math.rint(maximumUpdates))
                    appSettings.setDiamondProbeMaximumUpdates((int)maximumUpdates);
                if(appSettings.isDiamondProbeSphericalSweep()) {
                    double period=nk_label_edit(ctx,stack,"Updates per spherical cycle",sweepPeriodBuffer,appSettings.getDiamondProbeSweepPeriod());
                    double turns=nk_label_edit(ctx,stack,"Spiral turns per half cycle",sweepTurnsBuffer,appSettings.getDiamondProbeSweepTurns());
                    double cycles=nk_label_edit(ctx,stack,"Complete measurement cycles",sweepCyclesBuffer,appSettings.getDiamondProbeMeasurementCycles());
                    if(Double.isFinite(period)&&Double.isFinite(turns)&&Double.isFinite(cycles)
                        &&period==Math.rint(period)&&turns==Math.rint(turns)&&cycles==Math.rint(cycles)
                        &&period<=1_000_000&&turns<=64&&cycles<=1000) {
                        try {appSettings.setDiamondProbeSweepConfiguration((int)period,(int)turns,(int)cycles);}
                        catch(IllegalArgumentException invalid){scalar(ctx,"Sweep configuration",invalid.getMessage());}
                    }
                }
            }
            appSettings.setDiamondProbeDetection(controlledWavelength,positionThresholdExponent,gradientThresholdExponent,speedSpreadTolerance);
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
            Nuklear.nk_label_wrap(ctx,appSettings.isDiamondProbeSphericalSweep()?"A spherical cycle sweeps pole-to-pole and returns along the reversed spiral. Constant source radius; equal-area latitude progression. Probe windows cover whole source periods after detection. Period must be even and >= 32 times the turn count. Boundary limits can leave windows incomplete.":"Choose X, Y or Z displacement polarization. Your choice is retained when lattice dimensions change. The packet envelope stays radial; signed cycle/reversal diagnostics currently measure Z.");
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,appSettings.isDiamondProbeSphericalSweep()?"Run centered spherical sweep":"Run controlled centered packet")) {
                requestControlledProbeRun();
                project.getRemModel().startSimulation();
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,String.format(Locale.ROOT,"Source support: %.1f bonds. Suggested shell radii: %.1f / %.1f. Regenerate probes after changing wavelength. Full 3D tests need space in X, Y and Z; a thin slab tests different boundary conditions.",1.5*controlledWavelength,1.5*controlledWavelength+4,1.5*controlledWavelength+8));
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Change lattice dimensions..."))nuklearModel.showFrame(NuklearModel.Frame.dimension);
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,project.scene.getLattice().getDisturbance().summary());
        }
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        if (Nuklear.nk_button_label(ctx, "Start / Reset Measurement")) {
            if(project.scene.getLattice().isMechanicsOnly() && appSettings.isDiamondProbeSphericalSweep()) {
                requestControlledProbeRun();project.getRemModel().startSimulation();
            } else if(project.scene.getLattice().isMechanicsOnly())
                project.scene.requestDiamondProbeArrival(selectedArrivalShell,Math.pow(10.0,positionThresholdExponent),Math.pow(10.0,gradientThresholdExponent));
            else measurement.start(List.copyOf(project.scene.getProbes()), selectedArrivalShell,
                    Math.pow(10.0, positionThresholdExponent),
                    Math.pow(10.0, gradientThresholdExponent));
        }
        if (Nuklear.nk_button_label(ctx, "Clear")) project.scene.clearProbeArrivalMeasurements();

        var shellRows=project.scene.getProbeShellArrivalReport().rows();
        if(project.scene.getLattice().isMechanicsOnly())drawShellReport(ctx,shellRows);
        final int displayedShell=selectedArrivalShell;
        ProbeArrivalMeasurement.Snapshot snapshot = shellRows.stream().filter(r->r.shell()==displayedShell)
            .map(ProbeShellArrivalReport.Row::snapshot).findFirst().orElseGet(measurement::snapshot);
        scalar(ctx, "Time unit", "simulation updates since Start");
        scalar(ctx, "Status", arrivalStatus(snapshot));
        scalar(ctx, "Elapsed updates", Long.toString(snapshot.elapsedUpdates()));
        if(project.scene.getLattice().isMechanicsOnly()) {
            double positionSpread=ProbeArrivalMeasurement.equalRadiusApparentSpread(snapshot.results(),false);
            double gradientSpread=ProbeArrivalMeasurement.equalRadiusApparentSpread(snapshot.results(),true);
            scalar(ctx,"Apparent position-speed spread",Double.isFinite(positionSpread)?String.format(Locale.ROOT,"%.3f%%",positionSpread):"Incomplete");
            scalar(ctx,"Apparent gradient-speed spread",Double.isFinite(gradientSpread)?String.format(Locale.ROOT,"%.3f%%",gradientSpread):"Incomplete");
            scalar(ctx,"Position tolerance check",ProbeArrivalMeasurement.toleranceResult(positionSpread,speedSpreadTolerance));
            scalar(ctx,"Gradient tolerance check",ProbeArrivalMeasurement.toleranceResult(gradientSpread,speedSpreadTolerance));
            scalar(ctx,"Isotropy verdict","Inconclusive — convergence checks pending");
        }
        Nuklear.nk_layout_row_dynamic(ctx, 42, 1);
        Nuklear.nk_label_wrap(ctx,
                "Position: first n with |x(n) - x(0)| >= threshold. Gradient: first n with "
                + "|g(n) - g(0)| >= threshold. Combined arrival is max(position, gradient).");

        if (!snapshot.hasMeasurement()) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 1);
            Nuklear.nk_label(ctx,
                    "Choose a shell and press Start / Reset Measurement.", Nuklear.NK_TEXT_LEFT);
            return;
        }

        section(ctx, "Arrival chart — Y: arrival update | X: probe P1 ... PN");
        scalar(ctx, "Legend", "Position = cyan | Gradient = yellow");
        drawArrivalChart(ctx, snapshot);

        section(ctx, "Exact arrival updates");
        Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 6);
        Nuklear.nk_layout_row_push(ctx, 0.07f);
        Nuklear.nk_label(ctx, "Probe", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(ctx, 0.20f);
        Nuklear.nk_label(ctx, "ID", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(ctx, 0.31f);
        Nuklear.nk_label(ctx, "Represented ray", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(ctx, 0.14f);
        Nuklear.nk_label(ctx, "Position", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_layout_row_push(ctx, 0.14f);
        Nuklear.nk_label(ctx, "Gradient", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_layout_row_push(ctx, 0.14f);
        Nuklear.nk_label(ctx, "Combined", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_layout_row_end(ctx);
        for (ProbeArrivalMeasurement.Result result : snapshot.results()) {
            Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 6);
            Nuklear.nk_layout_row_push(ctx, 0.07f);
            Nuklear.nk_label(ctx, "P" + result.ordinal(), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.20f);
            Nuklear.nk_label(ctx, result.probeId(), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.31f);
            Nuklear.nk_label(ctx, result.representedRays(), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.14f);
            Nuklear.nk_label(ctx, arrivalValue(result.positionEstimatedArrival()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_push(ctx, 0.14f);
            Nuklear.nk_label(ctx, arrivalValue(result.gradientEstimatedArrival()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_push(ctx, 0.14f);
            Nuklear.nk_label(ctx, arrivalValue(result.combinedEstimatedArrival()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_end(ctx);
        }
    }

    private final StringBuffer orbitPeriodBuffer=new StringBuffer(),orbitRatioBuffer=new StringBuffer(),orbitWidthBuffer=new StringBuffer(),orbitAmplitudeBuffer=new StringBuffer();
    private void drawRotatingOrbit(NkContext ctx) {
        section(ctx,"Rotating source: prescribed orbit / Identity force");
        try(var stack=MemoryStack.stackPush()) {
            double period=nk_label_edit(ctx,stack,"X orbit period (updates)",orbitPeriodBuffer,appSettings.getOrbitFastPeriod());
            double ratio=nk_label_edit(ctx,stack,"Slower-axis period multiplier",orbitRatioBuffer,appSettings.getOrbitRatio());
            double width=nk_label_edit(ctx,stack,"Orbit source width (bonds)",orbitWidthBuffer,appSettings.getOrbitWidth());
            double amplitude=nk_label_edit(ctx,stack,"Orbit radius / Identity force amplitude",orbitAmplitudeBuffer,appSettings.getOrbitAmplitude());
            if(period==Math.rint(period)&&ratio==Math.rint(ratio)&&period<=4096&&ratio<=16) {
                try{appSettings.setOrbitConfiguration((int)period,(int)ratio,width,amplitude);}
                catch(IllegalArgumentException invalid){scalar(ctx,"Orbit settings",invalid.getMessage());}
            }
        }
        var orbit=new remframework.geometry.discovery.research.diamond.DiamondRotatingOrbit(appSettings.getOrbitFastPeriod(),appSettings.getOrbitRatio());
        scalar(ctx,"X / Y / Z periods",orbit.fastPeriod()+" / "+(orbit.fastPeriod()*orbit.ratio())+" / "+orbit.period()+" updates");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"A circular X orbit with continuously rotating Y and Z phases. Repeats until paused; no measurements or automatic arrival stop. Reflections are included. Finite path coverage is not guaranteed equal.");
        var disturbance=project.scene.getLattice().getDisturbance();
        scalar(ctx,"Constant Frequency / Electric Field",String.format(Locale.ROOT,"%.6f / %.6f; q = %.6f",project.getConstantFrequency(),project.getConstantElectricFactor(),project.getConstantWaveSpeed()*project.getConstantElectricFactor()));
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        if(Nuklear.nk_button_label(ctx,"Use tested propagation settings (q = 0.95)")) {
            project.getRemModel().stopSimulation();disturbance.cancel();
            project.setConstantFrequency(Math.sqrt(.95));project.setConstantElectricFactor(1);
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
        if(Nuklear.nk_button_label(ctx,"Start / reset prescribed orbit")) {
            disturbance.requestOrbitPreview(appSettings.getOrbitWidth(),appSettings.getOrbitAmplitude(),orbit);
            project.getRemModel().startSimulation();
        }
        if(Nuklear.nk_button_label(ctx,"Pause / resume")) {
            if(project.getRemModel().isSimulating())project.getRemModel().stopSimulation();else project.getRemModel().startSimulation();
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        if(Nuklear.nk_button_label(ctx,"Start centered Identity XYZ orbit (existing force mechanics)")) {
            var lattice=project.scene.getLattice();
            var center=lattice.getNodeByIndex(new org.joml.Vector3i(project.getDimensionX()/2,project.getDimensionY()/2,project.getDimensionZ()/2));
            if(center!=null&&!center.isFixed()) {
                project.getRemModel().stopSimulation();disturbance.cancel();
                var occupancy=project.occupancies.stream().filter(o->"XYZ orbit source".equals(o.getIdentity().getName())).findFirst().orElse(null);
                if(occupancy==null) {
                    occupancy=new remframework.opengl.model.entity.Occupancy(new remframework.opengl.model.entity.Identity("XYZ orbit source"),center);
                    project.occupancies.add(occupancy);
                } else occupancy.setNode(center);
                var identity=occupancy.getIdentity();identity.setXyzOrbit(true);identity.reset();
                identity.getPhaseIncrement().set(360.0/orbit.fastPeriod(),360.0/(orbit.fastPeriod()*orbit.ratio()),360.0/orbit.period());
                identity.getPhaseAngle().zero();identity.getAmplitude().set(appSettings.getOrbitAmplitude());
                lattice.setDiamondIdentityExcitationEnabled(true);
                project.getRemModel().setSelectedOccupancy(occupancy);project.getRemModel().startSimulation();
            }
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"Identity mode uses orbit radius as force amplitude on its occupied node; source width applies only to the prescribed preview. Edit the Identity for axis increments and amplitudes.");
        drawIdentityOrbitSpread(ctx);
        var active=disturbance.activeOrbit();
        var identitySource=project.occupancies.stream().map(o->o.getIdentity()).filter(id->id.isXyzOrbit()&&"XYZ orbit source".equals(id.getName())).findFirst().orElse(null);
        if(active==null&&identitySource==null)return;
        boolean identityMode=active==null;
        if(identityMode)active=orbit;
        long now=identityMode?identitySource.getOrbitUpdates():disturbance.steps();
        scalar(ctx,"Orbit progress",(identityMode?"Identity force; ":"Prescribed; ")+"update "+now);
        scalar(ctx,"Path preview","XY / XZ / YZ; cyan = recent trail, yellow = source (magnified)");
        Nuklear.nk_layout_row_dynamic(ctx,160,1);NkRect bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        var canvas=Nuklear.nk_window_get_canvas(ctx);var prior=NkRect.create().set(canvas.clip());
        Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));
        Nuklear.nk_fill_rect(canvas,bounds,0,Theme.colorChart);
        float scale=Math.min(bounds.w()/7,60),cy=bounds.y()+80;
        for(int plane=0;plane<3;plane++) {
            float cx=bounds.x()+bounds.w()*(plane+.5f)/3;
            Nuklear.nk_stroke_line(canvas,cx-scale,cy,cx+scale,cy,1,Theme.grey05);
            Nuklear.nk_stroke_line(canvas,cx,cy-scale,cx,cy+scale,1,Theme.grey05);
            float px=0,py=0;
            for(int i=0;i<=256;i++) {
                long t=now-Math.min(now,active.fastPeriod()*2L)+(long)Math.min(now,active.fastPeriod()*2L)*i/256;
                var d=identityMode?identitySource.sampleOrbitExcitation(t-now).div(Math.max(1e-30,identitySource.getAmplitude().length()/Math.sqrt(3))):active.direction(t);
                float x=cx+scale*(float)(plane==2?d.y:d.x),y=cy-scale*(float)(plane==0?d.y:d.z);
                if(i>0)Nuklear.nk_stroke_line(canvas,px,py,x,y,1.5f,Theme.blue4);px=x;py=y;
            }
            Nuklear.nk_fill_circle(canvas,NkRect.create().set(px-4,py-4,8,8),Theme.yellow);
        }
        Nuklear.nk_push_scissor(canvas,prior);
        scalar(ctx,"Preview status",identityMode?"Identity excitation through Occupancy and Node.addForce":disturbance.summary());
    }

    private String presetStatus="";
    private final StringBuffer identityWarmupBuffer=new StringBuffer(),identityCyclesBuffer=new StringBuffer();
    private void drawIdentityOrbitSpread(NkContext ctx) {
        section(ctx,"Transverse Identity survey: rotated pulses and distance");
        Nuklear.nk_layout_row_dynamic(ctx,70,1);
        Nuklear.nk_label_wrap(ctx,"26 excitation orientations; perpendicular probe circles. Same finite pulse through Identity force mechanics. Configuration: measurements/diamond-shells/presets/transverse-identity.properties. CSVs save automatically. Incomplete pulse windows are flagged, not accepted as complete measurements.");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
        if(Nuklear.nk_button_label(ctx,"Run all transverse directions"))project.scene.getTransverseIdentitySurvey().request();
        if(Nuklear.nk_button_label(ctx,"Cancel transverse survey"))project.scene.getTransverseIdentitySurvey().requestCancel();
        Nuklear.nk_layout_row_dynamic(ctx,42,1);
        Nuklear.nk_label_wrap(ctx,project.scene.getTransverseIdentitySurvey().status());
        section(ctx,"Measure spread with saved Identity orbit");
        var selected=project.getRemModel().getSelectedOccupancy();
        var source=selected!=null&&selected.getIdentity().isXyzOrbit()?selected.getIdentity():project.occupancies.stream()
                .map(o->o.getIdentity()).filter(id->id.isXyzOrbit()).findFirst().orElse(null);
        try(var stack=MemoryStack.stackPush()) {
            double warmup=nk_label_edit(ctx,stack,"Warm-up complete XYZ cycles",identityWarmupBuffer,appSettings.getOrbitWarmupCycles());
            double cycles=nk_label_edit(ctx,stack,"Complete XYZ measurement cycles",identityCyclesBuffer,appSettings.getDiamondProbeMeasurementCycles());
            if(warmup==Math.rint(warmup)&&warmup>=0&&warmup<=100)appSettings.setOrbitWarmupCycles((int)warmup);
            if(cycles==Math.rint(cycles)&&cycles>=1&&cycles<=100)appSettings.setDiamondProbeMeasurementCycles((int)cycles);
        }
        int period=0;
        if(source!=null)try {period=remframework.opengl.model.IdentityOrbitSpreadRun.periodFor(source);}
        catch(IllegalArgumentException invalid){scalar(ctx,"Source period",invalid.getMessage());}
        scalar(ctx,"Saved source",source==null?"Select an XYZ orbit Identity":source.getName());
        scalar(ctx,"Identity trajectory",source!=null&&source.isEqualAreaOrbit()?"Equal-area sphere sweep":"Coupled XYZ rotations");
        if(period>0)scalar(ctx,"Combined period / total updates",period+" / "+((long)period*(appSettings.getOrbitWarmupCycles()+appSettings.getDiamondProbeMeasurementCycles())));
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
        Nuklear.nk_label_wrap(ctx,"Restarts the existing Identity from equilibrium. Apply shell probes first. Shared complete-cycle windows measure gradient RMS response spread; first-arrival timing is separate. Reflections are included. XY probes cover only XY directions.");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        if(Nuklear.nk_button_label(ctx,"Restart Identity and measure all applied shells")&&source!=null)
            project.scene.requestIdentityOrbitSpread(source,appSettings.getOrbitWarmupCycles(),appSettings.getDiamondProbeMeasurementCycles(),
                Math.pow(10,positionThresholdExponent),Math.pow(10,gradientThresholdExponent));
        var run=project.scene.getIdentityOrbitSpreadRun();
        scalar(ctx,"Identity spread",project.scene.getIdentityOrbitSpreadError().isEmpty()?run.status():project.scene.getIdentityOrbitSpreadError());
        var report=project.scene.getProbeShellArrivalReport();
        if(run.period()==0)return;
        scalar(ctx,"Source offset from shell center",String.format(Locale.ROOT,"%.6g bonds; timing includes this geometry",run.sourceCenterOffset()));
        var responses=report.responseSpreads();
        if(!responses.isEmpty()) {
            drawIdentityResponseChart(ctx,responses);
            for(var row:responses)scalar(ctx,"S"+(row.shell()+1)+" r="+row.radius()+" gradient RMS",row.complete()==row.total()
                ?String.format(Locale.ROOT,"min %.5g; max %.5g; mean %.5g; spread %.4f%%",row.minimum(),row.maximum(),row.mean(),row.spreadPercent())
                :row.complete()+" / "+row.total()+" complete");
            for(var row:report.cycleArrivals())scalar(ctx,"S"+(row.shell()+1)+" "+row.signal()+" arrival timing",row.complete()
                ?String.format(Locale.ROOT,"min %.4f; max %.4f; mean %.4f; spread %.4f%%",row.minimum(),row.maximum(),row.mean(),row.spreadPercent()):"Incomplete detections/windows");
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Export Identity orbit spread CSV")) {
                try {
                    var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");java.nio.file.Files.createDirectories(directory);
                    var path=directory.resolve("identity-orbit-spread-"+System.currentTimeMillis()+".csv");
                    try(var writer=java.nio.file.Files.newBufferedWriter(path)){run.writeSummary(project.scene,writer);}
                    var probePath=directory.resolve(path.getFileName().toString().replace(".csv","-probes.csv"));
                    try(var writer=java.nio.file.Files.newBufferedWriter(probePath)){run.writeProbes(project.scene,writer);}
                    shellReportExportStatus="Saved summary and per-probe CSV: "+path;
                } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
            }
            scalar(ctx,"Identity CSV",shellReportExportStatus);
        }
    }
    private void drawIdentityResponseChart(NkContext ctx,List<ProbeShellArrivalReport.ResponseSpread> rows) {
        section(ctx,"X: shell radius (bonds) | Y: gradient RMS response spread (%)");
        Nuklear.nk_layout_row_dynamic(ctx,220,1);var bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        var plot=NkRect.create().set(bounds.x()+52,bounds.y()+10,Math.max(1,bounds.w()-64),Math.max(1,bounds.h()-42));
        var canvas=Nuklear.nk_window_get_canvas(ctx);var prior=NkRect.create().set(canvas.clip());
        Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));Nuklear.nk_fill_rect(canvas,bounds,0,Theme.colorChart);
        double xmin=rows.stream().mapToDouble(ProbeShellArrivalReport.ResponseSpread::radius).min().orElse(0);
        double xmax=rows.stream().mapToDouble(ProbeShellArrivalReport.ResponseSpread::radius).max().orElse(1);if(xmax<=xmin)xmax=xmin+1;
        double ymax=Math.max(1,rows.stream().mapToDouble(ProbeShellArrivalReport.ResponseSpread::spreadPercent).filter(Double::isFinite).max().orElse(1))*1.05;
        var label=NkRect.create();
        for(int i=0;i<=4;i++) {
            float x=plot.x()+plot.w()*i/4,y=plot.y()+plot.h()*(1-i/4f);
            Nuklear.nk_stroke_line(canvas,plot.x(),y,plot.x()+plot.w(),y,1,Theme.grey05);
            Nuklear.nk_draw_text(canvas,label.set(bounds.x(),y-8,48,16),String.format(Locale.ROOT,"%.2f",ymax*i/4),nuklearModel.getDefaultFont(),Theme.colorChart,Theme.grey12);
            Nuklear.nk_draw_text(canvas,label.set(x-20,plot.y()+plot.h()+6,42,16),String.format(Locale.ROOT,"%.0f",xmin+(xmax-xmin)*i/4),nuklearModel.getDefaultFont(),Theme.colorChart,Theme.grey12);
        }
        boolean previous=false;float px=0,py=0;
        for(var row:rows) {
            if(!Double.isFinite(row.spreadPercent())){previous=false;continue;}
            float x=plot.x()+(float)((row.radius()-xmin)/(xmax-xmin))*plot.w(),y=plot.y()+plot.h()*(1-(float)(row.spreadPercent()/ymax));
            if(previous)Nuklear.nk_stroke_line(canvas,px,py,x,y,2,Theme.yellow);
            Nuklear.nk_fill_circle(canvas,NkRect.create().set(x-3,y-3,6,6),Theme.yellow);px=x;py=y;previous=true;
        }
        Nuklear.nk_push_scissor(canvas,prior);
    }
    private long presetRevision;
    private void drawExperimentPreset(NkContext ctx) {
        var path=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells","presets","last-experiment.ini");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
        if(Nuklear.nk_button_label(ctx,"Save experiment preset")) {
            try {remframework.settings.DiamondExperimentPreset.save(path,appSettings,project);presetStatus="Saved "+path;}
            catch(java.io.IOException error){presetStatus="Save failed: "+error.getMessage();}
        }
        if(Nuklear.nk_button_label(ctx,"Load saved experiment")) {
            project.scene.requestExperimentPreset(path);presetStatus="";
        }
        if(presetRevision!=project.scene.getExperimentPresetRevision()) {
            presetRevision=project.scene.getExperimentPresetRevision();
            wavelengthBuffer.setLength(0);amplitudeBuffer.setLength(0);toleranceBuffer.setLength(0);
            identityWarmupBuffer.setLength(0);identityCyclesBuffer.setLength(0);
            orbitPeriodBuffer.setLength(0);orbitRatioBuffer.setLength(0);orbitWidthBuffer.setLength(0);orbitAmplitudeBuffer.setLength(0);
            sweepPeriodBuffer.setLength(0);sweepTurnsBuffer.setLength(0);sweepCyclesBuffer.setLength(0);maximumUpdatesBuffer.setLength(0);
        }
        if(!project.scene.getExperimentPresetStatus().isEmpty()&&presetStatus.isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
            Nuklear.nk_label_wrap(ctx,project.scene.getExperimentPresetStatus());
        }
        if(!presetStatus.isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);Nuklear.nk_label_wrap(ctx,presetStatus);
        }
    }

    private void requestControlledProbeRun() {
                if(appSettings.isDiamondProbeSphericalSweep()) {
                    var sweep=new remframework.geometry.discovery.research.diamond.DiamondSphericalSweep(appSettings.getDiamondProbeSweepPeriod(),appSettings.getDiamondProbeSweepTurns(),appSettings.getDiamondProbeMeasurementCycles());
                    project.scene.getLattice().getDisturbance().requestSphericalProbeRun(controlledWavelength,appSettings.getDiamondProbeAmplitude(),selectedArrivalShell,
                        Math.pow(10,positionThresholdExponent),Math.pow(10,gradientThresholdExponent),sweep);
                } else
                project.scene.getLattice().getDisturbance().requestProbeRun(controlledWavelength,appSettings.getDiamondProbeAmplitude(),selectedArrivalShell,
                    Math.pow(10,positionThresholdExponent),Math.pow(10,gradientThresholdExponent),appSettings.getDiamondProbeExcitationAxis());
    }

    private void drawShellReport(NkContext ctx,List<ProbeShellArrivalReport.Row> rows) {
        section(ctx,"All-shell report — same run and thresholds");
        var report=project.scene.getProbeShellArrivalReport();
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        report.setRecordTraces(nk_check_label(ctx,"Record diagnostic traces (next run)",appSettings.isDiamondProbeRecordTraces(),.8,.2));
        report.setCycleDeadband(appSettings.getDiamondProbeCycleDeadband());
        scalar(ctx,"Full-cycle noise deadband (next run)",String.format(Locale.ROOT,"%.3g per signal",report.getCycleDeadband()));
        report.setCycleDeadband(Math.pow(10,Math.rint(nk_slider(ctx,-15,Math.log10(report.getCycleDeadband()),-1,1))));
        var sweepRows=report.sweepRows();
        appSettings.setDiamondProbeDiagnostics(report.getCycleDeadband(),report.isRecordTraces());
        if(!sweepRows.isEmpty()&&project.scene.getIdentityOrbitSpreadRun().period()==0) {
            section(ctx,"Whole spherical-cycle measurements");
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,"Each probe starts at the next source-period boundary after both thresholds are detected. Vector imbalance change is averaged over the configured whole periods. Perpendicular means perpendicular to the source-to-probe radius. This is separate from Z zero-crossing cycles and first-arrival spread.");
            for(var shell:rows) {
                var selected=sweepRows.stream().filter(r->r.shell()==shell.shell()).toList();
                long complete=selected.stream().filter(r->r.result().measurement().complete()).count();
                int minimum=selected.stream().mapToInt(r->r.result().measurement().completedCycles()).min().orElse(0);
                int target=selected.isEmpty()?0:selected.getFirst().result().measurement().requestedCycles();
                scalar(ctx,"S"+(shell.shell()+1)+" spherical windows",complete+"/"+selected.size()+" complete; minimum cycles "+minimum+"/"+target);
            }
            section(ctx,"Cycle-completed directional arrival statistics");
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,"First threshold arrivals are averaged across directions only after all spherical windows complete. Timing spread = 100 * (max - min) / mean; standard deviation is across directions. Times remain relative to source start, not the common window end. This does not average repeated received cycles.");
            for(var arrival:report.cycleArrivals()) {
                scalar(ctx,"S"+(arrival.shell()+1)+" "+arrival.signal()+" arrival",arrival.complete()
                    ?String.format(Locale.ROOT,"mean %.4f; min %.4f; max %.4f",arrival.mean(),arrival.minimum(),arrival.maximum()):"Waiting for all cycle windows");
                if(arrival.complete()) scalar(ctx,"Range / SD / timing spread",String.format(Locale.ROOT,
                    "%.4f / %.4f updates / %.4f%%",arrival.range(),arrival.standardDeviation(),arrival.spreadPercent()));
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Export cycle-completed arrival statistics CSV")) {
                try {
                    var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");
                    java.nio.file.Files.createDirectories(directory);
                    var path=directory.resolve("shell-cycle-arrivals-"+System.currentTimeMillis()+".csv");
                    try(var writer=java.nio.file.Files.newBufferedWriter(path)){report.writeCycleArrivals(writer);}
                    shellReportExportStatus="Saved "+path;
                } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Export spherical-cycle vector report CSV")) {
                try {
                    var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");
                    java.nio.file.Files.createDirectories(directory);
                    var path=directory.resolve("shell-sweeps-"+System.currentTimeMillis()+".csv");
                    try(var writer=java.nio.file.Files.newBufferedWriter(path)){report.writeSweeps(writer);}
                    shellReportExportStatus="Saved "+path;
                } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
            }
        }
        if(rows.isEmpty()) {
            scalar(ctx,"Recording","Run a controlled packet to collect every applied shell.");return;
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        showCycleTiming=nk_check_label(ctx,"Show full-cycle completion arrivals",showCycleTiming,.8,.2);
        if(showCycleTiming) {
            section(ctx,"First qualified full cycle per probe");
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,"Each probe independently measures rising zero -> falling zero -> rising zero in signed Z velocity and imbalance. Arrival is the update confirming the final crossing beyond the noise deadband. Start/end/period estimates are exported. Different probes may qualify different oscillations.");
            var cycles=report.cycleRows();
            for(var shell:rows)for(String signal:new String[]{"velocity_z","imbalance_z"}) {
                var selected=cycles.stream().filter(c->c.shell()==shell.shell()&&c.result().signal().equals(signal)).toList();
                var complete=selected.stream().map(c->c.result().cycle()).filter(remframework.opengl.model.ProbeCycleMeasurement.Result::complete).toList();
                String value=complete.size()+"/"+selected.size()+" complete";
                if(!selected.isEmpty()&&complete.size()==selected.size()) {
                    long min=complete.stream().mapToLong(remframework.opengl.model.ProbeCycleMeasurement.Result::arrivalUpdate).min().orElseThrow();
                    long max=complete.stream().mapToLong(remframework.opengl.model.ProbeCycleMeasurement.Result::arrivalUpdate).max().orElseThrow();
                    double period=complete.stream().mapToDouble(remframework.opengl.model.ProbeCycleMeasurement.Result::periodUpdates).average().orElseThrow();
                    value+=String.format(Locale.ROOT,"; arrival %d-%d (range %d); mean period %.3f",min,max,max-min,period);
                }
                scalar(ctx,"S"+(shell.shell()+1)+" "+signal,value);
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Export full-cycle arrival report CSV")) {
                try {
                    var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");
                    java.nio.file.Files.createDirectories(directory);
                    var file=directory.resolve("shell-cycles-"+System.currentTimeMillis()+".csv");
                    try(var writer=java.nio.file.Files.newBufferedWriter(file)){report.writeCycles(writer);}
                    shellReportExportStatus="Saved "+file;
                } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
            }
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        showRawThresholdReport=nk_check_label(ctx,"Show raw absolute-threshold report",showRawThresholdReport,.8,.2);
        if(showRawThresholdReport || !report.hasTraces()) {
        showShellArrivalTimes=nk_check_label(ctx,"Show arrival times instead of spread",showShellArrivalTimes,.8,.2);
        scalar(ctx,"Legend",showShellArrivalTimes?"Position = cyan | Gradient = yellow; bars = min/max arrival":"Position = cyan | Gradient = yellow; dashed grey = tolerance");
        drawShellDistanceChart(ctx,rows);
        for(boolean gradient:new boolean[]{false,true}) {
            if(!showShellArrivalTimes) {
                var trend=ProbeShellArrivalReport.spreadTrend(rows,gradient);
                scalar(ctx,gradient?"Gradient spread slope":"Position spread slope",trend.valid()
                    ?String.format(Locale.ROOT,"%+.6g pp/bond (%d shells)",trend.slope(),trend.shells())
                    :"Need at least two complete shells at different radii");
                continue;
            }
            var fit=ProbeShellArrivalReport.fit(rows,gradient);
            scalar(ctx,gradient?"Gradient slope / speed":"Position slope / speed",fit.valid()
                ?String.format(Locale.ROOT,"%.6g updates/bond; %.6g bonds/update (%d shells)",fit.slope(),fit.speed(),fit.shells())
                :"Need at least two complete shells with increasing arrival time");
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,showShellArrivalTimes?"Fit: mean arrival = initial delay + slope * radius. Speed = 1/slope. Only complete shells are fitted.":"Fit: spread (%) = intercept + slope * radius. Negative slope means spread decreases overall; positive means increases. pp = percentage points. A trend does not imply every shell decreases.");
        for(var row:rows)for(boolean gradient:new boolean[]{false,true}) {
            var t=gradient?row.gradient():row.position();
            scalar(ctx,String.format(Locale.ROOT,"S%d r=%.3g %s",row.shell()+1,row.radius(),gradient?"gradient":"position"),
                t.detected()==0?"No arrivals (0/"+t.total()+")":String.format(Locale.ROOT,"%d/%d; %d-%d; diff %d; mean %.2f; spread %s",t.detected(),t.total(),t.minimum(),t.maximum(),t.difference(),t.mean(),
                t.complete()?String.format(Locale.ROOT,"%.3f%%",t.spreadPercent()):"incomplete"));
        }
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        if(Nuklear.nk_button_label(ctx,"Export all-shell report CSV")) {
            try {
                var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");
                java.nio.file.Files.createDirectories(directory);
                var file=directory.resolve("shell-report-"+System.currentTimeMillis()+".csv");
                java.nio.file.Files.writeString(file,ProbeShellArrivalReport.csv(rows));
                shellReportExportStatus="Saved "+file;
            } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
        }
        if(report.hasTraces()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            showReversalTiming=nk_check_label(ctx,"Show signed Z reversal timing",showReversalTiming,.8,.2);
            if(showReversalTiming) {
                section(ctx,"Signed Z reversals within each shell");
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
                Nuklear.nk_label_wrap(ctx,"Current velocity, current imbalance and cycle-summary imbalance are recorded separately. Noise deadband: 1% of each shell/channel's maximum absolute signal. Ranges require every probe to match the same reversal. Cycle-summary changes use whole updates.");
                var reversals=report.reversalRows();
                if(reversals.isEmpty())scalar(ctx,"Reversals","Available after recording finishes.");
                for(var row:reversals) {
                    var complete=row.result().events().stream().filter(remframework.opengl.model.ProbeReversalAnalysis.Event::complete).toList();
                    var worst=complete.stream().max(java.util.Comparator.comparingDouble(remframework.opengl.model.ProbeReversalAnalysis.Event::range));
                    scalar(ctx,String.format(Locale.ROOT,"S%d %s",row.shell()+1,row.signal()),worst.isPresent()
                        ?String.format(Locale.ROOT,"largest range %.4f updates at ref %.3f; matched %d/%d events",worst.get().range(),worst.get().referenceUpdate(),complete.size(),row.result().events().size())
                        :row.result().events().isEmpty()?row.result().status():"No reversal matched by every probe");
                }
                if(!reversals.isEmpty()) {
                    Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
                    if(Nuklear.nk_button_label(ctx,"Export signed Z reversal report CSV")) {
                        try {
                            var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");
                            java.nio.file.Files.createDirectories(directory);
                            var file=directory.resolve("shell-reversals-"+System.currentTimeMillis()+".csv");
                            try(var writer=java.nio.file.Files.newBufferedWriter(file)){report.writeReversals(writer);}
                            shellReportExportStatus="Saved "+file;
                        } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
                    }
                }
            }
            var peaks=report.peakRows();
            if(peaks.isEmpty())scalar(ctx,"Matched peaks","Available after the recording finishes.");
            else {
                section(ctx,"Matched peak timing and amplitude");
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
                Nuklear.nk_label_wrap(ctx,"Half-peak timing uses the rising crossing at 50% of each probe's peak in one shared lobe. This avoids absolute-threshold lobe jumps. Ambiguous crossings remain unavailable. Lobe selection is threshold-anchored; this is not a front-speed measurement.");
                drawHalfPeakRangeChart(ctx,peaks);
                for(var peak:peaks) {
                    var result=peak.result();
                    scalar(ctx,String.format(Locale.ROOT,"S%d %s peak",peak.shell()+1,peak.gradient()?"gradient":"position"),
                        result.complete()?String.format(Locale.ROOT,"half-peak %s; peak %.3f updates; amplitude %.5g-%.5g",
                            result.halfPeakComplete()?String.format(Locale.ROOT,"%.3f updates",result.halfPeakTimingRange()):"unavailable",
                            result.timingRange(),result.minimumAmplitude(),result.maximumAmplitude()):result.status());
                }
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
                if(Nuklear.nk_button_label(ctx,"Export matched-peak report CSV")) {
                    try {
                        var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");
                        java.nio.file.Files.createDirectories(directory);
                        var file=directory.resolve("shell-peaks-"+System.currentTimeMillis()+".csv");
                        try(var writer=java.nio.file.Files.newBufferedWriter(file)){report.writePeaks(writer);}
                        shellReportExportStatus="Saved "+file;
                    } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
                }
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Export diagnostic traces CSV")) {
                try {
                    var directory=java.nio.file.Path.of(remframework.helpers.Constants.BASE_PATH,"measurements","diamond-shells");
                    java.nio.file.Files.createDirectories(directory);
                    var file=directory.resolve("shell-traces-"+System.currentTimeMillis()+".csv");
                    try(var writer=java.nio.file.Files.newBufferedWriter(file)){report.writeTraces(writer);}
                    shellReportExportStatus="Saved "+file;
                } catch(java.io.IOException error){shellReportExportStatus="Export failed: "+error.getMessage();}
            }
        }
        if(!shellReportExportStatus.isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
            Nuklear.nk_label_wrap(ctx,shellReportExportStatus);
        }
    }

    private void drawHalfPeakRangeChart(NkContext ctx,List<ProbeShellArrivalReport.PeakRow> rows) {
        section(ctx,"X: radius (bonds) | Y: half-peak timing range (updates)");
        scalar(ctx,"Legend","Position = cyan | Gradient = yellow; gaps = unavailable");
        Nuklear.nk_layout_row_dynamic(ctx,240,1);
        NkRect bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        NkRect plot=NkRect.create().set(bounds.x()+52,bounds.y()+10,Math.max(1,bounds.w()-64),Math.max(1,bounds.h()-42));
        var canvas=Nuklear.nk_window_get_canvas(ctx);var prior=NkRect.create().set(canvas.clip());
        Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));
        Nuklear.nk_fill_rect(canvas,bounds,0,Theme.colorChart);
        double xmin=rows.stream().mapToDouble(ProbeShellArrivalReport.PeakRow::radius).min().orElse(0);
        double xmax=rows.stream().mapToDouble(ProbeShellArrivalReport.PeakRow::radius).max().orElse(1);
        if(xmax<=xmin)xmax=xmin+1;
        double ymax=Math.max(1,rows.stream().filter(r->r.result().halfPeakComplete()).mapToDouble(r->r.result().halfPeakTimingRange()).max().orElse(1))*1.1;
        NkRect label=NkRect.create();
        for(int i=0;i<=4;i++) {
            float y=plot.y()+plot.h()-i*plot.h()/4;
            Nuklear.nk_stroke_line(canvas,plot.x(),y,plot.x()+plot.w(),y,1,Theme.grey05);
            label.set(bounds.x()+2,y-8,48,16);
            Nuklear.nk_draw_text(canvas,label,String.format(Locale.ROOT,"%.2f",ymax*i/4),nuklearModel.getDefaultFont(),Theme.colorChart,Theme.grey12);
            float x=plot.x()+i*plot.w()/4;
            label.set(x-16,plot.y()+plot.h()+6,40,16);
            Nuklear.nk_draw_text(canvas,label,String.format(Locale.ROOT,"%.0f",xmin+(xmax-xmin)*i/4),nuklearModel.getDefaultFont(),Theme.colorChart,Theme.grey12);
        }
        for(boolean gradient:new boolean[]{false,true}) {
            var color=gradient?Theme.yellow:Theme.blue4;
            boolean previous=false;float px=0,py=0;
            for(var row:rows) {
                if(row.gradient()!=gradient)continue;
                if(!row.result().halfPeakComplete()){previous=false;continue;}
                float x=plot.x()+(float)((row.radius()-xmin)/(xmax-xmin))*plot.w();
                float y=plot.y()+plot.h()-(float)(row.result().halfPeakTimingRange()/ymax)*plot.h();
                if(previous)Nuklear.nk_stroke_line(canvas,px,py,x,y,2,color);
                label.set(x-3,y-3,6,6);Nuklear.nk_fill_circle(canvas,label,color);
                previous=true;px=x;py=y;
            }
        }
        Nuklear.nk_push_scissor(canvas,prior);
    }

    private void drawShellDistanceChart(NkContext ctx,List<ProbeShellArrivalReport.Row> rows) {
        section(ctx,showShellArrivalTimes?"X: radius (bonds) | Y: arrival update":"X: radius (bonds) | Y: directional speed spread (%)");
        Nuklear.nk_layout_row_dynamic(ctx,260,1);
        NkRect bounds=NkRect.create();
        if(Nuklear.nk_widget(bounds,ctx)==Nuklear.NK_WIDGET_INVALID)return;
        NkRect plot=NkRect.create().set(bounds.x()+52,bounds.y()+10,Math.max(1,bounds.w()-64),Math.max(1,bounds.h()-42));
        var canvas=Nuklear.nk_window_get_canvas(ctx);
        var prior=NkRect.create().set(canvas.clip());
        Nuklear.nk_push_scissor(canvas,intersect(bounds,prior,NkRect.create()));
        Nuklear.nk_fill_rect(canvas,bounds,0,Theme.colorChart);
        double xmin=rows.stream().mapToDouble(ProbeShellArrivalReport.Row::radius).min().orElse(0);
        double xmax=rows.stream().mapToDouble(ProbeShellArrivalReport.Row::radius).max().orElse(1);
        if(xmax<=xmin){xmin=Math.max(0,xmin-1);xmax+=1;}
        double ymax=showShellArrivalTimes?1:speedSpreadTolerance;
        for(var row:rows)for(var t:List.of(row.position(),row.gradient())) {
            if(showShellArrivalTimes)ymax=Math.max(ymax,t.maximum());
            else if(t.complete() && Double.isFinite(t.spreadPercent()))ymax=Math.max(ymax,t.spreadPercent());
        }
        ymax*=1.05;
        var label=NkRect.create();
        for(int i=0;i<=4;i++) {
            float y=plot.y()+plot.h()*(1-i/4f),x=plot.x()+plot.w()*i/4f;
            Nuklear.nk_stroke_line(canvas,plot.x(),y,plot.x()+plot.w(),y,1,Theme.grey05);
            label.set(bounds.x(),y-8,48,16);
            Nuklear.nk_draw_text(canvas,label,String.format(Locale.ROOT,showShellArrivalTimes?"%.0f":"%.2f",ymax*i/4),nuklearModel.getDefaultFont(),Theme.colorChart,Theme.grey12);
            label.set(x-20,plot.y()+plot.h()+6,42,16);
            Nuklear.nk_draw_text(canvas,label,String.format(Locale.ROOT,"%.0f",xmin+(xmax-xmin)*i/4),nuklearModel.getDefaultFont(),Theme.colorChart,Theme.grey12);
        }
        if(!showShellArrivalTimes) {
            float y=plot.y()+plot.h()-(float)(speedSpreadTolerance/ymax)*plot.h();
            for(float x=plot.x();x<plot.x()+plot.w();x+=10)
                Nuklear.nk_stroke_line(canvas,x,y,Math.min(x+5,plot.x()+plot.w()),y,1,Theme.grey12);
        }
        for(boolean gradient:new boolean[]{false,true}) {
            var color=gradient?Theme.yellow:Theme.blue4;
            boolean previous=false;float px=0,py=0;
            for(var row:rows) {
                var t=gradient?row.gradient():row.position();
                if(!t.complete() || (!showShellArrivalTimes && !Double.isFinite(t.spreadPercent()))){previous=false;continue;}
                float x=plot.x()+(float)((row.radius()-xmin)/(xmax-xmin))*plot.w();
                float y=plot.y()+plot.h()-(float)((showShellArrivalTimes?t.mean():t.spreadPercent())/ymax)*plot.h();
                float low=plot.y()+plot.h()-(float)(t.minimum()/ymax)*plot.h();
                float high=plot.y()+plot.h()-(float)(t.maximum()/ymax)*plot.h();
                if(showShellArrivalTimes) {
                    Nuklear.nk_stroke_line(canvas,x,low,x,high,2,color);
                    Nuklear.nk_stroke_line(canvas,x-3,low,x+3,low,1,color);
                    Nuklear.nk_stroke_line(canvas,x-3,high,x+3,high,1,color);
                }
                if(previous)Nuklear.nk_stroke_line(canvas,px,py,x,y,2,color);
                label.set(x-3,y-3,6,6);Nuklear.nk_fill_circle(canvas,label,color);
                previous=true;px=x;py=y;
            }
        }
        Nuklear.nk_push_scissor(canvas,prior);
    }

    /** Draws measured first-change series with numeric probe and update axes. */
    private void drawArrivalChart(NkContext ctx, ProbeArrivalMeasurement.Snapshot snapshot) {
        Nuklear.nk_layout_row_dynamic(ctx, 280, 1);
        NkRect bounds = NkRect.create();
        if (Nuklear.nk_widget(bounds, ctx) == Nuklear.NK_WIDGET_INVALID) return;
        NkCommandBuffer canvas = Nuklear.nk_window_get_canvas(ctx);
        NkRect plot = NkRect.create().set(bounds.x() + 52, bounds.y() + 10,
                Math.max(1, bounds.w() - 64), Math.max(1, bounds.h() - 42));
        NkRect priorClip = NkRect.create().set(canvas.clip());
        NkRect chartClip = intersect(bounds, priorClip, NkRect.create());
        Nuklear.nk_push_scissor(canvas, chartClip);
        Nuklear.nk_fill_rect(canvas, bounds, 0, Theme.colorChart);

        ArrivalAxisRange yRange = arrivalAxisRange(snapshot);
        int yTicks = yRange.tickCount();
        NkRect labelBounds = NkRect.create();
        for (int tick = 0; tick <= yTicks; tick++) {
            long value = yRange.minimum() + yRange.tickStep() * tick;
            float y = arrivalY(plot, value, yRange);
            Nuklear.nk_stroke_line(canvas, plot.x(), y, plot.x() + plot.w(), y,
                    tick == 0 ? 2.0f : 1.0f, tick == 0 ? Theme.grey12 : Theme.grey05);
            labelBounds.set(bounds.x() + 2, y - 8, 46, 16);
            Nuklear.nk_draw_text(canvas, labelBounds, Long.toString(value),
                    nuklearModel.getDefaultFont(), Theme.colorChart, Theme.grey12);
        }

        int count = snapshot.results().size();
        int xStride = arrivalProbeTickStride(count);
        for (int index = 0; index < count; index++) {
            if (index % xStride != 0 && index != count - 1) continue;
            float x = arrivalX(plot, index, count);
            Nuklear.nk_stroke_line(canvas, x, plot.y() + plot.h(), x,
                    plot.y() + plot.h() + 4, 1.0f, Theme.grey12);
            labelBounds.set(x - 12, plot.y() + plot.h() + 7, 24, 16);
            Nuklear.nk_draw_text(canvas, labelBounds,
                    Integer.toString(snapshot.results().get(index).ordinal()),
                    nuklearModel.getDefaultFont(), Theme.colorChart, Theme.grey12);
        }

        drawArrivalSeries(canvas, plot, snapshot, yRange, 0, Theme.blue4);
        drawArrivalSeries(canvas, plot, snapshot, yRange, 1, Theme.yellow);
        Nuklear.nk_push_scissor(canvas, priorClip);
    }

    private static void drawArrivalSeries(
            NkCommandBuffer canvas, NkRect plot, ProbeArrivalMeasurement.Snapshot snapshot,
            ArrivalAxisRange yRange, int series, org.lwjgl.nuklear.NkColor color) {
        boolean hasPrevious = false;
        float previousX = 0.0f;
        float previousY = 0.0f;
        NkRect marker = NkRect.create();
        int count = snapshot.results().size();
        for (int index = 0; index < count; index++) {
            double arrival = arrivalValue(snapshot.results().get(index), series);
            if (!Double.isFinite(arrival) || arrival < 0) {
                hasPrevious = false;
                continue;
            }
            float x = arrivalX(plot, index, count);
            float y = arrivalY(plot, arrival, yRange);
            if (hasPrevious) {
                Nuklear.nk_stroke_line(canvas, previousX, previousY, x, y, 2.0f, color);
            }
            marker.set(x - 3, y - 3, 6, 6);
            Nuklear.nk_fill_circle(canvas, marker, color);
            previousX = x;
            previousY = y;
            hasPrevious = true;
        }
    }

    private static double arrivalValue(ProbeArrivalMeasurement.Result result, int series) {
        return switch (series) {
            case 0 -> result.positionEstimatedArrival();
            case 1 -> result.gradientEstimatedArrival();
            default -> result.combinedEstimatedArrival();
        };
    }

    record ArrivalAxisRange(long minimum, long maximum, long tickStep) {
        ArrivalAxisRange {
            if (minimum < 0 || maximum <= minimum || tickStep <= 0) {
                throw new IllegalArgumentException("Invalid arrival-axis range.");
            }
        }

        int tickCount() {
            return Math.toIntExact((maximum - minimum) / tickStep);
        }
    }

    static ArrivalAxisRange arrivalAxisRange(ProbeArrivalMeasurement.Snapshot snapshot) {
        long firstArrival = Long.MAX_VALUE;
        long lastArrival = Long.MIN_VALUE;
        for (ProbeArrivalMeasurement.Result result : snapshot.results()) {
            for (long arrival : new long[]{result.positionArrivalUpdate(),
                    result.gradientArrivalUpdate(), result.combinedArrivalUpdate()}) {
                if (arrival < 0) continue;
                firstArrival = Math.min(firstArrival, arrival);
                lastArrival = Math.max(lastArrival, arrival);
            }
        }
        if (firstArrival == Long.MAX_VALUE) return new ArrivalAxisRange(0, 4, 1);

        long spread = lastArrival - firstArrival;
        long padding = Math.max(1, (long) Math.ceil(Math.max(1, spread) * 0.1));
        long paddedMinimum = Math.max(0, firstArrival - padding);
        long paddedMaximum = lastArrival + padding;
        long tickStep = Math.max(1, (paddedMaximum - paddedMinimum + 3) / 4);
        long minimum = paddedMinimum / tickStep * tickStep;
        long maximum = (paddedMaximum + tickStep - 1) / tickStep * tickStep;
        return new ArrivalAxisRange(minimum, maximum, tickStep);
    }

    static int arrivalProbeTickStride(int probeCount) {
        return Math.max(1, (probeCount + 19) / 20);
    }

    private static float arrivalX(NkRect plot, int probeIndex, int probeCount) {
        if (probeCount <= 1) return plot.x() + plot.w() * 0.5f;
        return plot.x() + plot.w() * probeIndex / (probeCount - 1.0f);
    }

    private static float arrivalY(NkRect plot, double arrival, ArrivalAxisRange range) {
        double normalized = (arrival - range.minimum())
                / (double) (range.maximum() - range.minimum());
        return (float) (plot.y() + plot.h() * (1.0 - normalized));
    }

    private static NkRect intersect(NkRect first, NkRect second, NkRect destination) {
        float left = Math.max(first.x(), second.x());
        float top = Math.max(first.y(), second.y());
        float right = Math.min(first.x() + first.w(), second.x() + second.w());
        float bottom = Math.min(first.y() + first.h(), second.y() + second.h());
        return destination.set(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static String arrivalStatus(ProbeArrivalMeasurement.Snapshot snapshot) {
        if (!snapshot.hasMeasurement()) return "Not started";
        if (snapshot.complete()) return "Complete";
        return snapshot.running() ? "Measuring" : "Stopped";
    }

    private static String arrivalValue(long update) {
        return update < 0 ? "Waiting" : Long.toString(update);
    }
    private static String arrivalValue(double update) {
        return Double.isFinite(update)?String.format(Locale.ROOT,"%.3f",update):"Unavailable";
    }

    private static void section(NkContext ctx, String label) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 1);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
    }

    private static void naVector(NkContext ctx, String label) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, "N/A", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "N/A", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "N/A", Nuklear.NK_TEXT_RIGHT);
    }

    private static void scalar(NkContext ctx, String label, String value) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, value, Nuklear.NK_TEXT_RIGHT);
    }

    private static String calibrationDirectionLabel(Scene.CalibrationDirection direction) {
        return switch (direction) {
            case NEGATIVE_X -> "−X";
            case POSITIVE_Y -> "+Y";
            case POSITIVE_Z -> "+Z";
        };
    }

    private int diamondProbeTab;
    private void diamondProbeSection(NkContext ctx) {
        drawExperimentPreset(ctx);
        drawRotatingOrbit(ctx);
        var scene=project.scene;
        section(ctx,"Diamond physical probe sampling");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        if(nk_button_label(ctx,"Add / replace diamond probe shells..."))nuklearModel.showFrame(NuklearModel.Frame.addProbes);
        scalar(ctx,"Probe installation",scene.getDiamondProbeStatus());
        var probes=scene.getProbes().stream().filter(p->p.getDetectorType()==remframework.opengl.model.ProbeDetectorType.DIAMOND_INTERPOLATED).toList();
        scalar(ctx,"Passive probes",Integer.toString(probes.size()));
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"Physical coordinates and raw bond imbalance; probes exert no force or pressure. Histories contain per-update samples, not cubic phase averages.");
        if(scene.getLattice().isDiamondTransportEnabled()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
            Nuklear.nk_label_wrap(ctx,"Disable transport for mechanical isotropy measurements.");
            if(nk_button_label(ctx,"Pause and disable Identity transport"))scene.getLattice().setDiamondTransportEnabled(false);
        }
        if(probes.isEmpty())return;
        var selected=scene.getSelectedProbe();
        if(selected==null || !probes.contains(selected)) {scene.selectProbe(probes.getFirst());selected=probes.getFirst();}
        try(MemoryStack stack=MemoryStack.stackPush()) {
            String[] ids=probes.stream().map(PassiveObserver::getId).toArray(String[]::new);
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            String id=nk_combo_options(ctx,stack,ids,selected.getId());
            for(var probe:probes)if(probe.getId().equals(id)){scene.selectProbe(probe);selected=probe;break;}
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),3);
        if(nk_button_label(ctx,"Live / history"))diamondProbeTab=0;
        if(nk_button_label(ctx,"Compare"))diamondProbeTab=1;
        if(nk_button_label(ctx,"Propagation arrival"))diamondProbeTab=2;
        if(diamondProbeTab==2) {arrivalSection(ctx);return;}
        if(diamondProbeTab==1) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
            if(nk_button_label(ctx,"Toggle selected in A / B"))scene.toggleComparisonProbe(selected);
            Nuklear.nk_label(ctx,"Assigns the next empty slot",Nuklear.NK_TEXT_LEFT);
            comparisonSection(ctx);return;
        }
        var target=selected.getDefinition().getPlacement().targets().getFirst();
        scalar(ctx,"Shell / radius",(target.shell()+1)+" / "+target.requestedRadius()+" bond lengths");
        diamondVector(ctx,"Requested physical direction",target.requestedDirection());
        diamondVector(ctx,"Equilibrium target",target.requestedCoordinate());
        var observation=selected.getObservation();
        diamondVector(ctx,"Displacement",new Vector3d(observation.position()).sub(target.requestedCoordinate()));
        diamondVector(ctx,"Velocity",observation.velocity());
        diamondVector(ctx,"Raw local imbalance",observation.gradientImbalance());
        if(nk_button_label(ctx,"Open selected probe history"))nuklearModel.showFrame(NuklearModel.Frame.occupancyHistory);
    }
    private void diamondTransportSection(NkContext ctx) {
        var lattice=project.scene.getLattice();
        var transport=remframework.opengl.controller.TransportSystem.getInstance();
        section(ctx,"Local Identity transport — live diagnostics");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
        if(nk_button_label(ctx,"Preferences / enable transport"))nuklearModel.showFrame(NuklearModel.Frame.preferences);
        if(nk_button_label(ctx,"Add / edit Identities"))nuklearModel.showFrame(NuklearModel.Frame.identity);
        scalar(ctx,"Occupancy pressure",lattice.isDiamondPressureEnabled()?"Enabled":"Disabled");
        scalar(ctx,"Diamond transport",lattice.isDiamondTransportEnabled()?"Enabled":"Disabled");
        scalar(ctx,"Transport System switch",transport.isEnableTransport()?"Enabled":"Disabled");
        scalar(ctx,"Transfer threshold",format(transport.getTransferThreshold()));
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"Enable local Identity transport in Preferences. Select an Identity below, then Run. Equal best directions wait; fixed nodes and walls are excluded.");
        for(Occupancy candidate:project.occupancies) {
            if(candidate.getIdentity()==null)continue;
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(nk_button_label(ctx,"Select "+candidate.getIdentity().getName()))
                project.getRemModel().setSelectedOccupancy(candidate);
        }
        Occupancy selected=project.getRemModel().getSelectedOccupancy();
        if(selected==null || selected.getIdentity()==null) {
            scalar(ctx,"Selected Identity","None — add one in Identities");
        } else {
            synchronized(selected) {
                section(ctx,selected.getIdentity().getName());
                var m=selected.diamondTransportMeasurement();
                if(selected.getNode()!=null)diamondVector(ctx,"Current node index",selected.getNodeIndex());
                diamondVector(ctx,"Last source imbalance",selected.getGradientImbalance());
                diamondVector(ctx,"Pressure / residual after update",selected.getPressure());
                diamondVector(ctx,"Persistent momentum",selected.getTransportMomentum());
                scalar(ctx,"Transport updates / transfers",m.updates()+" / "+m.transfers());
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
                Nuklear.nk_label_wrap(ctx,"Result: "+m.status());
                diamondVector(ctx,"Last hop",m.lastHop());
                diamondVector(ctx,"Pressure consumed on last update",m.consumedPressure());
                diamondVector(ctx,"Accumulated hop displacement",m.displacement());
                scalar(ctx,"Travelled path length",format(m.pathLength()));
                scalar(ctx,"Mean hop displacement / update",m.updates()==0?"Not measured":format(m.displacement().length()/m.updates()));
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
                if(nk_button_label(ctx,"Reset selected measurements (preserves pressure)"))selected.resetDiamondTransportMeasurement();
            }
        }
        section(ctx,"Isotropy assessment");
        section(ctx,"Directional spread: not measured");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"A moving Identity is a transport diagnostic. The 1% isotropy criterion requires equivalent directional experiments and boundary/sampling checks in later steps.");
    }

    private static void diamondVector(NkContext ctx, String label, Vector3d value) {
        section(ctx,label);
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),3);
        Nuklear.nk_label(ctx,format(value.x),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,format(value.y),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,format(value.z),Nuklear.NK_TEXT_RIGHT);
    }
    private static void diamondVector(NkContext ctx, String label, org.joml.Vector3i value) {
        section(ctx,label);
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),3);
        Nuklear.nk_label(ctx,Integer.toString(value.x),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,Integer.toString(value.y),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,Integer.toString(value.z),Nuklear.NK_TEXT_RIGHT);
    }
    private static int rowHeightFor(NkContext ctx) {
        return INSPECTOR_ROW_HEIGHT;
    }

    static int inspectorContentHeight(int rowCount) {
        return Math.max(0, rowCount) * INSPECTOR_ROW_HEIGHT;
    }

    static int inspectorRowHeight() {
        return INSPECTOR_ROW_HEIGHT;
    }

    static int inspectorScrollMaximum(int contentHeight, int visibleHeight) {
        return Math.max(0, contentHeight - Math.max(0, visibleHeight));
    }

    static int[] clampInspectorPosition(int x, int y, int width, int height,
                                         int viewportWidth, int viewportHeight, int minimumY) {
        int maxX = Math.max(0, viewportWidth - width);
        int maxY = Math.max(minimumY, viewportHeight - height);
        return new int[]{Math.max(0, Math.min(x, maxX)),
                Math.max(minimumY, Math.min(y, maxY))};
    }

    private void positionNearInspectionCluster(NkRect rect, int width, int height) {
        PassiveObserver probe = project.scene.getSelectedProbe();
        if (probe == null) return;
        Vector3d anchor = project.scene.getInspectionFocusNode() == null
                ? probe.getObservation().position()
                : project.scene.getInspectionFocusNode().toWorldPosition();
        int[] viewport = {0, 0, appSettings.getWindowWidth(), appSettings.getWindowHeight()};
        Vector3f screen = new Matrix4f(project.camera.getProjectionMatrix())
                .mul(project.camera.getViewMatrix())
                .project(new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z),
                         viewport, new Vector3f());
        int proposedX = Math.round(screen.x + 24.0f);
        int proposedY = Math.round(appSettings.getWindowHeight() - screen.y - height * 0.35f);
        int[] position = clampInspectorPosition(proposedX, proposedY, width, height,
                                                appSettings.getWindowWidth(),
                                                appSettings.getWindowHeight(),
                                                nuklearModel.getMenuBarHeight() + 5);
        rect.x(position[0]).y(position[1]).w(width).h(height);
    }

    private static void vector(NkContext ctx, String label, Vector3d value) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, format(value.x), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, format(value.y), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, format(value.z), Nuklear.NK_TEXT_RIGHT);
    }

    private static void vector(NkContext ctx, String label, org.joml.Vector3i value) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, Integer.toString(value.x), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, Integer.toString(value.y), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, Integer.toString(value.z), Nuklear.NK_TEXT_RIGHT);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "% .6e", value);
    }
}
