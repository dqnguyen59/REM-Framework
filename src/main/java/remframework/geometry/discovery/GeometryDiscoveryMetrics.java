package remframework.geometry.discovery;
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

public record GeometryDiscoveryMetrics(
        int iterationCount,
        boolean converged,
        double totalEdgeLengthError,
        double meanConnectedNeighborDistance,
        double minimumConnectedNeighborDistance,
        double maximumConnectedNeighborDistance,
        double standardDeviationConnectedNeighborDistance,
        double minimumDistinctNodeDistance,
        int collisionViolationCount,
        int connectedComponentCount,
        double extentX,
        double extentY,
        double extentZ,
        String effectiveDimensionality) {

    public boolean allFinite() {
        return Double.isFinite(totalEdgeLengthError)
                && Double.isFinite(meanConnectedNeighborDistance)
                && Double.isFinite(minimumConnectedNeighborDistance)
                && Double.isFinite(maximumConnectedNeighborDistance)
                && Double.isFinite(standardDeviationConnectedNeighborDistance)
                && Double.isFinite(minimumDistinctNodeDistance)
                && Double.isFinite(extentX) && Double.isFinite(extentY)
                && Double.isFinite(extentZ);
    }
}
