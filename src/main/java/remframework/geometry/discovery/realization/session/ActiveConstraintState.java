package remframework.geometry.discovery.realization.session;

public record ActiveConstraintState(String name,boolean enabled,boolean activeRealizationTarget,
        double currentError,double tolerance,double restoringStrength,boolean passed) { }
