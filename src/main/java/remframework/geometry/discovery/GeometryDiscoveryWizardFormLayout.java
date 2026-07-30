package remframework.geometry.discovery;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry shared by the New Discovery form. Each field owns one non-overlapping
 * row; descriptions receive a taller row and actions remain below content.
 */
public final class GeometryDiscoveryWizardFormLayout {
    public static final int STANDARD_ROW_HEIGHT = 30;
    public static final int DESCRIPTION_ROW_HEIGHT = 92;
    public static final int ROW_SPACING = 6;
    public static final int FOOTER_HEIGHT = 44;
    public static final double LABEL_FRACTION = .38;

    public Layout calculate(int width, int height, List<FieldKind> fields) {
        int safeWidth = Math.max(240, width);
        int safeHeight = Math.max(180, height);
        int labelWidth = Math.max(100, Math.min(240,
                (int) Math.round(safeWidth * LABEL_FRACTION)));
        int controlWidth = Math.max(100, safeWidth - labelWidth - 12);
        int y = 0;
        List<Row> rows = new ArrayList<>();
        for (FieldKind field : fields) {
            int rowHeight = field == FieldKind.DESCRIPTION
                    ? DESCRIPTION_ROW_HEIGHT : field == FieldKind.HELP ? 72
                    : STANDARD_ROW_HEIGHT;
            rows.add(new Row(new Rectangle(0, y, labelWidth, rowHeight),
                    new Rectangle(labelWidth + 12, y, controlWidth, rowHeight)));
            y += rowHeight + ROW_SPACING;
        }
        int contentHeight = Math.max(1, safeHeight - FOOTER_HEIGHT - ROW_SPACING);
        Rectangle footer = new Rectangle(0, contentHeight + ROW_SPACING,
                safeWidth, FOOTER_HEIGHT);
        return new Layout(List.copyOf(rows), footer, contentHeight,
                Math.max(contentHeight, y));
    }

    public enum FieldKind { STANDARD, DESCRIPTION, HELP }
    public record Rectangle(int x, int y, int width, int height) {
        public int bottom() { return y + height; }
        public boolean overlaps(Rectangle other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }
    public record Row(Rectangle label, Rectangle control) { }
    public record Layout(List<Row> rows, Rectangle footer,
                         int visibleContentHeight, int requiredContentHeight) { }
}
