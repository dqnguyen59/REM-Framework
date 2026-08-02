package remframework.gui.view.frames;
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

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.gui.model.NuklearModel;
import remframework.helpers.Constants;

/**
 * Context and guidance panel for the integrated discovery workspace.
 */
public final class GeometryDiscoverySearchFrame extends AbstractFrame {
    private final WorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();

    public GeometryDiscoverySearchFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER;
    }

    @Override public String getTitle() { return "Current Status"; }

    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                GeometryDiscoveryWorkspaceLayout.calculate(getWindowWidth(), getWindowHeight(),
                        nuklearModel.getMenuBarHeight() + 4,
                        Constants.STATUS_BAR_HEIGHT).status();
        createFixedLayout(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        heading(c, "Stage " + workflow.stage().number() + ": " + workflow.stage().label());
        label(c, "Experiment state: " + display(workflow.state().name()));
        label(c, "Candidate: " + snapshot.geometry().model().name());
        DiscoveryExperimentConfiguration experiment =
                controller.state().experimentConfiguration();
        if (experiment != null) {
            label(c, "Experiment: " + experiment.name());
            label(c, "Workflow phase: " + display(
                    controller.state().experimentLifecycle().name()));
        }
        label(c, "Scoring profile: " + display(controller.state().scoringProfile().name()));
        stage2Status(c);
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(c, "Current Candidate"))
            controller.state().setViewportSelection(
                    DiscoveryViewportSelection.CURRENT_CANDIDATE);
        if (Nuklear.nk_button_label(c, "Best Candidate"))
            controller.state().setViewportSelection(
                    DiscoveryViewportSelection.BEST_CANDIDATE);
        if (Nuklear.nk_button_label(c, "Preview Candidate"))
            controller.state().setViewportSelection(
                    DiscoveryViewportSelection.PREVIEW_CANDIDATE);
        if (controller.state().guidedMode()) {
            heading(c, "Recommended Next Action");
            label(c, workflow.recommendation().action());
            label(c, workflow.recommendation().explanation());
        }
        if (snapshot.error() != null && !snapshot.error().isBlank()) {
            heading(c, "Error");
            label(c, snapshot.error());
        }
        if (snapshot.search() != null) {
            TopologySearchResult search = snapshot.search();
            TopologySearchMetrics metrics = search.metrics();
            heading(c, "Search Progress");
            int maximum = search.settings().maximumSearchIterations();
            label(c, "Iteration: " + metrics.completedIterations() + " / " + maximum);
            label(c, String.format("Completion: %.1f%%", maximum == 0 ? 100
                    : 100.0 * metrics.completedIterations() / maximum));
            label(c, String.format("Current score: %.7g",
                    search.currentCandidate().score().total()));
            label(c, String.format("Best score: %.7g",
                    search.bestCandidate().score().total()));
            label(c, "Accepted: " + metrics.acceptedMutationCount()
                    + "    Rejected: " + metrics.rejectedMutationCount());
            label(c, "Invalid: " + metrics.invalidMutationCount()
                    + "    Duplicates: " + metrics.duplicateTopologyCount());
            label(c, "Relaxation failures: " + metrics.relaxationFailureCount());
            label(c, String.format("Temperature: %.6g",
                    search.history().isEmpty() ? search.settings().initialTemperature()
                            : search.history().getLast().temperature()));
            label(c, "Elapsed: " + metrics.elapsedMilliseconds() + " ms");
        } else if (snapshot.relaxationRunning()) {
            GeometryDiscoveryMetrics metrics = snapshot.geometry().metrics();
            int maximum = snapshot.geometry().settings().maximumIterations();
            label(c, "Relaxation iteration: " + metrics.iterationCount() + " / " + maximum);
            label(c, String.format("Relaxation completion: %.1f%%",
                    100.0 * metrics.iterationCount() / maximum));
            label(c, String.format("Current edge error: %.7g",
                    metrics.totalEdgeLengthError()));
        }
    }

    private void stage2Status(NkContext c){PhasedWorkflow workflow=controller.phasedWorkflow();IncrementalSession session=workflow.incrementalSession();if(session==null)return;ConstraintPresentationSummary summary=session.constraintPresentation();ContinuationSnapshot continuation=session.continuationSnapshot();
        heading(c,"Current Geometry Test");label(c,"Configuration: "+session.configuration().id());label(c,"Execution: "+session.executionState());label(c,"Strategy: "+continuation.strategy());label(c,"Current stage: "+ScientificDisplayNames.stage(continuation.stageId())+" - "+continuation.state());label(c,"Required constraints: "+summary.requiredPassed()+" / "+summary.requiredTotal()+" passed");label(c,"Worst constraint: "+summary.weakestRequiredConstraint());label(c,"Worst ratio: "+formatRatio(summary.worstRequiredRatio()));label(c,"Overall: "+(summary.qualified()?"QUALIFIED":"NOT QUALIFIED"));
        label(c,"Realization "+session.realizationIndex()+" - seed "+session.realizedSeed()+" - iteration "+session.iteration());ScientificTimingSnapshot timing=session.timing();label(c,String.format("Updates/s requested %d, achieved %.3f (%.2f ms/iteration)",workflow.scientificUpdatesPerSecond(),timing.achievedUpdatesPerSecond(),timing.averageIterationMillis()));
        if(continuation.strategy()==RealizationStrategy.SEQUENTIAL_CONTINUATION){heading(c,"Continuation - Stage "+continuation.stageIndex()+" of "+continuation.stageCount());label(c,ScientificDisplayNames.stage(continuation.stageId())+" - "+continuation.state());label(c,String.format("Ramp %.1f%% - stable %d / %d",100*continuation.rampFactor(),continuation.stableIterations(),continuation.requiredStableIterations()));for(ContinuationStageDefinition stage:session.request().continuation().schedule().stages()){String marker=continuation.history().stream().anyMatch(x->x.stageId().equals(stage.id())&&x.state()==ContinuationStageState.SATISFIED)?"[x]":stage.id().equals(continuation.stageId())?"->":"[ ]";label(c,marker+" "+ScientificDisplayNames.stage(stage.id()));}}
        groupedConstraints(c,summary);selectedElement(c,session);if(!session.stopReason().isBlank())label(c,"Latest reason: "+session.stopReason());}

    private void groupedConstraints(NkContext c,ConstraintPresentationSummary summary){constraintGroup(c,"Shape Construction",summary,"edge-equality","outward-face-orientation","face-planarity","face-angle-equality","face-plane-radius-equality");constraintGroup(c,"Regularity Measurements",summary,"dihedral-equality","vertex-radius-equality","face-centroid-radius-equality","edge-midpoint-radius-equality","normal-center-alignment","equal-face-area","convexity");constraintGroup(c,"Validation",summary,"closed-manifold","coherent-orientation","self-intersection","positive-volume","joint-qualification");}
    private void constraintGroup(NkContext c,String title,ConstraintPresentationSummary summary,String...ids){heading(c,title);for(String id:ids)summary.constraints().stream().filter(row->row.constraintId().equals(id)).findFirst().ifPresent(row->{label(c,row.displayName());if(!row.applicable()){label(c,"NOT APPLICABLE");return;}label(c,row.mode()+" - "+row.status());if(row.errorRatio()!=null){label(c,"Error "+format(row.rawError())+" / Tolerance "+format(row.tolerance()));label(c,"Ratio "+formatRatio(row.errorRatio().value()));}else if(Double.isFinite(row.rawError()))label(c,"Current "+row.metricName()+": "+format(row.rawError()));tooltip(c,ScientificDisplayNames.tooltip(id)+" "+row.explanation());});}
    private void selectedElement(NkContext c,IncrementalSession session){SelectedElement selected=controller.viewState().selectedElement();RenderModel model=controller.phasedWorkflow().renderModel();if(selected==null||model==null)return;SelectedElementMeasurements m;try{m=ElementMeasurementCalculator.measure(model,selected,session.request().targetEdgeLength(),session.request().edgeTolerance());}catch(RuntimeException ignored){return;}heading(c,"Selected Element");label(c,"Type: "+selected.type()+"    ID: "+selected.id());if(m.position()!=null)label(c,"Position: "+vector(m.position()));if(selected.type()==SelectedElement.Type.VERTEX){label(c,"Degree: "+m.vertexIds().size()+"    Neighbors: "+m.vertexIds());label(c,"Incident edges: "+m.edgeIds());label(c,"Incident faces: "+m.faceIds());label(c,"Vertex Distance: "+format(m.centerDistance()));}else if(selected.type()==SelectedElement.Type.EDGE){label(c,"Vertices: "+m.vertexIds());label(c,"Length: "+format(m.length())+" / target "+format(m.targetLength()));label(c,"Absolute error: "+format(m.error())+"    Ratio: "+formatRatio(m.ratio()));label(c,"Midpoint: "+vector(m.midpoint()));label(c,"Edge-Center Distance: "+format(m.centerDistance()));label(c,"Incident faces: "+m.faceIds());}else if(selected.type()==SelectedElement.Type.FACE){label(c,"Vertices: "+m.vertexIds());label(c,"Edges: "+m.edgeIds());label(c,"Area: "+format(m.area())+"    Perimeter: "+format(m.perimeter()));label(c,"Planarity error: "+format(m.planarityError()));label(c,"Angles radians: "+m.angles());label(c,"Angles degrees: "+m.angles().stream().map(x->String.format("%.3f",Math.toDegrees(x))).toList());label(c,"Centroid: "+vector(m.centroid()));label(c,"Normal: "+vector(m.normal()));label(c,"Orientation: "+m.state()+"    signed alignment: "+format(m.outwardAlignment()));label(c,"Face Distance: "+format(m.facePlaneDistance()));label(c,"Face-Center Distance: "+format(m.faceCentroidDistance()));}}
    private static String vector(org.joml.Vector3d v){return String.format("(%.6g, %.6g, %.6g)",v.x,v.y,v.z);}private static String format(double v){return Double.isFinite(v)?String.format("%.6g",v):"unavailable";}

    private static String constraintRow(ConstraintPresentation row){
        String error=Double.isFinite(row.rawError())?String.format("%.6g %s",row.rawError(),row.errorUnits()):"unavailable";
        String tolerance=row.errorRatio()==null?"not used":Double.isFinite(row.tolerance())?String.format("%.6g %s",row.tolerance(),row.toleranceUnits()):"invalid";
        String ratio=row.errorRatio()==null?"unavailable":formatRatio(row.errorRatio().value());
        String strength=row.restoringStrength()==null?"disabled":String.format("%.4g",row.restoringStrength());
        return row.displayName()+" | "+row.mode()+" | "+error+" | "+tolerance+" | "+ratio+" | "+strength+" | "+row.status();
    }
    private static String formatRatio(double ratio){return Double.isNaN(ratio)?"unavailable":Double.isInfinite(ratio)?"+INFINITY":String.format("%.3f",ratio);}

    private void heading(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 3, 1);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFontBold());
        Nuklear.nk_label_wrap(c, text);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFont());
    }

    private void label(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeight + 3, 1);
        Nuklear.nk_label_wrap(c, text);
    }

    private String display(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
