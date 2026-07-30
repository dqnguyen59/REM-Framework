package remframework.geometry.discovery;

/**
 * Centralized responsive geometry for the integrated discovery workspace.
 * Coordinates use the application's top-left Nuklear convention.
 */
public record GeometryDiscoveryWorkspaceLayout(
        Bounds workflow, Bounds status, Bounds details, Bounds viewport,
        int topBarHeight, int statusBarHeight) {

    public static final int PREFERRED_LEFT_WIDTH = 335;
    public static final int PREFERRED_RIGHT_WIDTH = 360;
    public static final int PREFERRED_BOTTOM_HEIGHT = 285;
    public static final int MINIMUM_VIEWPORT_WIDTH = 320;
    public static final int MINIMUM_VIEWPORT_HEIGHT = 200;
    public static final int MINIMUM_PANEL_WIDTH = 150;
    public static final int MINIMUM_BOTTOM_HEIGHT = 120;

    public static GeometryDiscoveryWorkspaceLayout calculate(
            int width, int height, int topBarHeight, int statusBarHeight) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        int top = clamp(topBarHeight, 0, Math.max(0, h - 1));
        int statusBar = clamp(statusBarHeight, 0, Math.max(0, h - top - 1));
        int bodyHeight = Math.max(1, h - top - statusBar);

        int viewportMinimumWidth = Math.min(MINIMUM_VIEWPORT_WIDTH,
                Math.max(80, w / 3));
        int sideBudget = Math.max(0, w - viewportMinimumWidth);
        int left = Math.min(PREFERRED_LEFT_WIDTH, sideBudget / 2);
        int right = Math.min(PREFERRED_RIGHT_WIDTH, sideBudget - left);
        if (sideBudget >= MINIMUM_PANEL_WIDTH * 2) {
            left = Math.max(MINIMUM_PANEL_WIDTH, left);
            right = Math.max(MINIMUM_PANEL_WIDTH, right);
            if (left + right > sideBudget) right = sideBudget - left;
        }

        int viewportMinimumHeight = Math.min(MINIMUM_VIEWPORT_HEIGHT,
                Math.max(80, bodyHeight / 2));
        int bottom = Math.min(PREFERRED_BOTTOM_HEIGHT,
                Math.max(0, bodyHeight - viewportMinimumHeight));
        if (bodyHeight >= MINIMUM_BOTTOM_HEIGHT + viewportMinimumHeight)
            bottom = Math.max(MINIMUM_BOTTOM_HEIGHT, bottom);

        int centerWidth = Math.max(1, w - left - right);
        int centerHeight = Math.max(1, bodyHeight - bottom);
        Bounds workflow = new Bounds(0, top, left, bodyHeight);
        Bounds currentStatus = new Bounds(w - right, top, right, centerHeight);
        Bounds details = new Bounds(left, top + centerHeight, centerWidth, bottom);
        Bounds viewport = new Bounds(left, top, centerWidth, centerHeight);
        return new GeometryDiscoveryWorkspaceLayout(
                workflow, currentStatus, details, viewport, top, statusBar);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Bounds(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean overlaps(Bounds other) {
            return x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }
}
