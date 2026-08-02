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
import remframework.geometry.discovery.measurement.regularity.*;
import remframework.geometry.discovery.reference.*;
import remframework.geometry.discovery.experiment.BenchmarkPreset;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.gui.model.NuklearModel;
import remframework.helpers.Constants;

import java.util.List;

/**
 * Context-sensitive bottom details area. Metrics are separated into tabs instead
 * of being presented as one dense paragraph.
 */
public final class GeometryDiscoveryMetricsFrame extends AbstractFrame {
    private final WorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();
    private int phase2DetailsTab;
    private static final String[] PHASE2_TABS = {"Summary", "Topology", "Classification", "Parameter Derivation", "Generation Strategy", "Face Composition", "Eligibility",
            "Realization", "Faces and Boundary", "2D Regularity", "3D Regularity", "Measurement", "Diagnostics"};

    public GeometryDiscoveryMetricsFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER | Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }

    @Override public String getTitle() { return "Discovery Details"; }

    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                GeometryDiscoveryWorkspaceLayout.calculate(getWindowWidth(), getWindowHeight(),
                        nuklearModel.getMenuBarHeight() + 4,
                        Constants.STATUS_BAR_HEIGHT).details();
        createFixedLayout(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        if (controller.phasedWorkflow().generation().isPresent()) {
            phase2Overview(c);
            return;
        }
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, GeometryDiscoveryDetailTab.values().length);
        for (GeometryDiscoveryDetailTab tab : GeometryDiscoveryDetailTab.values())
            if (Nuklear.nk_button_label(c, tab.label()))
                controller.state().setSelectedDetailTab(tab);
        GeometryDiscoveryResult result = snapshot.geometry();
        switch (controller.state().selectedDetailTab()) {
            case OVERVIEW -> overview(c, snapshot);
            case GEOMETRY -> geometry(c, result.metrics());
            case TOPOLOGY -> topology(c, new TopologyMetricsCalculator().calculate(result.model()));
            case DIRECTIONAL -> directional(c,
                    new DirectionalBalanceCalculator().calculate(result.model()));
            case SCORE -> score(c, controller.evaluatedCurrentCandidate().score());
            case SELECTED_NODE -> selectedNode(c, snapshot);
            case HISTORY -> history(c, snapshot);
        }
    }

    private void phase2Overview(NkContext c) {
        GenerationResult generation = controller.phasedWorkflow().generation().orElseThrow();
        heading(c, "Discovery Details - Geometry Discovery");
        ConfigurationId selected = controller.phasedWorkflow().selectedConfiguration().orElse(null);
        label(c, "Selected configuration: " + (selected == null ? "None" : selected));
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, PHASE2_TABS.length);
        for (int i = 0; i < PHASE2_TABS.length; i++)
            if (Nuklear.nk_button_label(c, (phase2DetailsTab == i ? "> " : "") + PHASE2_TABS[i])) phase2DetailsTab = i;
        Nuklear.nk_layout_row_dynamic(c, 260, 1);
        if (Nuklear.nk_group_begin(c, "GeometryDiscoveryPhase2Details", 0)) {
            Configuration configuration = selected == null ? null
                    : controller.phasedWorkflow().selectedConfigurationRecord().orElse(null);
            TestResult result = selected == null ? null : controller.phasedWorkflow().latestGeometryTest(selected);
            switch (phase2DetailsTab) {
                case 0 -> phase2Summary(c, generation, configuration, result);
                case 1 -> phase2Topology(c, configuration);
                case 2 -> phase2Classification(c, generation, configuration);
                case 3 -> phase2ParameterDerivation(c,generation);
                case 4 -> phase2GenerationStrategy(c,generation);
                case 5 -> phase2FaceComposition(c,generation,configuration);
                case 6 -> phase2Eligibility(c,result);
                case 7 -> phase2Realization(c, result);
                case 8 -> phase2FacesBoundary(c, result);
                case 9 -> phase2Regularity2d(c,result);
                case 10 -> phase2Regularity3d(c,result);
                case 11 -> phase2Measurement(c, result);
                case 12 -> phase2Diagnostics(c, generation, configuration, result);
                default -> { }
            }
            Nuklear.nk_group_end(c);
        }
    }

    private void phase2GenerationStrategy(NkContext c,GenerationResult generation){GenerationDiagnostics g=generation.generationDiagnostics();label(c,"Strategy: "+g.strategy()+" version="+g.strategyVersion());label(c,"Construction method: "+g.constructionMethod());label(c,"Normalized parameters: "+generation.request().normalizedGraphRequest());label(c,"Completeness: "+g.completeness());label(c,"Search limits: "+g.searchLimits());label(c,"States examined="+g.partialStatesExamined()+" pruned="+g.partialStatesPruned());label(c,"Completed candidates="+g.completedCandidates()+" duplicate candidates="+g.duplicateCandidates()+" retained="+g.retainedConfigurations());label(c,"Pruning diagnostics: "+g.pruningCounts());label(c,g.explanation());}

    private void phase2ParameterDerivation(NkContext c,GenerationResult generation){
        ParameterDerivationReport d=generation.request().parameterDerivation();
        label(c,"Primary parameter mode: "+d.primaryParameterMode());
        label(c,"Surface topology assumption: "+d.surfaceTopologyAssumption()+" chi="+d.eulerCharacteristic());
        label(c,"Authoritative inputs: "+d.authoritativeInputs());
        label(c,"Derived V="+d.derivedVertexCount()+" E="+d.derivedEdgeCount()+" F="+d.derivedFaceCount());
        d.formulas().forEach(formula->label(c,"Formula: "+formula));
        d.divisibilityChecks().forEach(check->label(c,"Divisibility: "+check));
        label(c,"Degree incidence: "+d.degreeIncidenceCalculation());
        label(c,"Face incidence: "+d.faceIncidenceCalculation());
        label(c,"Arithmetic feasibility: "+(d.arithmeticFeasible()?"PASS":"FAIL"));
        label(c,"Selected generation strategy: "+d.selectedGenerationStrategy());
        label(c,"Current generation capability: "+(d.generationStrategyAvailable()?"AVAILABLE":"UNAVAILABLE / LIMIT REACHED"));
        label(c,"Graph enumeration completeness: "+generation.metrics().enumerationComplete());
        label(c,"Generation status: "+generation.status().code());
        label(c,"Exact reasons: "+d.reasons());
    }

    private void phase2FaceComposition(NkContext c,GenerationResult generation,
            Configuration configuration) {
        FaceComposition selected=generation.request().faceComposition();
        label(c,"Selected mode: "+selected.mode());
        label(c,"Common face size: "+(selected.commonFaceEdgeCount()==null?"Not imposed":selected.commonFaceEdgeCount()));
        if(selected.mode()==FaceCompositionMode.UNIFORM_FACE_SIZE){
            FaceCompositionArithmetic a=FaceCompositionArithmetic.evaluate(generation.request().nodeCount(),
                    generation.request().exactDegree(),selected.commonFaceEdgeCount(),
                    generation.request().experimentProfile());
            label(c,a.explanation());label(c,"N="+a.nodeCount()+" K="+a.exactDegree()+" m="+a.commonFaceEdgeCount());
            label(c,"Expected E="+a.expectedEdgeCount()+" F="+a.expectedFaceCount()
                    +" mF="+a.expectedFaceEdgeIncidences()+" 2E="+a.requiredEdgeFaceIncidences());
            label(c,"Matching topology: "+a.matchingTopology());
        }
        if(configuration==null){label(c,"Select a configuration for its topology-derived face result.");return;}
        var report=new TopologyEligibilityAnalyzer().analyze(configuration,
                generation.request().experimentProfile(),selected).faceComposition();
        label(c,"Actual derived face count: "+report.actualFaceCount());
        label(c,"Actual face-size distribution: "+report.faceEdgeCountDistribution());
        label(c,"Match result: "+report.match());label(c,"ConfigurationStatus reasons: "+report.reasons());
        for(List<Integer> face:report.faces())label(c,"Canonical face cycle: "+face);
    }

    private void phase2Summary(NkContext c, GenerationResult generation, Configuration configuration, TestResult result) {
        if (configuration == null) { label(c, "No configuration selected."); return; }
        label(c, "Configuration ID: " + configuration.id());
        label(c, "Generation status: " + configuration.status().code() + " - " + configuration.status().summary());
        label(c, "N=" + configuration.nodeCount() + "    K=" + configuration.exactDegree() + "    Edges=" + configuration.edges().size());
        label(c, "Realization: " + (result == null ? "Not tested" : result.realization().converged() ? "EQUAL_EDGE_REALIZATION_FOUND" : result.realization().status()));
        label(c, "Boundary: " + (result == null || result.boundary() == null ? "Undetermined" : result.boundary().status().code()));
        label(c, "Candidate eligibility: " + (result != null && result.status().code() == GeometryConfigurationStatusCode.VALID_CANDIDATE ? "Yes" : "No"));
        if(result!=null&&result.benchmarkPreset()!=BenchmarkPreset.NONE){label(c,"Benchmark preset: "+result.benchmarkPreset());label(c,"Required cube topology match: "+(result.benchmarkTopologyRequirementPassed()?"PASS":"FAIL"));label(c,"Selected benchmark: "+(result.selectedBenchmarkPassed()?"PASSED":"FAILED"));}
        label(c, "N and K constrain vertex count and degree only. They do not uniquely determine topology or geometry.");
    }

    private void phase2Topology(NkContext c, Configuration configuration) {
        if (configuration == null) { label(c, "No configuration selected."); return; }
        TopologyInspection t = TopologyInspection.from(configuration);
        label(c, "Canonical encoding: " + configuration.canonicalGraphLabel());
        label(c, "Connected=" + configuration.connected() + " simple=" + t.simpleGraph() + " regular=" + t.degreeMismatches().isEmpty());
        for (String line : t.adjacencyLines()) label(c, line);
        label(c, "Canonical undirected edge list:");
        for (String line : t.edgeLines()) label(c, line);
        label(c, "Degrees: " + java.util.stream.IntStream.range(0, t.adjacency().size()).mapToObj(i -> i + "=" + t.adjacency().get(i).size()).collect(java.util.stream.Collectors.joining(", ")));
    }

    private void phase2Classification(NkContext c, GenerationResult generation, Configuration configuration) {
        if (configuration == null) { label(c, "No configuration selected."); return; }
        label(c, "Automorphism-group size: " + configuration.automorphismGroupSize());
        label(c, "Graph symmetry: " + configuration.graphSymmetry());
        label(c, "Vertex transitivity: " + (configuration.graphSymmetry() == GraphSymmetryClassification.HIGHLY_SYMMETRIC ? "Yes" : "No / not classified as highly symmetric"));
        label(c, "Enumeration completeness: " + configuration.enumerationComplete());
        ReferenceTopologyMatch match = new ReferenceTopologyRecognizer().recognize(configuration);
        label(c, "Reference topology: " + match.topology());
        label(c, "Exact reference match: " + (match.exactMatch() ? "Yes" : "No"));
        label(c, "Matching method: " + match.matchingMethod());
        label(c, "Reference version: " + match.referenceVersion());
        label(c, match.explanation());
    }

    private void phase2Realization(NkContext c, TestResult result) {
        IncrementalSession session = controller.phasedWorkflow().incrementalSession();
        if (session == null && result == null) { label(c, "Equal-edge realization: Not tested"); return; }
        if (session != null) {
            label(c, "Execution state: " + session.executionState());
            label(c, "Scientific state: " + session.scientificState());
            label(c, "Iteration: " + session.iteration() + "    Convergence iteration: " + session.convergenceIteration());
            label(c, "Edge-length error: " + session.metrics().meanDistanceAccuracy());
            ExperimentConvergenceDiagnostics d=session.convergenceDiagnostics();
            label(c,"Edge-length constraint: "+d.edgeConstraint()+"    Planarity: "+d.planarity());
            label(c,"Angle condition: "+d.angleCondition()+"    Simple boundary: "+d.simpleBoundary());
            label(c,"Area qualification: "+d.areaQualification()+"    Full experiment converged: "+d.fullExperimentConverged());
            if(session.request().dimension()==GeometryEmbeddingDimension.THREE_D){label(c,"Active 3D constraint summary:");for(ActiveConstraintState state:session.activeThreeDConstraints())label(c,state.name()+": enabled="+state.enabled()+" mode="+(state.activeRealizationTarget()?"ACTIVE_REALIZATION_TARGET":"MEASUREMENT_ONLY")+" error="+state.currentError()+" tolerance="+state.tolerance()+" strength="+state.restoringStrength()+" "+(state.passed()?"PASS":"FAIL"));ThreeDConstraintDiagnostics live=session.threeDConstraintDiagnostics();label(c,"Maximum velocity="+session.metrics().maximumNodeSpeed()+" volume estimate="+live.enclosedVolumeEstimate()+" combinatorial manifold="+live.manifold());}
        }
        label(c, "Target edge length: configured Geometry Test request");
        label(c, "Equal-edge realization: " + StatusPresentation.equalEdgeConclusion(session, result));
        label(c, "Target geometry: Not established");
    }

    private void phase2Eligibility(NkContext c,TestResult result){if(result==null){label(c,"Eligibility: Not tested");return;}label(c,"Topology eligibility: "+result.eligibility().status());label(c,result.eligibility().conclusion());result.eligibility().conditions().forEach(condition->label(c,condition.condition()+": "+condition.outcome()+" - "+condition.explanation()));}

    private void phase2Regularity2d(NkContext c,TestResult result){
        if(result==null||result.twoDRegularity()==null){label(c,"2D regularity: Not available");return;}
        TwoDRegularityMetrics m=result.twoDRegularity();
        label(c,"Plane: point="+m.planePoint()+" normal="+m.planeNormal());
        label(c,"Planarity: "+(m.planar()?"PASS":"FAIL")+" tolerance="+m.planarityTolerance());
        label(c,"Planarity mode: "+result.planarityMode()+"    Angle condition: "+result.angleConditionMode());
        label(c,"Plane deviation max="+m.maximumPlaneDeviation()+" mean="+m.meanPlaneDeviation()+" RMS="+m.rmsPlaneDeviation());
        for(int i=0;i<m.interiorAngles().values().size();i++)label(c,"Vertex "+i+": plane distance="+m.planeDistances().get(i)+" interior="+m.interiorAngles().values().get(i)+" turning="+m.turningAngles().values().get(i)+" interior deviation="+m.interiorAngleTargetDeviations().get(i)+" turning deviation="+m.turningAngleTargetDeviations().get(i));
        label(c,"Target interior="+m.targetInteriorAngle()+" target turning="+m.targetTurningAngle());
        label(c,"Interior mean="+m.interiorAngles().mean()+" min="+m.interiorAngles().minimum()+" max="+m.interiorAngles().maximum()+" range="+m.interiorAngles().range()+" variance="+m.interiorAngles().variance()+" stddev="+m.interiorAngles().standardDeviation()+" maximum target deviation="+m.maximumAngleTargetDeviation());
        label(c,"Angle regularity: "+switch(result.angleConditionMode()){case NONE->"NOT_APPLICABLE";case MEASURE_ONLY->"MEASURED";case REQUIRE_EQUAL_ANGLES->m.equalAngles()?"PASSED":"FAILED";});
        label(c,"Regular polygon realization: "+(m.equalEdges()&&m.equalAngles()?"YES":"NO"));
    }

    private void phase2Regularity3d(NkContext c,TestResult result){
        if(result==null||result.threeDRegularity()==null){label(c,"3D regularity: Not applicable");return;}
        ThreeDRegularityMetrics m=result.threeDRegularity();label(c,"Center definition: "+m.centerDefinition()+"    Center: "+m.geometryCenter());
        label(c,"Metrics applicability: "+m.applicable()+" - "+m.applicabilityReason());
        if(!m.applicable())return;
        for(FacePlaneDiagnostic face:m.facePlanes())label(c,face.faceId()+": planar="+face.planar()+" max="+face.maximumDeviation()+" mean="+face.meanDeviation()+" RMS="+face.rmsDeviation()+" tolerance="+face.tolerance()+" normal="+face.outwardNormal()+" orientation="+face.orientation()+" plane radius="+face.facePlaneRadius()+" normal-center alignment="+face.normalCenterAlignment());
        statistics(c,"Face-plane radii",m.facePlaneRadii());label(c,"Face-plane maximum mean deviation="+m.maximumFacePlaneRadiusDeviationFromMean()+" relative maximum deviation="+m.relativeMaximumFacePlaneRadiusDeviation());
        statistics(c,"Vertex radii",m.vertexRadii());statistics(c,"Face-centroid radii",m.faceCenterRadii());statistics(c,"Edge-midpoint radii",m.edgeMidpointRadii());statistics(c,"Normal-center alignment",m.normalCenterAlignments());
        label(c,"Maximum normal alignment deviation from 1="+m.maximumNormalAlignmentDeviationFromOne());
        for(FaceMetrics face:m.faces())label(c,face.faceId()+" edgeCount="+face.edgeCount()+" target interior="+face.targetInteriorAngle()+" angle variance="+face.interiorAngles().variance()+" max target deviation="+face.maximumInteriorTargetDeviation());
        label(c,"Interior dihedral convention; edge-angle pairs: "+m.dihedralEdgeAngles());statistics(c,"Interior dihedral angles",m.interiorDihedralAngles());
        label(c,"Face-area variance by edge count: "+m.faceAreaByEdgeCount());label(c,"Face-angle variance groups: "+m.faceAngleVarianceByEdgeCount());
        if(result.threeDConditionReport()!=null){label(c,"Basic valid 3D candidate: "+result.threeDConditionReport().basicValidCandidate());label(c,"Selected constrained experiment passed: "+result.threeDConditionReport().constrainedExperimentPassed());label(c,"Active conditions: "+result.threeDConditionReport().conditions());label(c,"Conclusions: "+result.threeDConditionReport().conclusions());}
        label(c,"Equal face-plane radii verifies plane distances only; no common insphere is claimed.");
    }

    private void statistics(NkContext c,String name,Statistics s){label(c,name+": values="+s.values()+" mean="+s.mean()+" min="+s.minimum()+" max="+s.maximum()+" range="+s.range()+" variance="+s.variance()+" stddev="+s.standardDeviation());}

    private void phase2FacesBoundary(NkContext c, TestResult result) {
        if (result == null) { label(c, "Faces: Undetermined"); label(c, "Boundary: Undetermined"); return; }
        if(result.faceDerivation()==null)label(c,"Derived faces: Not run");else for(DerivedFace face:result.faceDerivation().candidateFaces()){boolean accepted=result.faceDerivation().faces().contains(face);label(c,face.id()+" cycle="+face.nodeIds()+" edgeCount="+face.nodeIds().size()+" status="+(accepted?"ACCEPTED":"REJECTED")+" reason="+(accepted?"simple topological cycle and geometric planarity passed":!face.chordless()?"cycle has a chord":"face planarity error "+face.planarityError()));}
        label(c, "Unique face set: " + (result.faceDerivation() == null ? "Undetermined" : result.faceDerivation().unique()));
        label(c, "Boundary: " + (result.boundary() == null ? "Undetermined" : result.boundary().status().code()));
        if (result.boundary() != null) label(c, "Closed=" + result.boundary().closed() + " manifold=" + result.boundary().manifold() + " self-intersecting=" + result.boundary().selfIntersecting());
    }

    private void phase2Measurement(NkContext c, TestResult result) {
        if (result == null) { label(c, "Area eligibility: Not tested"); label(c, "Volume eligibility: Not tested"); return; }
        label(c, "Area: " + (result.area().available() ? result.area().absoluteArea() : "unavailable"));
        label(c, "Volume: " + (result.volume().available() ? result.volume().absoluteVolume() : "unavailable"));
        label(c, "Optimization: " + (result.optimization().attempted() ? result.optimization().summary() : "not attempted"));
    }

    private void phase2Diagnostics(NkContext c, GenerationResult generation, Configuration configuration, TestResult result) {
        if (configuration != null) {
            label(c, "ConfigurationStatus: " + configuration.status().code());
            label(c, configuration.status().explanation());
            for (GeometryConfigurationDiagnostic d : configuration.diagnostics()) label(c, d.stage() + ": " + d.code() + " - " + d.explanation());
        }
        if (result != null) { label(c, "Result status: " + result.status().code()); label(c, result.status().explanation()); }
        else label(c, "Equal-edge realization: Not tested");
    }

    private void heading(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 3, 1);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFontBold());
        Nuklear.nk_label_wrap(c, text);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFont());
    }

    private void overview(NkContext c, GeometryDiscoveryWorkspaceSnapshot snapshot) {
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        TopologyCandidate evaluated = controller.evaluatedCurrentCandidate();
        label(c, "Candidate: " + snapshot.geometry().model().name());
        label(c, "Experiment state: " + display(workflow.state().name()));
        label(c, "Geometry converged: "
                + controller.workflowResolver().geometrySatisfied(snapshot.geometry()));
        label(c, String.format("Total score: %.7g", evaluated.score().total()));
        label(c, "Runtime compatible: "
                + snapshot.geometry().runtimeCompatibility().compatible());
        label(c, "Search status: " + (snapshot.searchRunning() ? "Running"
                : snapshot.search() == null ? "Not started" : "Stopped"));
    }

    private void geometry(NkContext c, GeometryDiscoveryMetrics g) {
        label(c, "Converged: " + g.converged() + "    Iteration: " + g.iterationCount());
        label(c, String.format("Edge error: %.7g    Edge deviation: %.7g",
                g.totalEdgeLengthError(), g.standardDeviationConnectedNeighborDistance()));
        label(c, String.format("Minimum separation: %.7g    Collisions: %d",
                g.minimumDistinctNodeDistance(), g.collisionViolationCount()));
        label(c, String.format("Extents: %.5g x %.5g x %.5g",
                g.extentX(), g.extentY(), g.extentZ()));
        label(c, "Effective dimensionality: " + g.effectiveDimensionality());
    }

    private void topology(NkContext c, TopologyMetrics t) {
        label(c, "Nodes: " + t.nodeCount() + "    Edges: " + t.edgeCount());
        label(c, String.format("Degree: %d to %d    Mean: %.5g",
                t.minimumDegree(), t.maximumDegree(), t.meanDegree()));
        label(c, String.format("Diameter: %d    Mean shortest path: %.5g",
                t.graphDiameter(), t.meanShortestPathLength()));
        label(c, String.format("Clustering: %.5g    Triangles: %d",
                t.clusteringCoefficient(), t.triangleCount()));
        label(c, "Bridges: " + t.bridgeEdgeCount()
                + "    Articulation points: " + t.articulationPointCount());
    }

    private void directional(NkContext c, DirectionalBalanceMetrics d) {
        label(c, String.format("Directional bias: %.6g    Tensor isotropy: %.6g",
                d.meanNormalizedDirectionalBias(), d.meanTensorIsotropyError()));
        label(c, String.format("Angular reference error: %.6g    Applicable nodes: %d",
                d.meanKnownReferenceAngularError(), d.angularReferenceApplicableNodeCount()));
        label(c, String.format("Spherical coverage mean: %.6g    Maximum gap: %.6g",
                d.meanNearestDirectionAngle(), d.maximumUncoveredAngle()));
        label(c, String.format("Opposite-direction mismatch: %.6g",
                d.meanOppositeDirectionMismatch()));
        label(c, "Dimension-deficient nodes: " + d.dimensionDeficientNodeCount());
    }

    private void score(NkContext c, TopologyScore score) {
        label(c, "Profile: " + display(controller.state().scoringProfile().name()));
        label(c, String.format("Total score: %.7g", score.total()));
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(c, "Show only non-zero: "
                + controller.state().showOnlyNonZeroScoreComponents()))
            controller.state().setShowOnlyNonZeroScoreComponents(
                    !controller.state().showOnlyNonZeroScoreComponents());
        if (Nuklear.nk_button_label(c, (controller.state().scoreWeightsExpanded()
                ? "Hide" : "Edit") + " Score Weights"))
            controller.state().setScoreWeightsExpanded(!controller.state().scoreWeightsExpanded());
        for (TopologyScoreComponent component : score.details().values()) {
            if (controller.state().showOnlyNonZeroScoreComponents()
                    && component.weightedContribution() == 0) continue;
            double percentage = score.total() == 0 ? 0
                    : component.weightedContribution() * 100 / score.total();
            label(c, String.format("%s: raw %.4g norm %.4g weight %.4g add %.4g (%.1f%%) app=%s",
                    component.name(), component.rawValue(), component.normalizedValue(),
                    component.weight(), component.weightedContribution(), percentage,
                    component.applicable()));
            if (controller.state().scoreWeightsExpanded()) {
                Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
                if (Nuklear.nk_button_label(c, component.name() + " Weight -"))
                    controller.overrideComponentWeight(component.name(),
                            Math.max(0, component.weight() * .8));
                if (Nuklear.nk_button_label(c, component.name() + " Weight +"))
                    controller.overrideComponentWeight(component.name(),
                            component.weight() == 0 ? 1 : component.weight() * 1.25);
            }
        }
    }

    private void selectedNode(NkContext c, GeometryDiscoveryWorkspaceSnapshot snapshot) {
        Integer id = controller.viewState().selectedNodeId();
        if (id == null) {
            label(c, "Select a node in the viewport to inspect it.");
            return;
        }
        GeometryDiscoveryModel model = snapshot.geometry().model();
        DiscoveryNode node = model.nodes().stream().filter(candidate -> candidate.id() == id)
                .findFirst().orElse(null);
        if (node == null) {
            label(c, "The selected node is not present in this candidate.");
            return;
        }
        List<Integer> neighbors = List.copyOf(
                TopologyConstraintValidator.adjacency(model).get(id));
        label(c, "Node ID: " + id + "    Degree: " + neighbors.size());
        label(c, "Position: " + node.position());
        label(c, "Fixed: " + node.fixedPosition() + "    Neighbors: " + neighbors);
        LocalDirectionalDiagnostics diagnostics =
                new DirectionalBalanceCalculator().calculate(model).localDiagnostics().get(id);
        label(c, String.format("Directional bias: %.6g    Tensor error: %.6g",
                diagnostics.normalizedDirectionalBias(), diagnostics.tensorIsotropyError()));
        label(c, "Local dimensionality: " + diagnostics.localDimensionality()
                + "    Eigenvalues: " + diagnostics.eigenvalues());
        label(c, "Pairwise dot products: " + diagnostics.pairwiseDotProducts());
    }

    private void history(NkContext c, GeometryDiscoveryWorkspaceSnapshot snapshot) {
        if (snapshot.search() == null || snapshot.search().history().isEmpty()) {
            label(c, "Search history and candidate archive will appear after search begins.");
            return;
        }
        label(c, "Score History");
        double minimum = snapshot.search().history().stream()
                .mapToDouble(TopologySearchHistoryEntry::bestScore).min().orElse(0);
        double maximum = snapshot.search().history().stream()
                .mapToDouble(TopologySearchHistoryEntry::currentScore).max().orElse(minimum + 1);
        if (maximum <= minimum) maximum = minimum + 1;
        Nuklear.nk_layout_row_dynamic(c, 70, 1);
        int first = Math.max(0, snapshot.search().history().size() - 500);
        if (Nuklear.nk_chart_begin(c, Nuklear.NK_CHART_LINES,
                snapshot.search().history().size() - first, (float) minimum, (float) maximum)) {
            for (int i = first; i < snapshot.search().history().size(); i++)
                Nuklear.nk_chart_push(c,
                        (float) snapshot.search().history().get(i).bestScore());
            Nuklear.nk_chart_end(c);
        }
        label(c, "Archived candidates: " + snapshot.search().topCandidates().size());
        label(c, "Important Events (latest first)");
        List<DiscoveryExperimentEvent> events = controller.workspace().eventLog();
        for (int i = events.size() - 1; i >= Math.max(0, events.size() - 8); i--) {
            DiscoveryExperimentEvent event = events.get(i);
            label(c, event.type() + " at iteration " + event.iteration()
                    + ": " + event.details());
        }
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
