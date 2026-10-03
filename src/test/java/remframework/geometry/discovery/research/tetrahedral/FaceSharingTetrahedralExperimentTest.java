package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.*;
import static org.junit.jupiter.api.Assertions.*;
import static remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralExperiment.*;

class FaceSharingTetrahedralExperimentTest {
    private final FaceSharingTetrahedralExperiment experiment = new FaceSharingTetrahedralExperiment();

    @Test void exactSmallGenerationsAndObservedDegrees() {
        Run run = experiment.generate(5,488);
        assertEquals(GeometryConfigurationStatusCode.GENERATED,run.status().code());
        int[] nodes = {4,8,20,56,164,488};
        int[] tetrahedra = {1,5,17,53,161,485};
        int[] faces = {4,12,36,108,324,972};
        int[] edgeCounts = {6,18,54,162,486,1458};
        for (Snapshot s : run.generations()) {
            int g = s.generation();
            assertEquals(nodes[g],s.nodeCount());
            assertEquals(tetrahedra[g],s.tetrahedra().size());
            assertEquals(faces[g],s.exposedFaces().size());
            assertEquals(edgeCounts[g],s.edges().size());
            assertEquals(4 * (int)Math.pow(3,g),s.exposedFaces().size());
            assertEquals(Collections.nCopies(4,3 * (1 << g)),s.degrees().subList(0,4));
            for (int id=4; id<s.nodeCount(); id++)
                assertEquals(3 * (1 << (g-s.nodeBirthGenerations().get(id))),s.degrees().get(id));
            assertEquals(2L*s.nodeCount()-5,s.cycleRank());
            assertEquals(s.cycleRank()-3,s.attachmentReconnections());
            assertEquals(2*s.edges().size(),s.degrees().stream().mapToInt(Integer::intValue).sum());
            assertEquals(s.edges().size(),s.edges().stream().map(DiscoveryConnection::undirectedKey).distinct().count());
            assertTrue(s.faceIncidence().values().stream().allMatch(n -> n==1 || n==2));
            assertEquals(s.tetrahedra().size()-1,s.faceIncidence().values().stream().filter(n -> n==2).count());
            Set<Integer> reached = new HashSet<>(Set.of(0));
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(0));
            var adjacency = s.adjacency();
            while (!queue.isEmpty())
                for (int id : adjacency.get(queue.remove())) if (reached.add(id)) queue.add(id);
            assertEquals(s.nodeCount(),reached.size());
            Map<Face,Integer> derived = new HashMap<>();
            for (Tetrahedron t : s.tetrahedra()) {
                t.faces().forEach(f -> derived.merge(f,1,Integer::sum));
                List<Integer> ids = List.of(t.a(),t.b(),t.c(),t.d());
                for (int a : ids) for (int b : ids)
                    if (a!=b) assertTrue(adjacency.get(a).contains(b));
            }
            assertEquals(derived,s.faceIncidence());
        }
    }

    @Test void firstGenerationHasExactlyOneChildPerSeedFace() {
        Snapshot s = experiment.generate(1,8).generations().getLast();
        assertEquals(List.of(
            new Tetrahedron(0,1,2,3,0),
            new Tetrahedron(0,1,2,4,1),
            new Tetrahedron(0,1,3,5,1),
            new Tetrahedron(0,2,3,6,1),
            new Tetrahedron(1,2,3,7,1)),s.tetrahedra());
        assertEquals(List.of(0,1,2),s.adjacency().get(4));
        assertEquals(List.of(0,1,3),s.adjacency().get(5));
        assertEquals(List.of(0,2,3),s.adjacency().get(6));
        assertEquals(List.of(1,2,3),s.adjacency().get(7));
        assertEquals(4,experiment.generate(1,8).generations().getFirst().nodeCount());
        assertThrows(UnsupportedOperationException.class,() -> s.faceIncidence().clear());
        assertThrows(UnsupportedOperationException.class,() -> s.nodeBirthGenerations().clear());
    }

    @Test void limitsAndInvalidRequestsRetainStructuredReasons() {
        for (int[] request : List.of(new int[]{-1,8},new int[]{1,3})) {
            Run run = experiment.generate(request[0],request[1]);
            assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,run.status().code());
            assertFalse(run.status().mayContinue());
            assertTrue(run.generations().isEmpty());
            assertFalse(run.status().diagnostics().isEmpty());
        }
        Run limited = experiment.generate(Integer.MAX_VALUE,19);
        assertEquals(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,limited.status().code());
        assertFalse(limited.status().mayContinue());
        assertEquals(1,limited.generations().getLast().generation());
        assertEquals(8,limited.generations().getLast().nodeCount());
        assertTrue(limited.status().explanation().contains("20 nodes"));
        assertEquals(experiment.generate(1,8).generations(),limited.generations());
    }

    @Test void capacityGuardIsExplicitAndDoesNotSelectAnAttachmentRule() {
        Snapshot seed = experiment.generate(0,4).generations().getFirst();
        Face face = new Face(2,0,1);
        assertEquals(GeometryConfigurationStatusCode.INVALID_DEGREE,
            new DegreeCapacity(3).checkFreshAttachment(seed,face).code());
        assertEquals(List.of(0,1,2),
            new DegreeCapacity(3).checkFreshAttachment(seed,face).diagnostics().getFirst().affectedNodeIds());
        assertTrue(new DegreeCapacity(4).checkFreshAttachment(seed,face).mayContinue());
        Snapshot next = experiment.generate(1,8).generations().getLast();
        assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
            new DegreeCapacity(10).checkFreshAttachment(next,face).code());
        assertThrows(IllegalArgumentException.class,() -> new DegreeCapacity(2));
        assertEquals(4,seed.nodeCount());
    }
}
