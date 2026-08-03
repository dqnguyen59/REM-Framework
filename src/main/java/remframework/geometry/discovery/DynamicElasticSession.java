package remframework.geometry.discovery;
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

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import remframework.geometry.discovery.experiment.*;

/**
 * Incremental velocity-based relaxation for a fixed regular graph.
 */
public final class DynamicElasticSession {
    private static final double EPSILON = 1e-12;
    private final FixedDegreeGraph graph;
    private DynamicElasticSettings settings;
    private final List<DynamicElasticNode> initialNodes;
    private List<DynamicElasticNode> nodes;
    private Map<Integer, DynamicElasticNode> byId;
    private DynamicElasticState state = DynamicElasticState.INITIALIZING;
    private int iteration;
    private int stableIterations;
    private int elasticStableIterations;
    private final long startedNanos = System.nanoTime();
    private String failureReason = "";
    private boolean strictTwoDimensional;
    private boolean planeConstraint;
    private double planeRestoringStrength;
    private double planarityTolerance = 1e-7;
    private boolean cycleAngleConstraint;
    private double cycleTargetAngleDegrees;
    private double cycleAngleToleranceDegrees;
    private double cycleAngleStrength;
    private DynamicElasticThreeDConstraints threeDConstraints;
    private ThreeDExperimentConditions threeDModes;
    private final int secondAnchorId;
    private final StagnationDetector stagnationDetector=new StagnationDetector(200,1e-5);
    private StagnationStatus stagnationStatus=StagnationStatus.progressing();
    private boolean spatialConstraintsEnabled=true;
    private ConstraintObjectiveDiagnostics objectiveDiagnostics;

    public DynamicElasticSession(FixedDegreeGraph graph, DynamicElasticSettings settings) {
        this(graph, settings, initialize(graph.nodeCount(), settings));
    }

    public DynamicElasticSession(FixedDegreeGraph graph, DynamicElasticSettings settings,
                                 List<DynamicElasticNode> initialNodes) {
        this.graph = graph;
        this.settings = settings;
        secondAnchorId=graph.connections().stream().filter(edge->edge.firstNodeId()==0||edge.secondNodeId()==0)
                .mapToInt(edge->edge.firstNodeId()==0?edge.secondNodeId():edge.firstNodeId()).min().orElse(1);
        validateInitialNodes(graph, initialNodes);
        boolean legacyAnchors=initialNodes.stream().filter(DynamicElasticNode::fixed).map(DynamicElasticNode::id).toList().equals(List.of(0,1));
        this.initialNodes = initialNodes.stream().map(node->legacyAnchors
                ?new DynamicElasticNode(node.id(),node.position(),node.velocity(),node.id()==0||node.id()==secondAnchorId)
                :node.copy()).toList();
        reset();
    }

    public boolean step() {
        if (finished()) return false;
        state = DynamicElasticState.RELAXING;
        Map<Integer, Vector3d> forces = DevelopmentTimingDiagnostics.measure("stage2.edge-and-constraint-forces",this::calculateForces);
        clampForces(forces);
        integrate(forces);
        enforceProfilePlane();
        applyAngularDamping();
        enforceAnchors();
        iteration++;
        if (!finite()) {
            state = DynamicElasticState.NUMERICAL_FAILURE;
            failureReason = "A non-finite position, velocity, or metric was produced.";
            return false;
        }
        DynamicElasticMetrics current = DevelopmentTimingDiagnostics.measure("stage2.qualification",this::calculateMetrics);
        boolean elasticConditions = current.connectionsSatisfied()
                == current.connectionCount()
                && current.maximumNodeSpeed() <= settings.maximumEquilibriumSpeed();
        elasticStableIterations = elasticConditions ? elasticStableIterations + 1 : 0;
        boolean canonicalConditions = !settings.canonicalGeometry().angleConstraintsEnabled()
                || current.localAnglesSatisfied() == current.localAngleCount()
                && current.spatiallyValid();
        boolean planeConditions = !planeConstraint || maximumPlaneDeviation() <= planarityTolerance;
        boolean cycleAngleConditions = !cycleAngleConstraint || maximumCycleAngleErrorDegrees() <= cycleAngleToleranceDegrees;
        boolean threeDConditions=threeDConstraints==null||threeDConstraintsPass();
        stableIterations = elasticConditions && canonicalConditions && planeConditions && cycleAngleConditions&&threeDConditions
                ? stableIterations + 1 : 0;
        if (stableIterations >= settings.requiredStableIterations())
            state = DynamicElasticState.EQUILIBRIUM_REACHED;
        else if (iteration >= settings.maximumIterations())
            state = DynamicElasticState.MAX_ITERATIONS_REACHED;
        else if (elasticConditions)
            state = DynamicElasticState.NEAR_EQUILIBRIUM;
        objectiveDiagnostics=calculateObjectiveDiagnostics(current);
        stagnationStatus=stagnationDetector.add(objectiveDiagnostics.totalObjective(),current.maximumNodeSpeed(),state==DynamicElasticState.EQUILIBRIUM_REACHED);
        return true;
    }

    public DynamicElasticResult runUntilFinished() {
        while (!finished()) step();
        return result();
    }

    public void reset() {
        nodes = initialNodes.stream().map(DynamicElasticNode::copy).toList();
        byId = new HashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        iteration = 0;
        stableIterations = 0;
        elasticStableIterations = 0;
        failureReason = "";
        state = DynamicElasticState.INITIALIZING;
        stagnationDetector.reset();stagnationStatus=StagnationStatus.progressing();
        enforceAnchors();
        objectiveDiagnostics=calculateObjectiveDiagnostics(calculateMetrics());
    }

    public Map<Integer, Vector3d> calculateForces() {
        Map<Integer, Vector3d> forces = new HashMap<>();
        nodes.forEach(node -> forces.put(node.id(), new Vector3d()));
        for (DiscoveryConnection edge : graph.connections()) {
            DynamicElasticNode first = byId.get(edge.firstNodeId());
            DynamicElasticNode second = byId.get(edge.secondNodeId());
            Vector3d displacement = new Vector3d(second.position()).sub(first.position());
            double distance = displacement.length();
            Vector3d direction = distance > EPSILON ? displacement.div(distance)
                    : deterministicDirection(edge.undirectedKey());
            double error = distance - settings.targetDistance();
            Vector3d force = direction.mul(settings.springStrength() * error);
            forces.get(first.id()).add(force);
            forces.get(second.id()).sub(force);
        }
        if(spatialConstraintsEnabled)new DynamicElasticSpatialConstraints().addForces(graph, nodes, forces,
                settings.spatial(), settings.randomSeed());
        new DynamicElasticAngleConstraints().addForces(graph, byId, forces,
                settings.canonicalGeometry());
        if (cycleAngleConstraint)
            new DynamicElasticAngleConstraints().addCycleForces(graph, byId, forces,
                    cycleTargetAngleDegrees, cycleAngleStrength);
        if (planeConstraint)
            nodes.stream().filter(node -> !node.fixed()).forEach(node ->
                    forces.get(node.id()).z -= planeRestoringStrength * node.position().z);
        if(threeDConstraints!=null)threeDConstraints.addForces(nodes,forces);
        return forces;
    }

    public void configureThreeDimensionalProfile(List<List<Integer>> faces,
            ThreeDExperimentConditions modes,ThreeDConstraintStrengths strengths){
        configureThreeDimensionalProfile(faces,modes,strengths,false,0);
    }
    public void configureThreeDimensionalProfile(List<List<Integer>> faces,ThreeDExperimentConditions modes,ThreeDConstraintStrengths strengths,boolean outwardOrientation,double orientationStrength){configureThreeDimensionalProfile(faces,modes,strengths,outwardOrientation,orientationStrength,false,.05,.6);}
    public void configureThreeDimensionalProfile(List<List<Integer>> faces,ThreeDExperimentConditions modes,ThreeDConstraintStrengths strengths,boolean outwardOrientation,double orientationStrength,boolean avoidance,double clearanceFactor,double avoidanceStrength){threeDModes=modes;threeDConstraints=new DynamicElasticThreeDConstraints(faces,modes,strengths,settings.targetDistance(),outwardOrientation,orientationStrength,avoidance,clearanceFactor,avoidanceStrength);}
    public void continueRelaxation(){if(state==DynamicElasticState.EQUILIBRIUM_REACHED){state=DynamicElasticState.NEAR_EQUILIBRIUM;stableIterations=0;stagnationDetector.reset();stagnationStatus=StagnationStatus.progressing();}}
    public void setSpatialConstraintsEnabled(boolean enabled){spatialConstraintsEnabled=enabled;}
    /** Runtime manual-relaxation control; coordinates and velocities are intentionally preserved. */
    public void setSpringStrength(double strength){settings=settings.withSpringStrength(Math.max(0,strength));}
    public void refreshDiagnostics(){objectiveDiagnostics=calculateObjectiveDiagnostics(calculateMetrics());}
    public void replaceVelocities(List<Vector3d> velocities){if(velocities.size()!=nodes.size())throw new IllegalArgumentException("Velocity count does not match nodes.");for(int i=0;i<nodes.size();i++)nodes.get(i).velocity().set(velocities.get(i));}
    public void resetVelocities(){nodes.forEach(node->node.velocity().zero());}
    public void replacePositions(List<Vector3d> positions){if(positions.size()!=nodes.size())throw new IllegalArgumentException("Position count does not match nodes.");for(int i=0;i<nodes.size();i++)nodes.get(i).position().set(positions.get(i));enforceAnchors();}
    public void acceptContinuationEquilibrium(){state=DynamicElasticState.EQUILIBRIUM_REACHED;}

    public ThreeDConstraintDiagnostics threeDConstraintDiagnostics(){return threeDConstraints==null?ThreeDConstraintDiagnostics.inactive():threeDConstraints.diagnostics(nodes);}
    public SelfIntersectionDiagnostics selfIntersectionDiagnostics(){return threeDConstraints==null?SelfIntersectionDiagnostics.inactive():threeDConstraints.selfIntersectionDiagnostics(nodes);}
    public ConstraintObjectiveDiagnostics objectiveDiagnostics(){return objectiveDiagnostics;}
    public StagnationStatus stagnationStatus(){return stagnationStatus;}

    private ConstraintObjectiveDiagnostics calculateObjectiveDiagnostics(DynamicElasticMetrics metrics){double max=0,sum=0;for(DynamicElasticEdgeMetric edge:metrics.edges()){double error=(edge.distance()-edge.targetDistance())/settings.targetDistance();max=Math.max(max,Math.abs(error));sum+=error*error;}double rms=metrics.edges().isEmpty()?0:Math.sqrt(sum/metrics.edges().size());double edgeContribution=.5*settings.springStrength()*(metrics.edges().isEmpty()?0:sum/metrics.edges().size());FaceObjectiveDiagnostics face=threeDConstraints==null?FaceObjectiveDiagnostics.inactive():threeDConstraints.objectiveDiagnostics(nodes);double kinetic=nodes.stream().filter(node->!node.fixed()).mapToDouble(node->node.velocity().lengthSquared()).sum()*.5;return new ConstraintObjectiveDiagnostics(max,rms,face.planarityMaximum(),face.planarityRms(),face.angleMaximum(),face.angleRms(),edgeContribution,face.planarityContribution(),face.angleContribution(),face.dihedralContribution(),face.facePlaneRadiusContribution(),edgeContribution+face.totalContribution(),metrics.maximumNodeSpeed(),kinetic);}

    private boolean threeDConstraintsPass(){ThreeDConstraintDiagnostics d=threeDConstraintDiagnostics();if(!d.finite())return false;
        boolean planar=threeDModes.facePlanarity()!=FacePlanarityMode.REQUIRE_PLANAR_FACES||d.maximumFacePlanarityError()<=threeDModes.facePlanarityTolerance();
        boolean angles=threeDModes.faceAngles()!=FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE||d.maximumFaceAngleDeviation()<=threeDModes.absoluteRegularityTolerance();
        boolean dihedral=threeDModes.dihedrals()!=DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES||d.maximumDihedralDeviation()<=threeDModes.absoluteRegularityTolerance();
        boolean radii=threeDModes.facePlaneRadii()!=FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII||d.maximumFacePlaneRadiusDeviation()<=threeDModes.absoluteRegularityTolerance();
        return planar&&angles&&dihedral&&radii;
    }

    public void configureTwoDimensionalProfile(boolean strict, boolean constrained,
            double restoringStrength, double tolerance, boolean requireEqualAngles,
            double targetAngleDegrees, double angleToleranceDegrees, double angleStrength) {
        strictTwoDimensional = strict;
        planeConstraint = constrained;
        planeRestoringStrength = restoringStrength;
        planarityTolerance = tolerance;
        cycleAngleConstraint = requireEqualAngles;
        cycleTargetAngleDegrees = targetAngleDegrees;
        cycleAngleToleranceDegrees = angleToleranceDegrees;
        cycleAngleStrength = angleStrength;
        enforceProfilePlane();
    }

    private void enforceProfilePlane() {
        if (!strictTwoDimensional) return;
        nodes.forEach(node -> { node.position().z = 0; node.velocity().z = 0; });
    }

    public double maximumPlaneDeviation() {
        return nodes.stream().mapToDouble(node -> Math.abs(node.position().z)).max().orElse(0);
    }

    public double maximumCycleAngleErrorDegrees() {
        if (graph.degree() != 2) return Double.NaN;
        Map<Integer, java.util.Set<Integer>> adjacency = new HashMap<>();
        for (int id=0;id<graph.nodeCount();id++) adjacency.put(id,new java.util.TreeSet<>());
        graph.connections().forEach(edge->{adjacency.get(edge.firstNodeId()).add(edge.secondNodeId());adjacency.get(edge.secondNodeId()).add(edge.firstNodeId());});
        double maximum=0;
        for(int center=0;center<graph.nodeCount();center++){int[] neighbor=adjacency.get(center).stream().mapToInt(Integer::intValue).toArray();
            Vector3d a=new Vector3d(byId.get(neighbor[0]).position()).sub(byId.get(center).position());
            Vector3d b=new Vector3d(byId.get(neighbor[1]).position()).sub(byId.get(center).position());
            if(a.lengthSquared()<=EPSILON||b.lengthSquared()<=EPSILON)return 180;
            double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,a.normalize().dot(b.normalize())))));
            maximum=Math.max(maximum,Math.abs(angle-cycleTargetAngleDegrees));}
        return maximum;
    }

    private void clampForces(Map<Integer, Vector3d> forces) {
        for (Vector3d force : forces.values()) clamp(force, settings.maximumForce());
    }

    private void integrate(Map<Integer, Vector3d> forces) {
        for (DynamicElasticNode node : nodes) {
            if (node.fixed()) {
                node.velocity().zero();
                continue;
            }
            node.velocity().mul(settings.velocityRetention())
                    .add(new Vector3d(forces.get(node.id())).mul(settings.updateStrength()))
                    .mul(Math.max(0, 1 - settings.linearVelocityDamping()));
            clamp(node.velocity(), settings.maximumVelocity());
            node.position().add(node.velocity());
        }
    }

    private void applyAngularDamping() {
        if (settings.angularVelocityDamping() <= 0) return;
        double momentumX = 0;
        double inertiaX = 0;
        for (DynamicElasticNode node : nodes) {
            if (node.fixed()) continue;
            momentumX += node.position().y * node.velocity().z
                    - node.position().z * node.velocity().y;
            inertiaX += node.position().y * node.position().y
                    + node.position().z * node.position().z;
        }
        if (inertiaX <= EPSILON) return;
        double omega = momentumX / inertiaX * settings.angularVelocityDamping();
        for (DynamicElasticNode node : nodes) {
            if (node.fixed()) continue;
            node.velocity().sub(0, -omega * node.position().z,
                    omega * node.position().y);
        }
    }

    private void enforceAnchors() {
        DynamicElasticNode first = byId.get(0);
        DynamicElasticNode second = byId.get(secondAnchorId);
        if (first != null) { first.position().zero(); first.velocity().zero(); }
        if (second != null) {
            second.position().set(settings.targetDistance(), 0, 0);
            second.velocity().zero();
        }
    }

    public DynamicElasticMetrics metrics() {
        return calculateMetrics();
    }

    private DynamicElasticMetrics calculateMetrics() {
        List<DynamicElasticEdgeMetric> edgeMetrics = new ArrayList<>();
        int satisfied = 0;
        double accuracySum = 0;
        for (DiscoveryConnection edge : graph.connections()) {
            double distance = byId.get(edge.firstNodeId()).position()
                    .distance(byId.get(edge.secondNodeId()).position());
            double relativeError = Math.abs(distance - settings.targetDistance())
                    / settings.targetDistance();
            double accuracy = Math.max(0, Math.min(1, 1 - relativeError));
            boolean edgeSatisfied = accuracy >= settings.requiredAccuracy();
            if (edgeSatisfied) satisfied++;
            accuracySum += accuracy;
            edgeMetrics.add(new DynamicElasticEdgeMetric(edge.firstNodeId(),
                    edge.secondNodeId(), distance, settings.targetDistance(),
                    relativeError, accuracy, edgeSatisfied));
        }
        double maximumSpeed = nodes.stream().filter(node -> !node.fixed())
                .mapToDouble(node -> node.velocity().length()).max().orElse(0);
        SpatialMetrics spatial = spatialMetrics();
        AngleMetrics angles = angleMetrics();
        int edgeCount = edgeMetrics.size();
        return new DynamicElasticMetrics(iteration, satisfied, edgeCount,
                edgeCount == 0 ? 1 : accuracySum / edgeCount,
                edgeCount == 0 ? 100 : 100.0 * satisfied / edgeCount,
                maximumSpeed, stableIterations, settings.requiredStableIterations(),
                state, edgeMetrics, spatial.minimumNonNeighborDistance(),
                spatial.edgeClearanceViolations(), spatial.meanLocalSpread(),
                spatial.minimumLocalSpread(),
                elasticStableIterations >= settings.requiredStableIterations(),
                spatial.spatiallyValid(), angles.count(), angles.satisfied(),
                angles.meanErrorDegrees(), angles.maximumErrorDegrees(),
                settings.canonicalGeometry().targetAngleDegrees(),
                settings.canonicalGeometry().angleToleranceDegrees(),
                settings.canonicalGeometry().angleConstraintsEnabled()
                        && stableIterations >= settings.requiredStableIterations());
    }

    private AngleMetrics angleMetrics() {
        if (!settings.canonicalGeometry().angleConstraintsEnabled()
                || graph.canonicalTopology() == null)
            return new AngleMetrics(0, 0, 0, 0);
        Map<Integer, java.util.Set<Integer>> adjacency = new HashMap<>();
        for (int id = 0; id < graph.nodeCount(); id++) adjacency.put(id, new java.util.TreeSet<>());
        for (DiscoveryConnection edge : graph.connections()) {
            adjacency.get(edge.firstNodeId()).add(edge.secondNodeId());
            adjacency.get(edge.secondNodeId()).add(edge.firstNodeId());
        }
        int count = 0, satisfied = 0;
        double sum = 0, maximum = 0;
        for (int center = 0; center < graph.nodeCount(); center++) {
            int[] neighbor = adjacency.get(center).stream().mapToInt(Integer::intValue).toArray();
            for (int first = 0; first < neighbor.length; first++)
                for (int second = first + 1; second < neighbor.length; second++) {
                    Vector3d u = new Vector3d(byId.get(neighbor[first]).position())
                            .sub(byId.get(center).position());
                    Vector3d v = new Vector3d(byId.get(neighbor[second]).position())
                            .sub(byId.get(center).position());
                    double error;
                    if (u.lengthSquared() <= EPSILON || v.lengthSquared() <= EPSILON)
                        error = 180;
                    else {
                        double cosine = Math.max(-1, Math.min(1,
                                u.normalize().dot(v.normalize())));
                        double angle = Math.toDegrees(Math.acos(cosine));
                        error = Math.abs(angle
                                - settings.canonicalGeometry().targetAngleDegrees());
                    }
                    count++;
                    sum += error;
                    maximum = Math.max(maximum, error);
                    if (error <= settings.canonicalGeometry().angleToleranceDegrees())
                        satisfied++;
                }
        }
        return new AngleMetrics(count, satisfied, count == 0 ? 0 : sum / count, maximum);
    }

    private record AngleMetrics(int count, int satisfied,
                                double meanErrorDegrees,
                                double maximumErrorDegrees) {}

    private SpatialMetrics spatialMetrics() {
        java.util.Set<Long> edgeKeys = graph.connections().stream()
                .map(DiscoveryConnection::undirectedKey)
                .collect(java.util.stream.Collectors.toSet());
        double minimumNonNeighbor = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++) {
            DynamicElasticNode a = nodes.get(i), b = nodes.get(j);
            long key = ((long) Math.min(a.id(), b.id()) << 32)
                    | (Math.max(a.id(), b.id()) & 0xffffffffL);
            if (!edgeKeys.contains(key))
                minimumNonNeighbor = Math.min(minimumNonNeighbor,
                        a.position().distance(b.position()));
        }
        int clearanceViolations = 0;
        List<DiscoveryConnection> edges = graph.connections();
        for (int i = 0; i < edges.size(); i++) for (int j = i + 1; j < edges.size(); j++) {
            DiscoveryConnection a = edges.get(i), b = edges.get(j);
            if (DynamicElasticSpatialConstraints.shareEndpoint(a, b)) continue;
            double distance = DynamicElasticSpatialConstraints.closestPoints(
                    byId.get(a.firstNodeId()).position(), byId.get(a.secondNodeId()).position(),
                    byId.get(b.firstNodeId()).position(), byId.get(b.secondNodeId()).position())
                    .distance();
            if (distance < settings.spatial().edgeClearanceDistance()) clearanceViolations++;
        }
        Map<Integer, java.util.Set<Integer>> adjacency = new HashMap<>();
        for (int id = 0; id < graph.nodeCount(); id++) adjacency.put(id, new java.util.TreeSet<>());
        for (DiscoveryConnection edge : edges) {
            adjacency.get(edge.firstNodeId()).add(edge.secondNodeId());
            adjacency.get(edge.secondNodeId()).add(edge.firstNodeId());
        }
        double spreadSum = 0, minimumSpread = Double.MAX_VALUE;
        int spreadCount = 0;
        for (int id = 0; id < graph.nodeCount(); id++) {
            if (adjacency.get(id).size() != 3) continue;
            int[] neighbor = adjacency.get(id).stream().mapToInt(Integer::intValue).toArray();
            Vector3d origin = byId.get(id).position();
            double spread = DynamicElasticSpatialConstraints.localSpread(
                    new Vector3d(byId.get(neighbor[0]).position()).sub(origin),
                    new Vector3d(byId.get(neighbor[1]).position()).sub(origin),
                    new Vector3d(byId.get(neighbor[2]).position()).sub(origin));
            spreadSum += spread;
            minimumSpread = Math.min(minimumSpread, spread);
            spreadCount++;
        }
        if (spreadCount == 0) minimumSpread = 0;
        boolean finiteGeometry = nodes.stream().allMatch(n -> n.position().isFinite());
        boolean valid = finiteGeometry
                && minimumNonNeighbor >= settings.spatial().minimumNonNeighborDistance()
                && clearanceViolations == 0;
        return new SpatialMetrics(minimumNonNeighbor, clearanceViolations,
                spreadCount == 0 ? 0 : spreadSum / spreadCount, minimumSpread, valid);
    }

    private record SpatialMetrics(double minimumNonNeighborDistance,
                                  int edgeClearanceViolations,
                                  double meanLocalSpread,
                                  double minimumLocalSpread,
                                  boolean spatiallyValid) {}

    public DynamicElasticResult result() {
        DynamicElasticResultStatus status = switch (state) {
            case EQUILIBRIUM_REACHED -> DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND;
            case NUMERICAL_FAILURE -> DynamicElasticResultStatus.NUMERICAL_FAILURE;
            default -> DynamicElasticResultStatus.NO_EQUILIBRIUM_FOUND;
        };
        return new DynamicElasticResult("", graph, settings, initialNodes, nodes, metrics(),
                status, failureReason, (System.nanoTime() - startedNanos) / 1_000_000);
    }
    public DynamicElasticResult resultForQualification(){return new DynamicElasticResult("",graph,settings,initialNodes,nodes,metrics(),DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND,"",(System.nanoTime()-startedNanos)/1_000_000);}

    public GeometryDiscoveryModel geometryModel() {
        List<DiscoveryNode> discoveryNodes = nodes.stream().map(node ->
                new DiscoveryNode(node.id(), node.position(), node.fixed())).toList();
        return new GeometryDiscoveryModel("dynamic-elastic-k" + graph.degree()
                + "-n" + graph.nodeCount(), "Fixed-degree dynamic elastic candidate",
                discoveryNodes, graph.connections());
    }

    public void updateSettings(DynamicElasticSettings value) {
        if (value.maximumIterations() < iteration)
            throw new IllegalArgumentException("Maximum iterations cannot be below "
                    + "the current iteration.");
        boolean mayContinue = state == DynamicElasticState.MAX_ITERATIONS_REACHED
                && value.maximumIterations() > iteration;
        settings = value;
        if (mayContinue) state = DynamicElasticState.RELAXING;
    }
    public List<DynamicElasticNode> nodes() {
        return nodes.stream().map(DynamicElasticNode::copy).toList();
    }
    public void setNodeState(int id, Vector3d position, Vector3d velocity) {
        DynamicElasticNode node = byId.get(id);
        if (node == null) throw new IllegalArgumentException("Unknown node ID " + id);
        if (node.fixed()) throw new IllegalArgumentException("Anchor nodes cannot be moved.");
        node.position().set(position);
        node.velocity().set(velocity);
        stableIterations = 0;
        state = DynamicElasticState.RELAXING;
    }
    /** Updates a node only while the session remains in its pre-iteration initialization phase. */
    public void setInitializationNodeState(int id,Vector3d position){
        if(iteration!=0||state!=DynamicElasticState.INITIALIZING)
            throw new IllegalStateException("Initialization coordinates cannot change after Dynamic Elastic starts.");
        DynamicElasticNode node=byId.get(id);if(node==null)throw new IllegalArgumentException("Unknown node ID "+id);
        node.position().set(position);node.velocity().zero();
    }
    public FixedDegreeGraph graph() { return graph; }
    public DynamicElasticSettings settings() { return settings; }
    public DynamicElasticState state() { return state; }
    public int iteration() { return iteration; }
    public boolean finished() {
        return state == DynamicElasticState.EQUILIBRIUM_REACHED
                || state == DynamicElasticState.MAX_ITERATIONS_REACHED
                || state == DynamicElasticState.NUMERICAL_FAILURE;
    }

    private boolean finite() {
        return nodes.stream().allMatch(node -> node.position().isFinite()
                && node.velocity().isFinite()) && metrics().allFinite();
    }
    private void clamp(Vector3d vector, double maximum) {
        double length = vector.length();
        if (length > maximum && length > 0) vector.mul(maximum / length);
    }
    private Vector3d deterministicDirection(long key) {
        SplittableRandom random = new SplittableRandom(key ^ settings.randomSeed());
        Vector3d value = new Vector3d(random.nextDouble(-1, 1),
                random.nextDouble(-1, 1), random.nextDouble(-1, 1));
        return value.lengthSquared() < EPSILON ? new Vector3d(1, 0, 0) : value.normalize();
    }
    private static List<DynamicElasticNode> initialize(int count,
                                                       DynamicElasticSettings settings) {
        SplittableRandom random = new SplittableRandom(settings.randomSeed());
        List<DynamicElasticNode> result = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            Vector3d position;
            if (id == 0) position = new Vector3d();
            else if (id == 1) position = new Vector3d(settings.targetDistance(), 0, 0);
            else {
                double angle = random.nextDouble() * Math.PI * 2;
                double z = random.nextDouble(-1, 1);
                double planar = Math.sqrt(1 - z * z);
                position = new Vector3d(planar * Math.cos(angle),
                        planar * Math.sin(angle), z)
                        .mul(settings.targetDistance() * Math.max(1, Math.cbrt(count)));
            }
            result.add(new DynamicElasticNode(id, position, new Vector3d(), id < 2));
        }
        return result;
    }
    private static void validateInitialNodes(FixedDegreeGraph graph,
                                             List<DynamicElasticNode> initialNodes) {
        if (initialNodes.size() != graph.nodeCount())
            throw new IllegalArgumentException("Initial state must contain every graph node.");
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (DynamicElasticNode node : initialNodes) {
            if (node.id() < 0 || node.id() >= graph.nodeCount() || !ids.add(node.id()))
                throw new IllegalArgumentException("Initial node IDs must be unique and in range.");
            if (!node.position().isFinite() || !node.velocity().isFinite())
                throw new IllegalArgumentException("Initial positions and velocities must be finite.");
        }
        if (!ids.contains(0) || !ids.contains(1))
            throw new IllegalArgumentException("Anchor nodes 0 and 1 are required.");
    }
}
