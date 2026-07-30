package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import remframework.gui.model.NuklearModel;
import remframework.gui.view.frames.AbstractFrame;
import remframework.gui.view.frames.GeometryDiscoveryNewFrame;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryLayoutTest {

    @Test void fixedRegionsRemainAttachedAcrossResizeAndExcludeViewport() {
        GeometryDiscoveryWorkspaceLayout first =
                GeometryDiscoveryWorkspaceLayout.calculate(1280, 800, 30, 30);
        GeometryDiscoveryWorkspaceLayout second =
                GeometryDiscoveryWorkspaceLayout.calculate(1700, 1000, 30, 30);
        assertEquals(1280, first.status().right());
        assertEquals(1700, second.status().right());
        assertEquals(800 - 30, first.details().bottom());
        assertEquals(1000 - 30, second.details().bottom());
        assertFalse(first.viewport().overlaps(first.workflow()));
        assertFalse(first.viewport().overlaps(first.status()));
        assertFalse(first.viewport().overlaps(first.details()));
        assertEquals(first.workflow().right(), first.viewport().x());
        assertEquals(first.status().x(), first.viewport().right());
    }

    @Test void smallWindowClampsWithoutPanelOverlap() {
        GeometryDiscoveryWorkspaceLayout layout =
                GeometryDiscoveryWorkspaceLayout.calculate(480, 320, 30, 30);
        assertTrue(layout.viewport().width() > 0);
        assertTrue(layout.viewport().height() > 0);
        assertFalse(layout.workflow().overlaps(layout.viewport()));
        assertFalse(layout.status().overlaps(layout.viewport()));
        assertFalse(layout.details().overlaps(layout.viewport()));
        assertEquals(480, layout.status().right());
        assertEquals(290, layout.details().bottom());
    }

    @Test void fixedGeometryIsPureAndCannotBeAffectedByPersistedCoordinates() {
        GeometryDiscoveryWorkspaceLayout before =
                GeometryDiscoveryWorkspaceLayout.calculate(1200, 700, 30, 30);
        GeometryDiscoveryWorkspaceLayout after =
                GeometryDiscoveryWorkspaceLayout.calculate(1200, 700, 30, 30);
        assertEquals(before, after);
        assertEquals(0, after.workflow().x());
        assertEquals(1200, after.status().right());
    }

    @Test void modalWizardIsQueuedAbovePanelsAndBlocksBackgroundInput() {
        NuklearModel model = new NuklearModel();
        AbstractFrame panel = model.showFrame(
                NuklearModel.Frame.geometryDiscoveryControls);
        AbstractFrame wizard = model.showFrame(NuklearModel.Frame.geometryDiscoveryNew);
        assertInstanceOf(GeometryDiscoveryNewFrame.class, wizard);
        assertTrue(wizard.isDialog());
        assertTrue(model.isAnyDialogActive());
        assertTrue(model.isInputBlockedFor(panel));
        assertFalse(model.isInputBlockedFor(wizard));
        assertTrue(model.getFrames2Add().indexOf(wizard)
                > model.getFrames2Add().indexOf(panel));
        model.closeFrame(NuklearModel.Frame.geometryDiscoveryNew);
        assertFalse(model.isInputBlockedFor(panel));
    }

    @Test void wizardFormRowsAndFooterRemainSeparatedAtMultipleSizes() {
        GeometryDiscoveryWizardFormLayout calculator =
                new GeometryDiscoveryWizardFormLayout();
        List<GeometryDiscoveryWizardFormLayout.FieldKind> fields = List.of(
                GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                GeometryDiscoveryWizardFormLayout.FieldKind.DESCRIPTION,
                GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD);
        for (int[] size : List.of(new int[]{680, 560}, new int[]{360, 260})) {
            GeometryDiscoveryWizardFormLayout.Layout layout =
                    calculator.calculate(size[0], size[1], fields);
            for (int i = 0; i < layout.rows().size(); i++) {
                assertFalse(layout.rows().get(i).label()
                        .overlaps(layout.rows().get(i).control()));
                if (i > 0) assertTrue(layout.rows().get(i).label().y()
                        >= layout.rows().get(i - 1).label().bottom());
            }
            assertEquals(GeometryDiscoveryWizardFormLayout.DESCRIPTION_ROW_HEIGHT,
                    layout.rows().get(1).control().height());
            assertTrue(layout.footer().y() >= layout.visibleContentHeight());
            assertTrue(layout.footer().bottom() <= Math.max(180, size[1])
                    + GeometryDiscoveryWizardFormLayout.ROW_SPACING);
        }
    }

    @Test void wizardTopologyGeometryAndSearchFieldsUseDistinctRowsAndClearScroll()
            throws Exception {
        GeometryDiscoveryWizardFormLayout calculator =
                new GeometryDiscoveryWizardFormLayout();
        for (List<GeometryDiscoveryWizardFormLayout.FieldKind> page : List.of(
                List.of(GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.HELP),
                List.of(GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD),
                List.of(GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD,
                        GeometryDiscoveryWizardFormLayout.FieldKind.STANDARD))) {
            GeometryDiscoveryWizardFormLayout.Layout layout =
                    calculator.calculate(640, 500, page);
            for (int i = 1; i < layout.rows().size(); i++)
                assertTrue(layout.rows().get(i).label().y()
                        >= layout.rows().get(i - 1).label().bottom());
            for (GeometryDiscoveryWizardFormLayout.Row row : layout.rows())
                assertFalse(row.label().overlaps(row.control()));
        }
        String source = Files.readString(Path.of(
                "src/main/java/remframework/gui/view/frames/GeometryDiscoveryNewFrame.java"));
        for (String label : List.of("Initial topology", "Neighbor-count policy",
                "Minimum neighbors per node", "Target initial neighbors per node",
                "Maximum neighbors per node", "Initial geometry", "Search method",
                "Search iterations", "Mutations per iteration", "Archive size"))
            assertTrue(source.contains("\"" + label + "\""));
        assertTrue(source.contains("nk_group_set_scroll"));
        assertTrue(source.contains("\"Create Initial Candidate\""));
        assertTrue(source.contains("\"Create and Relax Geometry\""));
        assertTrue(source.contains("\"Create and Start Topology Search\""));
        assertFalse(source.contains("\"Create Experiment\""));
        assertFalse(source.contains("\"Create and Relax\""));
        assertFalse(source.contains("\"Start Discovery\""));
    }
}
