package remframework.geometry.discovery.realization.session;

import java.util.List;

public record ConstraintPresentationSummary(List<ConstraintPresentation> constraints,
        int requiredPassed,int requiredTotal,String weakestRequiredConstraint,
        double worstRequiredRatio,List<String> failedDiscreteConstraints,boolean qualified) {
    public ConstraintPresentationSummary { constraints=List.copyOf(constraints);failedDiscreteConstraints=List.copyOf(failedDiscreteConstraints); }
    public static ConstraintPresentationSummary of(List<ConstraintPresentation> constraints){
        int total=0,passed=0;double worst=Double.NaN;String weakest="none";
        java.util.ArrayList<String> discrete=new java.util.ArrayList<>();
        for(ConstraintPresentation c:constraints){
            if(!c.applicable()||!c.requiredForQualification())continue;
            total++;
            boolean ok=c.status()==ConstraintPresentationStatus.SATISFIED||c.status()==ConstraintPresentationStatus.PASS;
            if(ok)passed++;
            if(c.continuous()&&c.errorRatio().available()&&(!Double.isNaN(c.errorRatio().value()))
                    &&(Double.isNaN(worst)||c.errorRatio().value()>worst)){
                worst=c.errorRatio().value();weakest=c.displayName();
            }
            if(!c.continuous()&&!ok)discrete.add(c.displayName()+": "+c.status());
        }
        return new ConstraintPresentationSummary(constraints,passed,total,weakest,worst,
                discrete,passed==total);
    }
}
