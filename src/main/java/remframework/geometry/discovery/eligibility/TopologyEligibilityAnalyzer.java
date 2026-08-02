package remframework.geometry.discovery.eligibility;

import remframework.geometry.discovery.DiscoveryConnection;
import remframework.geometry.discovery.GraphSymmetryClassification;
import remframework.geometry.discovery.configuration.Configuration;
import remframework.geometry.discovery.configuration.ConfigurationRequest;
import remframework.geometry.discovery.experiment.ExperimentProfile;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import remframework.geometry.discovery.graph.symmetry.AutomorphismAnalyzer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Coordinate-free Stage 1 classification for an immutable graph topology. */
public final class TopologyEligibilityAnalyzer {
    public TopologyEligibilityReport analyze(Configuration configuration, ExperimentProfile profile) {
        return analyze(configuration, profile, FaceComposition.discover());
    }

    public TopologyEligibilityReport analyze(Configuration configuration, ExperimentProfile profile,
            FaceComposition composition) {
        return analyze(configuration.nodeCount(), configuration.exactDegree(), configuration.edges(),
                configuration.canonicalGraphLabel(), profile, composition);
    }

    public TopologyEligibilityReport analyze(Configuration configuration,ConfigurationRequest request) {
        TopologyEligibilityReport report=analyze(configuration,request.experimentProfile(),request.faceComposition());
        return new TopologyEligibilityReport(report.profile(),report.status(),report.conclusion(),
                report.conditions(),report.bridgeCount(),report.articulationVertexCount(),
                report.vertexConnectivity(),report.edgeConnectivity(),report.cycleRank(),report.planar(),
                report.automorphismGroupSize(),report.graphSymmetry(),report.vertexTransitive(),
                report.topologicalFaces(),report.faceComposition(),request.primaryParameterMode(),
                request.surfaceTopologyAssumption(),request.parameterDerivation());
    }

    /** Returns a coordinate-free sphere-like face set when one is combinatorially available. */
    public List<List<Integer>> deriveTopologicalFaces(Configuration configuration) {
        return sphericalFaceSet(adjacency(configuration.nodeCount(), configuration.edges()),
                configuration.edges().size());
    }

    public TopologyEligibilityReport analyze(int n, int expectedDegree,
            List<DiscoveryConnection> edges, String canonicalIdentity, ExperimentProfile profile) {
        return analyze(n,expectedDegree,edges,canonicalIdentity,profile,FaceComposition.discover());
    }

    public TopologyEligibilityReport analyze(int n, int expectedDegree,
            List<DiscoveryConnection> edges, String canonicalIdentity, ExperimentProfile profile,
            FaceComposition composition) {
        List<ConditionResult> checks = new ArrayList<>();
        boolean noLoops = edges.stream().noneMatch(e -> e.firstNodeId() == e.secondNodeId());
        boolean noDuplicates = edges.stream().map(DiscoveryConnection::undirectedKey).distinct().count() == edges.size();
        boolean endpoints = edges.stream().allMatch(e -> e.firstNodeId() >= 0 && e.secondNodeId() >= 0
                && e.firstNodeId() < n && e.secondNodeId() < n);
        boolean simple = noLoops && noDuplicates && endpoints;
        checks.add(result("NO_SELF_LOOPS", noLoops, "No edge may join a vertex to itself."));
        checks.add(result("NO_DUPLICATE_UNDIRECTED_EDGES", noDuplicates,
                "Each undirected edge must occur exactly once."));
        checks.add(result("SIMPLE_GRAPH", simple, "The graph has valid endpoints, no loops, and no duplicate edges."));
        List<Set<Integer>> adjacency = adjacency(n, edges);
        int components = components(adjacency, Set.of());
        boolean connected = n > 0 && components == 1;
        checks.add(measured("CONNECTED_GRAPH", connected, components, 1,
                "All vertices must be in one connected component."));
        boolean regular = adjacency.stream().allMatch(v -> v.size() == expectedDegree);
        checks.add(result("EXACT_K_REGULARITY", regular,
                "Every vertex degree must equal selected K=" + expectedDegree + "."));
        boolean canonical = canonicalIdentity != null && !canonicalIdentity.isBlank();
        checks.add(result("DETERMINISTIC_CANONICAL_IDENTITY", canonical,
                "A deterministic canonical graph identity must be retained."));

        int bridges = bridgeCount(adjacency);
        int articulations = articulationCount(adjacency);
        int cycleRank = edges.size() - n + components;
        Integer vertexConnectivity = connected ? vertexConnectivity(adjacency) : 0;
        Integer edgeConnectivity = connected ? edgeConnectivity(adjacency, edges) : 0;
        checks.add(measured("NO_BRIDGES", bridges == 0, bridges, 0, "A closed boundary candidate cannot contain a bridge."));
        checks.add(measured("NO_ARTICULATION_VERTICES", articulations == 0, articulations, 0,
                "A closed boundary candidate cannot contain an articulation vertex."));
        checks.add(ConditionResult.pass("CYCLE_RANK", "Cycle rank E-V+C = " + cycleRank + "."));
        checks.add(ConditionResult.pass("VERTEX_CONNECTIVITY", "Vertex connectivity is " + vertexConnectivity + "."));
        checks.add(ConditionResult.pass("EDGE_CONNECTIVITY", "Edge connectivity is " + edgeConnectivity + "."));

        boolean cycleFamily=simple&&connected&&expectedDegree==2&&regular&&n>=3;
        AutomorphismAnalyzer.Result symmetry = cycleFamily
                ?new AutomorphismAnalyzer.Result(2L*n,true)
                :simple && n <= 12?new AutomorphismAnalyzer().analyze(n, edges)
                :new AutomorphismAnalyzer.Result(0, false);
        FaceSetSearch faceSearch = simple && connected ? sphericalFaceSearch(adjacency, edges.size())
                : new FaceSetSearch(List.of(), 0);
        List<List<Integer>> faces = faceSearch.faces();
        Boolean planar = simple && connected ? !faces.isEmpty() : false;

        boolean eligible;
        EligibilityStatus status;
        if (profile == ExperimentProfile.CLOSED_2D_AREA) {
            boolean k2 = expectedDegree == 2 && regular;
            boolean oneCycle = connected && k2 && n >= 3 && cycleRank == 1;
            checks.add(result("K_EQUALS_2", k2, "The single-boundary profile requires K=2."));
            checks.add(result("ONE_CYCLE_CONTAINS_ALL_VERTICES", oneCycle,
                    "A connected simple 2-regular graph is one cycle containing every vertex."));
            checks.add(result("NO_BRANCHES", k2, "Every boundary vertex must have exactly two neighbors."));
            checks.add(result("NO_DISCONNECTED_SECONDARY_CYCLE", connected,
                    "No secondary disconnected cycle is permitted."));
            checks.add(new ConditionResult("GEOMETRIC_PLANARITY", ConditionOutcome.NOT_DETERMINABLE_FROM_TOPOLOGY,
                    "Coordinate coplanarity is a Stage 2 condition.", null, null, null));
            checks.add(new ConditionResult("POSITIVE_AREA", ConditionOutcome.NOT_DETERMINABLE_FROM_TOPOLOGY,
                    "Positive area requires Stage 2 coordinates.", null, null, null));
            eligible = simple && oneCycle && bridges == 0 && articulations == 0 && canonical;
            status = eligible ? EligibilityStatus.ELIGIBLE_FOR_GEOMETRY_TEST : EligibilityStatus.INELIGIBLE;
        } else if (profile == ExperimentProfile.CLOSED_3D_VOLUME) {
            boolean enoughVertices = n >= 4;
            boolean degree = adjacency.stream().allMatch(v -> v.size() >= profile.minimumDegree());
            boolean cycles = cycleRank >= 3;
            checks.add(result("AT_LEAST_FOUR_VERTICES", enoughVertices, "A volume boundary needs at least four vertices."));
            checks.add(result("MINIMUM_VERTEX_DEGREE", degree, "Every vertex must have degree at least three."));
            checks.add(result("SUFFICIENT_CYCLE_STRUCTURE", cycles,
                    "Cycle rank must be at least three to support candidate faces."));
            checks.add(ConditionResult.unknown("CLOSED_BOUNDARY",
                    "Topology passes necessary checks; a face set and closed geometric boundary remain undetermined until Stage 2."));
            eligible = simple && connected && regular && enoughVertices && degree && bridges == 0
                    && articulations == 0 && cycles && canonical;
            status = eligible ? EligibilityStatus.ELIGIBLE_FOR_GEOMETRY_TEST : EligibilityStatus.INELIGIBLE;
        } else {
            boolean fixedDegree = regular && expectedDegree >= 3;
            boolean threeConnected = vertexConnectivity != null && vertexConnectivity >= 3;
            boolean faceCycles = faces.stream().allMatch(f -> f.size() >= 3 && f.stream().distinct().count() == f.size());
            boolean incidence = !faces.isEmpty() && edgeIncidenceTwo(faces, edges);
            int euler = n - edges.size() + faces.size();
            checks.add(result("VALID_FIXED_DEGREE", fixedDegree, "The selected fixed degree must be at least three."));
            checks.add(result("GRAPH_PLANARITY", planar, "A combinatorial sphere embedding must be available."));
            checks.add(measured("THREE_VERTEX_CONNECTED", threeConnected, vertexConnectivity, 3,
                    "Convex polyhedral topology requires vertex connectivity of at least three."));
            checks.add(result("SIMPLE_TOPOLOGICAL_FACE_CYCLES", faceCycles,
                    "Every embedded face boundary must be a simple cycle."));
            checks.add(result("TWO_FACES_PER_EDGE", incidence,
                    "Every graph edge must be incident to exactly two topological faces."));
            checks.add(result("CONNECTED_COMBINATORIAL_BOUNDARY", !faces.isEmpty(),
                    "The selected face complex must be connected."));
            checks.add(measured("SPHERE_LIKE_EULER_DIAGNOSTIC", euler == 2, euler, 2,
                    "V-E+F=2 is diagnostic and is not used alone."));
            eligible = simple && connected && fixedDegree && threeConnected && planar && faceCycles
                    && incidence && euler == 2 && canonical;
            status = eligible ? EligibilityStatus.POLYHEDRAL_TOPOLOGY_CANDIDATE : EligibilityStatus.INELIGIBLE;
        }
        String conclusion = eligible
                ? "All topology-only necessary conditions for " + profile
                    + " passed. Closed geometric boundary remains undetermined until Stage 2."
                : "One or more topology-only necessary conditions for " + profile + " failed.";
        FaceCompositionReport faceComposition=classifyFaceComposition(n,expectedDegree,edges,profile,
                composition,faceSearch,connected);
        if(composition.mode()==FaceCompositionMode.UNIFORM_FACE_SIZE) {
            boolean compositionEligible=faceComposition.match()==FaceCompositionMatch.MATCH;
            checks.add(result("UNIFORM_FACE_SIZE",compositionEligible,faceComposition.explanation()));
            eligible &= compositionEligible;
            status=eligible?EligibilityStatus.POLYHEDRAL_TOPOLOGY_CANDIDATE:EligibilityStatus.INELIGIBLE;
            conclusion=eligible
                    ?"The topology satisfies the topology-only requirements of the selected uniform-face experiment. A valid geometric realization has not yet been established."
                    :faceComposition.explanation();
        }
        return new TopologyEligibilityReport(profile, status, conclusion, checks, bridges,
                articulations, vertexConnectivity, edgeConnectivity, cycleRank, planar,
                symmetry.groupSize(), symmetry.classification(), symmetry.vertexTransitive(), faces,
                faceComposition);
    }

    private static FaceCompositionReport classifyFaceComposition(int n,int k,
            List<DiscoveryConnection> edges,ExperimentProfile profile,FaceComposition composition,
            FaceSetSearch search,boolean connected) {
        FaceCompositionArithmetic arithmetic=composition.mode()==FaceCompositionMode.UNIFORM_FACE_SIZE
                ?FaceCompositionArithmetic.evaluate(n,k,composition.commonFaceEdgeCount(),profile):null;
        Map<Integer,Integer> distribution=new TreeMap<>();
        search.faces().forEach(face->distribution.merge(face.size(),1,Integer::sum));
        List<GeometryConfigurationStatusCode> reasons=new ArrayList<>();
        FaceCompositionMatch match=FaceCompositionMatch.UNDETERMINED;
        if(search.solutionCount()==0) reasons.add(GeometryConfigurationStatusCode.NO_COMBINATORIAL_FACE_SET);
        else if(search.solutionCount()>1) reasons.add(GeometryConfigurationStatusCode.AMBIGUOUS_COMBINATORIAL_FACE_SET);
        else if(!connected) reasons.add(GeometryConfigurationStatusCode.OPEN_COMBINATORIAL_BOUNDARY);
        else if(!edgeIncidenceTwo(search.faces(),edges)) reasons.add(GeometryConfigurationStatusCode.NON_MANIFOLD_COMBINATORIAL_BOUNDARY);
        else if(search.faces().stream().anyMatch(face->face.size()<3||face.stream().distinct().count()!=face.size()))
            reasons.add(GeometryConfigurationStatusCode.NO_COMBINATORIAL_FACE_SET);
        else if(composition.mode()==FaceCompositionMode.UNIFORM_FACE_SIZE) {
            boolean uniform=distribution.size()==1;
            if(!uniform) reasons.add(GeometryConfigurationStatusCode.NON_UNIFORM_FACE_SIZE);
            else if(distribution.keySet().iterator().next()!=composition.commonFaceEdgeCount())
                reasons.add(GeometryConfigurationStatusCode.FACE_SIZE_MISMATCH);
            else { reasons.add(GeometryConfigurationStatusCode.ELIGIBLE_FOR_GEOMETRY_TEST); match=FaceCompositionMatch.MATCH; }
        }
        if(composition.mode()==FaceCompositionMode.DISCOVER) match=search.solutionCount()==1
                ?FaceCompositionMatch.MATCH:FaceCompositionMatch.UNDETERMINED;
        else if(!reasons.contains(GeometryConfigurationStatusCode.ELIGIBLE_FOR_GEOMETRY_TEST)
                &&search.solutionCount()==1) match=FaceCompositionMatch.NO_MATCH;
        String explanation=match==FaceCompositionMatch.MATCH
                ?"Face composition: MATCH. Stage 1 topology-only conditions passed; geometric realization remains undetermined."
                :"Face composition: "+(match==FaceCompositionMatch.NO_MATCH?"NO MATCH":"UNDETERMINED")
                +". Reasons: "+reasons;
        return new FaceCompositionReport(composition,arithmetic,match,search.faces().size(),
                distribution,search.faces(),reasons,explanation);
    }

    private static ConditionResult result(String name, boolean pass, String text) {
        return pass ? ConditionResult.pass(name, text) : ConditionResult.fail(name, text);
    }
    private static ConditionResult measured(String name, boolean pass, double value, double expected, String text) {
        return ConditionResult.measured(name, pass, value, expected, 0, text);
    }
    private static List<Set<Integer>> adjacency(int n, List<DiscoveryConnection> edges) {
        List<Set<Integer>> a = new ArrayList<>();
        for (int i = 0; i < n; i++) a.add(new HashSet<>());
        for (DiscoveryConnection e : edges) if (e.firstNodeId() >= 0 && e.secondNodeId() >= 0
                && e.firstNodeId() < n && e.secondNodeId() < n && e.firstNodeId() != e.secondNodeId()) {
            a.get(e.firstNodeId()).add(e.secondNodeId()); a.get(e.secondNodeId()).add(e.firstNodeId());
        }
        return a;
    }
    private static int components(List<Set<Integer>> a, Set<Integer> removed) {
        boolean[] seen = new boolean[a.size()]; int count = 0;
        for (int root = 0; root < a.size(); root++) if (!removed.contains(root) && !seen[root]) {
            count++; ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(root); seen[root] = true;
            while (!q.isEmpty()) for (int next : a.get(q.remove()))
                if (!removed.contains(next) && !seen[next]) { seen[next] = true; q.add(next); }
        }
        return count;
    }
    private static int articulationCount(List<Set<Integer>> a) {
        if (components(a, Set.of()) != 1) return 0; int count = 0;
        for (int v = 0; v < a.size(); v++) if (components(a, Set.of(v)) > 1) count++;
        return count;
    }
    private static int bridgeCount(List<Set<Integer>> a) {
        int count = 0;
        for (int u = 0; u < a.size(); u++) for (int v : List.copyOf(a.get(u))) if (u < v) {
            a.get(u).remove(v); a.get(v).remove(u);
            if (components(a, Set.of()) > 1) count++;
            a.get(u).add(v); a.get(v).add(u);
        }
        return count;
    }
    private static int vertexConnectivity(List<Set<Integer>> a) {
        int n = a.size(); if (n <= 1) return 0;
        int minDegree = a.stream().mapToInt(Set::size).min().orElse(0);
        for (int size = 1; size <= minDegree; size++) if (disconnectingVertexSubset(a, size, 0, new HashSet<>())) return size;
        return minDegree;
    }
    private static boolean disconnectingVertexSubset(List<Set<Integer>> a, int left, int start, Set<Integer> removed) {
        if (left == 0) return a.size() - removed.size() > 1 && components(a, removed) > 1;
        for (int i = start; i <= a.size() - left; i++) { removed.add(i);
            if (disconnectingVertexSubset(a, left - 1, i + 1, removed)) return true; removed.remove(i); }
        return false;
    }
    private static int edgeConnectivity(List<Set<Integer>> a, List<DiscoveryConnection> edges) {
        int minDegree = a.stream().mapToInt(Set::size).min().orElse(0);
        for (int size = 1; size <= minDegree; size++) if (disconnectingEdges(a, edges, size, 0, new ArrayList<>())) return size;
        return minDegree;
    }
    private static boolean disconnectingEdges(List<Set<Integer>> a, List<DiscoveryConnection> edges,
            int left, int start, List<DiscoveryConnection> removed) {
        if (left == 0) {
            for (DiscoveryConnection e : removed) { a.get(e.firstNodeId()).remove(e.secondNodeId()); a.get(e.secondNodeId()).remove(e.firstNodeId()); }
            boolean disconnected = components(a, Set.of()) > 1;
            for (DiscoveryConnection e : removed) { a.get(e.firstNodeId()).add(e.secondNodeId()); a.get(e.secondNodeId()).add(e.firstNodeId()); }
            return disconnected;
        }
        for (int i = start; i <= edges.size() - left; i++) { removed.add(edges.get(i));
            if (disconnectingEdges(a, edges, left - 1, i + 1, removed)) return true; removed.remove(removed.size() - 1); }
        return false;
    }

    private static List<List<Integer>> sphericalFaceSet(List<Set<Integer>> a, int edgeCount) {
        return sphericalFaceSearch(a,edgeCount).faces();
    }
    private record FaceSetSearch(List<List<Integer>> faces,int solutionCount) {}
    private static FaceSetSearch sphericalFaceSearch(List<Set<Integer>> a, int edgeCount) {
        int required = 2 - a.size() + edgeCount;
        if (required < 1 || edgeCount > 30) return new FaceSetSearch(List.of(),0);
        List<List<Integer>> cycles = chordlessCycles(a);
        int[] use = new int[edgeCount];
        List<Long> edgeKeys = new ArrayList<>();
        for (int u = 0; u < a.size(); u++) for (int v : a.get(u)) if (u < v) edgeKeys.add(key(u, v));
        List<List<List<Integer>>> solutions=new ArrayList<>();
        chooseFaces(cycles,0,required,new ArrayList<>(),use,edgeKeys,solutions);
        return new FaceSetSearch(solutions.isEmpty()?List.of():solutions.getFirst(),solutions.size());
    }
    private static void chooseFaces(List<List<Integer>> cycles, int at, int remaining,
            List<List<Integer>> selected, int[] use, List<Long> keys,
            List<List<List<Integer>>> solutions) {
        if(solutions.size()>=2)return;
        if (remaining == 0) {if(Arrays.stream(use).allMatch(v -> v == 2))
            solutions.add(selected.stream().map(List::copyOf).toList());return;}
        if (cycles.size() - at < remaining) return;
        for (int i = at; i < cycles.size(); i++) {
            List<Integer> c = cycles.get(i); List<Integer> positions = new ArrayList<>(); boolean valid = true;
            for (int j = 0; j < c.size(); j++) { int p = keys.indexOf(key(c.get(j), c.get((j + 1) % c.size())));
                if (p < 0 || use[p] >= 2) { valid = false; break; } positions.add(p); }
            if (!valid) continue; positions.forEach(p -> use[p]++); selected.add(c);
            chooseFaces(cycles, i + 1, remaining - 1, selected, use, keys,solutions);
            selected.remove(selected.size() - 1); positions.forEach(p -> use[p]--);
        }
    }
    private static List<List<Integer>> chordlessCycles(List<Set<Integer>> a) {
        List<List<Integer>> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (int start = 0; start < a.size(); start++) enumerateCycles(start, start, a, new boolean[a.size()], new ArrayList<>(), seen, result);
        return result;
    }
    private static void enumerateCycles(int start, int current, List<Set<Integer>> a, boolean[] used,
            List<Integer> path, Set<String> seen, List<List<Integer>> out) {
        used[current] = true; path.add(current);
        for (int next : a.get(current)) if (next == start && path.size() >= 3) {
            List<Integer> c = canonical(path); String id = c.toString();
            if (seen.add(id) && chordless(c, a)) out.add(c);
        } else if (!used[next] && next >= start) enumerateCycles(start, next, a, used, path, seen, out);
        path.remove(path.size() - 1); used[current] = false;
    }
    private static boolean chordless(List<Integer> c, List<Set<Integer>> a) {
        for (int i = 0; i < c.size(); i++) for (int j = i + 1; j < c.size(); j++)
            if (a.get(c.get(i)).contains(c.get(j)) && j != i + 1 && !(i == 0 && j == c.size() - 1)) return false;
        return true;
    }
    private static List<Integer> canonical(List<Integer> path) {
        int min = path.stream().mapToInt(Integer::intValue).min().orElseThrow(), at = path.indexOf(min), n = path.size();
        List<Integer> forward = new ArrayList<>(), reverse = new ArrayList<>();
        for (int i = 0; i < n; i++) { forward.add(path.get((at + i) % n)); reverse.add(path.get((at - i + n) % n)); }
        for (int i = 0; i < n; i++) { int cmp = Integer.compare(forward.get(i), reverse.get(i)); if (cmp < 0) return forward; if (cmp > 0) return reverse; }
        return forward;
    }
    private static boolean edgeIncidenceTwo(List<List<Integer>> faces, List<DiscoveryConnection> edges) {
        for (DiscoveryConnection e : edges) { long key = e.undirectedKey(); int count = 0;
            for (List<Integer> face : faces) for (int i = 0; i < face.size(); i++)
                if (key(face.get(i), face.get((i + 1) % face.size())) == key) count++;
            if (count != 2) return false;
        }
        return true;
    }
    private static long key(int a, int b) { return ((long)Math.min(a,b) << 32) | (Math.max(a,b) & 0xffffffffL); }
}
