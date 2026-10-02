package remframework.geometry.discovery.realization.sequence;

public record ConstraintSequenceIssue(int stageIndex,ConstraintId constraintId,Severity severity,
        String explanation) {
    public enum Severity { WARNING, BLOCKING }
}
