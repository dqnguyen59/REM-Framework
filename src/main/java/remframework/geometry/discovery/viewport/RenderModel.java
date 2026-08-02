package remframework.geometry.discovery.viewport;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Immutable render adapter for topology previews and incremental realizations. */
public record RenderModel(String configurationId,
        GeometryDiscoveryModel model, DisplayTransform transform,
        boolean topologyPreview, long revision, List<CanonicalNodeLabel> nodeLabels,
        List<DerivedFace> derivedFaces) {
    private static final ConcurrentHashMap<String,List<DerivedFace>> FACE_CACHE=new ConcurrentHashMap<>();
    private static final AtomicLong FACE_COMPLEX_BUILDS=new AtomicLong();
    public RenderModel {
        nodeLabels = List.copyOf(nodeLabels);
        derivedFaces=List.copyOf(derivedFaces);
    }
    public static RenderModel topologyPreview(Configuration configuration,
            long revision) {
        List<Vector3d> coordinates = new ArrayList<>();
        int count = configuration.nodeCount();
        for (int id = 0; id < count; id++) {
            double angle = 2 * Math.PI * id / Math.max(1, count);
            double z = count > 4 ? .35 * Math.sin(angle * 2) : 0;
            coordinates.add(new Vector3d(Math.cos(angle), Math.sin(angle), z));
        }
        return of(configuration, coordinates, true, revision);
    }

    public static RenderModel realization(Configuration configuration,
            List<Vector3d> coordinates, long revision) {
        return of(configuration, coordinates, false, revision);
    }

    private static RenderModel of(Configuration configuration,
            List<Vector3d> scientificCoordinates, boolean preview, long revision) {
        DisplayTransform transform =
                DisplayTransform.calculate(scientificCoordinates, 4.0);
        List<DiscoveryNode> nodes = new ArrayList<>();
        for (int id = 0; id < configuration.nodeCount(); id++)
            nodes.add(new DiscoveryNode(id, transform.display(scientificCoordinates.get(id)), id < 2));
        GeometryDiscoveryModel model = new GeometryDiscoveryModel(
                "Phase 2 " + (preview ? "Topology Preview" : "Geometry Realization"),
                preview ? "Graph visualization only - not a geometric realization."
                        : "Display-only transform of an emergent geometry realization.",
                nodes, configuration.edges());
        List<CanonicalNodeLabel> labels = nodes.stream().map(node ->
                new CanonicalNodeLabel(node.id(), Integer.toString(node.id()), node.position())).toList();
        String faceKey=configuration.id()+":"+configuration.provenance().getOrDefault("combinatorialFaces","");List<DerivedFace> faces=FACE_CACHE.computeIfAbsent(faceKey,ignored->{FACE_COMPLEX_BUILDS.incrementAndGet();return new remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer().deriveTopologicalFaces(configuration).stream().map(cycle->new DerivedFace("face-"+cycle,cycle,true,true,0)).toList();});
        return new RenderModel(configuration.id().toString(), model,
                transform, preview, revision, labels,faces);
    }
    public static long faceComplexBuildCount(){return FACE_COMPLEX_BUILDS.get();}
}
