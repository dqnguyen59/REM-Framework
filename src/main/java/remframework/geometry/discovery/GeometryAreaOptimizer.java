package remframework.geometry.discovery;

/** @deprecated use {@link remframework.geometry.discovery.optimization.AreaOptimizer}. */
@Deprecated
public final class GeometryAreaOptimizer {
    private final remframework.geometry.discovery.optimization.AreaOptimizer delegate =
            new remframework.geometry.discovery.optimization.AreaOptimizer();
    public GeometryOptimizationResult maximize2d(remframework.geometry.discovery.configuration.Configuration graph,
            remframework.geometry.discovery.realization.session.Realization baseline, ClosedBoundaryResult boundary,
            double target, int iterations) {
        return delegate.maximize2d(graph, baseline, boundary, target, iterations);
    }
}
