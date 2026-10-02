package remframework.geometry.discovery;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.realization.session.*;

class NormalizedErrorRatioTest {
    @Test void ratioIsUnboundedAndUsesToleranceAsAcceptanceBoundary(){
        assertRatio(0,1,0,true);assertRatio(.25,1,.25,true);assertRatio(1,1,1,true);
        assertRatio(2,1,2,false);assertRatio(10,1,10,false);
    }
    @Test void exactZeroAndInvalidToleranceAreDeterministic(){
        NormalizedErrorRatio exact=NormalizedErrorRatio.evaluate(0,0,true);
        assertTrue(exact.available());assertEquals(0,exact.value());assertTrue(exact.passes());
        NormalizedErrorRatio exceeded=NormalizedErrorRatio.evaluate(.1,0,true);
        assertEquals(Double.POSITIVE_INFINITY,exceeded.value());assertFalse(exceeded.passes());
        assertEquals(ConstraintPresentationStatus.NUMERICALLY_INVALID,
                NormalizedErrorRatio.evaluate(0,0,false).status());
        assertEquals(ConstraintPresentationStatus.NUMERICALLY_INVALID,
                NormalizedErrorRatio.evaluate(0,-1,false).status());
    }
    @Test void nonFiniteErrorsAreUnavailableAndCannotPass(){
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            NormalizedErrorRatio ratio=NormalizedErrorRatio.evaluate(value,1,false);
            assertFalse(ratio.available());assertFalse(ratio.passes());
            assertEquals(ConstraintPresentationStatus.NUMERICALLY_INVALID,ratio.status());
        }
    }
    @Test void worstRequiredRatioControlsAggregateRatherThanAverage(){
        ConstraintPresentation pass=row("edge","Edge",.1,1);
        ConstraintPresentation fail=row("face","Face",2,1);
        ConstraintPresentation discrete=new ConstraintPresentation("boundary","Boundary",
                ConstraintPresentationMode.REQUIRED_POST_QUALIFICATION,true,true,false,Double.NaN,"","boolean",
                Double.NaN,"",null,null,ConstraintPresentationStatus.FAIL,"failed");
        ConstraintPresentationSummary summary=ConstraintPresentationSummary.of(List.of(pass,fail,discrete));
        assertEquals(1,summary.requiredPassed());assertEquals(3,summary.requiredTotal());
        assertEquals(2,summary.worstRequiredRatio());assertEquals("Face",summary.weakestRequiredConstraint());
        assertFalse(summary.qualified());assertEquals(List.of("Boundary: FAIL"),summary.failedDiscreteConstraints());
    }
    @Test void modesRetainApplicabilityAndForceDistinction(){
        ConstraintPresentation measured=new ConstraintPresentation("d","Dihedral",
                ConstraintPresentationMode.MEASURE_ONLY,true,false,false,.2,"rad","maximum deviation",
                Double.NaN,"not used",null,null,ConstraintPresentationStatus.MEASURED,"measured");
        ConstraintPresentation none=new ConstraintPresentation("v","Vertex radius",
                ConstraintPresentationMode.NONE,false,false,false,Double.NaN,"","maximum deviation",
                Double.NaN,"",null,null,ConstraintPresentationStatus.NOT_APPLICABLE,"disabled");
        assertFalse(measured.activeRealizationForce());assertFalse(measured.requiredForQualification());
        assertNull(measured.errorRatio());assertFalse(none.applicable());
    }
    private static void assertRatio(double error,double tolerance,double expected,boolean pass){
        NormalizedErrorRatio ratio=NormalizedErrorRatio.evaluate(error,tolerance,false);
        assertEquals(expected,ratio.value(),1e-12);assertEquals(pass,ratio.passes());
    }
    private static ConstraintPresentation row(String id,String name,double error,double tolerance){
        NormalizedErrorRatio ratio=NormalizedErrorRatio.evaluate(error,tolerance,false);
        return new ConstraintPresentation(id,name,ConstraintPresentationMode.REQUIRED_ACTIVE_TARGET,
                true,true,true,error,"length","maximum deviation",tolerance,"length",ratio,1d,
                ratio.status(),ratio.explanation());
    }
}
