package remframework.geometry.discovery.measurement.regularity;

import remframework.geometry.discovery.experiment.ThreeDExperimentConditions;

import java.util.Map;

public record ThreeDConditionReport(boolean basicValidCandidate,boolean constrainedExperimentPassed,
        ThreeDExperimentConditions conditions,Map<String,ConditionConclusion> conclusions,
        String summary){public ThreeDConditionReport{conclusions=Map.copyOf(conclusions);}}
