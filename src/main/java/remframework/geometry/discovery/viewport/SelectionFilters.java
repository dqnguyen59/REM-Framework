package remframework.geometry.discovery.viewport;

/** View-only picking filters. */
public record SelectionFilters(boolean vertices, boolean edges, boolean faces, boolean center) {
    public static SelectionFilters all() { return new SelectionFilters(true, true, true, true); }
}
