package remframework.geometry.discovery.viewport;

/** Immutable selection by canonical topology identifier. */
public record SelectedElement(Type type, String id) {
    public enum Type { CENTER, VERTEX, EDGE, FACE }
    public static SelectedElement center() { return new SelectedElement(Type.CENTER, "geometry-center"); }
    public static SelectedElement vertex(int id) { return new SelectedElement(Type.VERTEX, Integer.toString(id)); }
    public static SelectedElement edge(int a, int b) { return new SelectedElement(Type.EDGE, "edge-" + Math.min(a,b) + "-" + Math.max(a,b)); }
    public static SelectedElement face(String id) { return new SelectedElement(Type.FACE, id); }
}
