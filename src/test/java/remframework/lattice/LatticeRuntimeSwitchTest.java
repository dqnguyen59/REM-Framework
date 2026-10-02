package remframework.lattice;
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

import org.junit.jupiter.api.Test;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LatticeRuntimeSwitchTest {
    @Test
    void rebuildingClearsStaleNodeDependentState() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Project project = Project.getInstance();
            project.setDimension(2, 2, 2, false);
            Lattice lattice = Lattice.getInstance();

            lattice.clearEntities();
            lattice.buildHeadless();
            awaitReady(project);
            lattice.update();
            Node oldNode = (Node) lattice.getNodeArray()[0];

            lattice.clearEntities();
            lattice.buildHeadless();
            awaitReady(project);
            lattice.update();

            assertFalse(lattice.getEntities().contains(oldNode));
            assertNotSame(oldNode, lattice.getNodeArray()[0]);
            assertSame(lattice.getNode(0, 0, 0), lattice.getNodeArray()[0]);
        });
    }

    private void awaitReady(Project project) throws InterruptedException {
        while (!project.getRemModel().isLoadingReady()) Thread.sleep(5);
    }
}
