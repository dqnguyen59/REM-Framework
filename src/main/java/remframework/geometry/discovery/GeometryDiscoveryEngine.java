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

public final class GeometryDiscoveryEngine {
    @FunctionalInterface
    public interface ProgressListener {
        void update(int iteration, GeometryDiscoveryMetrics metrics);
    }

    public GeometryDiscoveryResult relax(GeometryDiscoveryModel input,
                                         GeometryDiscoverySettings settings) {
        return relax(input, settings, null);
    }

    public GeometryDiscoveryResult relax(GeometryDiscoveryModel input,
                                         GeometryDiscoverySettings settings,
                                         ProgressListener progressListener) {
        GeometryDiscoverySession session = new GeometryDiscoverySession(input, settings);
        session.start();
        while (!session.isFinished()) {
            session.step();
            if (progressListener != null
                    && session.state().iteration() % settings.progressInterval() == 0
                    && !session.isFinished())
                progressListener.update(session.state().iteration(), session.state().metrics());
        }
        return session.result();
    }
}
