package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;

/**
 * Display-only reflected-apex layout of the coordinate-free experiment.
 * <br><br>
 * Coordinates are visualization choices, not discovered equilibrium or REM mechanics.
 * Intersecting tetrahedra are retained and diagnosed, never merged or dropped.
 */
public final class FaceSharingTetrahedralPreview {
    private FaceSharingTetrahedralPreview() { }
    public record Preview(GeometryDiscoveryModel model,
            FaceSharingTetrahedralExperiment.Run topology,
            List<GeometryConfigurationStatus> statuses, int overlappingTetrahedronPairs,
            int coincidentNodePairs) {
        public Preview { statuses = List.copyOf(statuses); }
    }

    public static GeometryDiscoveryModel firstLayer(double displayScale) {
        Preview preview = create(1,8,displayScale);
        if(preview.model()==null) throw new IllegalArgumentException(preview.statuses().getFirst().explanation());
        return preview.model();
    }

    /** Retains complete generations at the caller's node budget; maximum display budget is 1000 nodes. */
    public static Preview create(int generation, int maximumNodes, double displayScale) {
        if(!Double.isFinite(displayScale) || displayScale < 1e-6 || displayScale > 1e6
                || maximumNodes > 1000) {
            var failure = status(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                "Preview scale must be between 1e-6 and 1e6; maximumNodes must not exceed 1000 "
                + "because overlap diagnostics compare tetrahedron pairs.",false);
            return new Preview(null,null,List.of(failure),0,0);
        }
        var run = new FaceSharingTetrahedralExperiment().generate(generation,maximumNodes);
        if(run.generations().isEmpty()) return new Preview(null,run,List.of(run.status()),0,0);
        var snapshot = run.generations().getLast();
        return fromSnapshot(snapshot,run,displayScale);
    }

    /** Reuses the same placement and diagnostics for an explicitly retained partial layer. */
    static Preview fromSnapshot(FaceSharingTetrahedralExperiment.Snapshot snapshot,
            FaceSharingTetrahedralExperiment.Run run,double displayScale) {
        double radius = displayScale / (2*Math.sqrt(2));
        List<Vector3d> positions = new ArrayList<>(List.of(
            new Vector3d(radius,radius,radius),new Vector3d(radius,-radius,-radius),
            new Vector3d(-radius,radius,-radius),new Vector3d(-radius,-radius,radius)));
        Map<FaceSharingTetrahedralExperiment.Face,Integer> oppositeByFace = new HashMap<>();
        for(var tetrahedron:snapshot.tetrahedra()) {
            if(tetrahedron.generation()>0) {
                var face=new FaceSharingTetrahedralExperiment.Face(tetrahedron.a(),tetrahedron.b(),tetrahedron.c());
                Vector3d a=positions.get(face.a());
                Vector3d normal=new Vector3d(positions.get(face.b())).sub(a)
                    .cross(new Vector3d(positions.get(face.c())).sub(a)).normalize();
                Vector3d apex=new Vector3d(positions.get(oppositeByFace.get(face)));
                double distance=new Vector3d(apex).sub(a).dot(normal);
                positions.add(apex.sub(new Vector3d(normal).mul(2*distance)));
            }
            List<Integer> vertices=vertices(tetrahedron);
            for(var face:tetrahedron.faces()) {
                int opposite=vertices.stream().filter(id -> !face.nodes().contains(id)).findFirst().orElseThrow();
                oppositeByFace.putIfAbsent(face,opposite);
            }
        }
        int coincidences=0,overlaps=0;
        double tolerance=displayScale*1e-9;
        for(int a=0;a<positions.size();a++) for(int b=a+1;b<positions.size();b++)
            if(positions.get(a).distance(positions.get(b))<=tolerance) coincidences++;
        var tetrahedra=snapshot.tetrahedra();
        for(int a=0;a<tetrahedra.size();a++) for(int b=a+1;b<tetrahedra.size();b++)
            if(interiorsOverlap(tetrahedra.get(a),tetrahedra.get(b),positions,tolerance)) overlaps++;
        List<DiscoveryNode> nodes=new ArrayList<>();
        for(int id=0;id<positions.size();id++) nodes.add(new DiscoveryNode(id,positions.get(id),false));
        String description="Display-only reflected layout, generation "+snapshot.generation()+": "
            +snapshot.nodeCount()+" nodes, "+snapshot.edges().size()+" connections, "
            +tetrahedra.size()+" tetrahedra; "+overlaps+" overlapping tetrahedron pairs, "
            +coincidences+" coincident node pairs. No REM equilibrium or isotropy qualification.";
        var model=new GeometryDiscoveryModel("face-sharing-tetrahedral-preview",description,nodes,snapshot.edges());
        List<GeometryConfigurationStatus> statuses=new ArrayList<>(List.of(run.status()));
        if(overlaps>0 || coincidences>0)
            statuses.add(status(GeometryConfigurationStatusCode.SELF_INTERSECTING,
                description+" All graph nodes and edges are retained; this layout fails non-overlap qualification.",false));
        else statuses.add(status(GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED,
            "Display layout only. Pairwise interior-overlap checks passed within tolerance; no full realization qualification.",true));
        return new Preview(model,run,statuses,overlaps,coincidences);
    }

    private static List<Integer> vertices(FaceSharingTetrahedralExperiment.Tetrahedron t) {
        return List.of(t.a(),t.b(),t.c(),t.d());
    }

    /** Separating-axis test for two convex tetrahedra; touching faces/edges are not interior overlap. */
    static boolean interiorsOverlap(FaceSharingTetrahedralExperiment.Tetrahedron a,
            FaceSharingTetrahedralExperiment.Tetrahedron b,List<Vector3d> positions,double tolerance) {
        List<Vector3d> axes=new ArrayList<>();
        for(var t:List.of(a,b)) for(var f:t.faces())
            axes.add(new Vector3d(positions.get(f.b())).sub(positions.get(f.a()))
                .cross(new Vector3d(positions.get(f.c())).sub(positions.get(f.a()))));
        List<Vector3d> edgesA=edges(a,positions),edgesB=edges(b,positions);
        for(var first:edgesA) for(var second:edgesB) axes.add(new Vector3d(first).cross(second));
        for(var axis:axes) {
            double length=axis.length();
            if(length<=tolerance*tolerance) continue;
            axis.div(length);
            double minA=Double.POSITIVE_INFINITY,maxA=Double.NEGATIVE_INFINITY;
            double minB=Double.POSITIVE_INFINITY,maxB=Double.NEGATIVE_INFINITY;
            for(int id:vertices(a)) { double v=positions.get(id).dot(axis);minA=Math.min(minA,v);maxA=Math.max(maxA,v); }
            for(int id:vertices(b)) { double v=positions.get(id).dot(axis);minB=Math.min(minB,v);maxB=Math.max(maxB,v); }
            if(Math.min(maxA,maxB)-Math.max(minA,minB)<=tolerance) return false;
        }
        return true;
    }
    private static List<Vector3d> edges(FaceSharingTetrahedralExperiment.Tetrahedron t,List<Vector3d> positions) {
        List<Vector3d> result=new ArrayList<>();var ids=vertices(t);
        for(int a=0;a<4;a++) for(int b=a+1;b<4;b++)
            result.add(new Vector3d(positions.get(ids.get(b))).sub(positions.get(ids.get(a))));
        return result;
    }
    private static GeometryConfigurationStatus status(GeometryConfigurationStatusCode code,String explanation,boolean proceed) {
        return GeometryConfigurationStatus.of(proceed
            ? GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_INITIALIZATION,code,"Tetrahedral display layout",explanation)
            : GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_INITIALIZATION,code,"Tetrahedral display layout limit",explanation));
    }
}
