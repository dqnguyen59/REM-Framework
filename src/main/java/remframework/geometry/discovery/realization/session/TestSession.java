package remframework.geometry.discovery.realization.session;
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

/** Run-to-completion compatibility facade over the incremental session. */
public final class TestSession {
    private final IncrementalSession incremental;

    public TestSession(Configuration configuration, TestRequest request) {
        incremental = new IncrementalSession(configuration, request);
    }

    public TestResult run() {
        incremental.resume();
        while (incremental.state() == SessionState.RUNNING
                || incremental.state() == SessionState.READY) {
            incremental.step();
        }
        return incremental.result();
    }
}
