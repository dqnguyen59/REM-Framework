package remframework.geometry.discovery.realization.session;

import org.joml.Vector3d;
import java.util.*;

/**
 * Evaluates the complete outward face half-space condition.<br><br>
 * The objective is the mean squared positive signed distance, normalized by
 * the supplied target length. Face winding is made coherent globally and is
 * reversed at most once when the selected center identifies all normals as
 * inward.
 */
public final class FaceHalfSpaceConvexityCalculator {
    private static final double EPS = 1e-12;

    public FaceHalfSpaceConvexityDiagnostics calculate(List<List<Integer>> input,
            List<Vector3d> coordinates, double tolerance, double targetLength) {
        double scale = validScale(targetLength);
        if (input == null || coordinates == null || input.isEmpty() || coordinates.isEmpty())
            return unavailable(FaceHalfSpaceConvexityStatus.UNAVAILABLE_FACE_COMPLEX_INVALID,
                    "Face complex and coordinates are required.", tolerance, scale);
        if (!validComplex(input, coordinates.size()))
            return unavailable(FaceHalfSpaceConvexityStatus.UNAVAILABLE_FACE_COMPLEX_INVALID,
                    "Face complex contains invalid cycles, indices, or edge incidence.", tolerance, scale);
        List<List<Integer>> faces = coherentFaces(input);
        if (faces == null)
            return unavailable(FaceHalfSpaceConvexityStatus.UNAVAILABLE_INCOHERENT_ORIENTATION,
                    "Face cycles cannot be assigned one coherent manifold winding.", tolerance, scale);
        Vector3d center = new Vector3d();
        coordinates.forEach(center::add);
        center.div(coordinates.size());
        List<Plane> planes = new ArrayList<>();
        for (List<Integer> face : faces) {
            Plane plane = plane(face, coordinates);
            if (plane == null)
                return unavailable(FaceHalfSpaceConvexityStatus.UNAVAILABLE_DEGENERATE_FACE,
                        "At least one face has a degenerate plane.", tolerance, scale);
            planes.add(plane);
        }
        boolean reverse = false;
        int positive = 0, negative = 0;
        for (Plane plane : planes) {
            double radial = plane.normal.dot(new Vector3d(plane.point).sub(center));
            if (!Double.isFinite(radial) || Math.abs(radial) <= EPS)
                return unavailable(FaceHalfSpaceConvexityStatus.UNAVAILABLE_INVALID_CENTER,
                        "A face centroid coincides with the selected geometry center.", tolerance, scale);
            if (radial > 0) positive++; else negative++;
        }
        if (positive == 0) reverse = true;
        if (reverse) planes = planes.stream().map(p -> new Plane(p.point,
                new Vector3d(p.normal).negate(), p.vertices)).toList();

        int tested = 0, violating = 0, invalid = 0, worstFace = -1, worstVertex = -1;
        double sum = 0, maximum = 0;
        for (int faceId = 0; faceId < planes.size(); faceId++) {
            Plane face = planes.get(faceId);
            for (int vertexId = 0; vertexId < coordinates.size(); vertexId++) {
                if (face.vertices.contains(vertexId)) continue;
                Vector3d vertex = coordinates.get(vertexId);
                if (vertex == null || !vertex.isFinite()) { invalid++; continue; }
                double h = face.normal.dot(new Vector3d(vertex).sub(face.point));
                if (!Double.isFinite(h)) { invalid++; continue; }
                double violation = Math.max(0, h - tolerance);
                tested++;
                if (violation > 0) violating++;
                if (violation > maximum) { maximum = violation; worstFace = faceId; worstVertex = vertexId; }
                sum += violation * violation;
            }
        }
        if (tested == 0 || invalid > 0 && tested == 0)
            return unavailable(FaceHalfSpaceConvexityStatus.NUMERICALLY_INVALID,
                    "No valid nonincident face-vertex pairs were available.", tolerance, scale);
        double objective = sum / tested / (scale * scale);
        if (!Double.isFinite(objective))
            return unavailable(FaceHalfSpaceConvexityStatus.NUMERICALLY_INVALID,
                    "Face half-space objective is not finite.", tolerance, scale);
        return new FaceHalfSpaceConvexityDiagnostics(violating == 0 ? FaceHalfSpaceConvexityStatus.SATISFIED
                : FaceHalfSpaceConvexityStatus.VIOLATING, tested, violating, invalid, maximum,
                Math.sqrt(sum / tested), worstFace, worstVertex, objective, tolerance, scale, reverse,
                reverse ? "A single global face-cycle reversal was applied." : "Coherent outward face orientation used.");
    }

    public double objective(List<List<Integer>> faces, List<Vector3d> coordinates,
            double tolerance, double targetLength) {
        return calculate(faces, coordinates, tolerance, targetLength).objective();
    }

    private static FaceHalfSpaceConvexityDiagnostics unavailable(FaceHalfSpaceConvexityStatus status,
            String explanation, double tolerance, double scale) {
        return FaceHalfSpaceConvexityDiagnostics.unavailable(status, explanation, tolerance, scale);
    }
    private static double validScale(double scale) { return Double.isFinite(scale) && scale > EPS ? scale : 1.0; }
    private static boolean validComplex(List<List<Integer>> faces, int vertexCount) {
        Map<Long, Integer> incidence = new HashMap<>();
        for (List<Integer> face : faces) {
            if (face == null || face.size() < 3 || new HashSet<>(face).size() != face.size()) return false;
            for (int id : face) if (id < 0 || id >= vertexCount) return false;
            for (int i = 0; i < face.size(); i++) incidence.merge(key(face.get(i), face.get((i + 1) % face.size())), 1, Integer::sum);
        }
        return incidence.values().stream().allMatch(count -> count == 2);
    }
    private static List<List<Integer>> coherentFaces(List<List<Integer>> input) {
        List<List<Integer>> faces = input.stream().map(List::copyOf).toList();
        Boolean[] flip = new Boolean[faces.size()]; flip[0] = false;
        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(0);
        while (!queue.isEmpty()) { int a = queue.remove();
            for (int b = 0; b < faces.size(); b++) if (a != b) {
                int relation = relation(faces.get(a), faces.get(b)); if (relation == 0) continue;
                boolean wanted = flip[a] ^ (relation == 1);
                if (flip[b] == null) { flip[b] = wanted; queue.add(b); }
                else if (flip[b] != wanted) return null;
            }
        }
        if (Arrays.stream(flip).anyMatch(Objects::isNull)) return null;
        List<List<Integer>> result = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) { List<Integer> face = new ArrayList<>(faces.get(i)); if (flip[i]) Collections.reverse(face); result.add(List.copyOf(face)); }
        return result;
    }
    private static int relation(List<Integer> a, List<Integer> b) { for (int i = 0; i < a.size(); i++) for (int j = 0; j < b.size(); j++) { int x=a.get(i), y=a.get((i+1)%a.size()), u=b.get(j), v=b.get((j+1)%b.size()); if (x==u&&y==v) return 1; if (x==v&&y==u) return -1; } return 0; }
    private static Plane plane(List<Integer> face, List<Vector3d> p) { Vector3d c=new Vector3d(); for(int id:face)c.add(p.get(id)); c.div(face.size()); Vector3d n=new Vector3d(); for(int i=0;i<face.size();i++){Vector3d a=p.get(face.get(i)),b=p.get(face.get((i+1)%face.size())); n.x+=(a.y-b.y)*(a.z+b.z); n.y+=(a.z-b.z)*(a.x+b.x); n.z+=(a.x-b.x)*(a.y+b.y);} return n.lengthSquared()<=EPS?null:new Plane(c,n.normalize(),Set.copyOf(face)); }
    private static long key(int a,int b) { return ((long)Math.min(a,b)<<32)|(Math.max(a,b)&0xffffffffL); }
    private record Plane(Vector3d point, Vector3d normal, Set<Integer> vertices) { }
}
