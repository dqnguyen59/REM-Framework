package remframework.geometry.discovery;

/** Run-to-completion compatibility facade over the incremental session. */
public final class GeometryTestSession {
    private final GeometryTestIncrementalSession incremental;

    public GeometryTestSession(GeometryConfiguration configuration, GeometryTestRequest request) {
        incremental = new GeometryTestIncrementalSession(configuration, request);
    }

    public GeometryTestResult run() {
        incremental.resume();
        while (incremental.state() == GeometryTestSessionState.RUNNING
                || incremental.state() == GeometryTestSessionState.READY) {
            incremental.step();
        }
        return incremental.result();
    }
}
