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

import java.util.List;

public record DynamicElasticResult(
        String runId,
        FixedDegreeGraph graph,
        DynamicElasticSettings settings,
        List<DynamicElasticNode> initialNodes,
        List<DynamicElasticNode> finalNodes,
        DynamicElasticMetrics metrics,
        DynamicElasticResultStatus resultStatus,
        String failureReason,
        long elapsedMilliseconds) {
    public DynamicElasticResult {
        initialNodes = initialNodes.stream().map(DynamicElasticNode::copy).toList();
        finalNodes = finalNodes.stream().map(DynamicElasticNode::copy).toList();
    }
}
