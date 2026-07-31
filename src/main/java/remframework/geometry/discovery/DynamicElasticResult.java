package remframework.geometry.discovery;

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
