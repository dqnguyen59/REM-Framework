package remframework.geometry.discovery;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.geometry.discovery.realization.sequence.*;

class ConstraintSequenceExperimentTest {
    @TempDir Path temporary;

    @Test void addRemoveMoveDuplicateAndEnablePreserveExplicitOrder(){
        ConstraintSequenceEditorModel editor=new ConstraintSequenceEditorModel(ConstraintSequencePresets.edgeFirst());
        List<String> original=ids(editor.sequence());editor.moveDown(0);assertEquals(original.get(0),ids(editor.sequence()).get(1));
        editor.moveUp(1);assertEquals(original,ids(editor.sequence()));int size=editor.sequence().stages().size();editor.duplicate(0);assertEquals(size+1,editor.sequence().stages().size());assertNotEquals(editor.sequence().stages().get(0).stageId(),editor.sequence().stages().get(1).stageId());editor.setEnabled(0,false);assertFalse(editor.sequence().stages().get(0).enabled());editor.remove(0);assertEquals(size,editor.sequence().stages().size());
    }

    @Test void persistenceRoundTripRetainsEveryStageParameterAndOrder()throws Exception{
        ConstraintSequence expected=ConstraintSequencePresets.orientationFirst();Path file=temporary.resolve("sequence.properties");new ConstraintSequenceStore().save(file,expected);assertEquals(expected,new ConstraintSequenceStore().load(file));
    }

    @Test void cumulativeAndStageOnlyActivationAreExact(){
        ConstraintSequence cumulative=ConstraintSequencePresets.edgeFirst();assertEquals(List.of(ConstraintId.EDGE_LENGTH,ConstraintId.FACE_ORIENTATION),ConstraintActivationPlan.at(cumulative,1).active());assertEquals(List.of(ConstraintId.EDGE_LENGTH),ConstraintActivationPlan.at(cumulative,1).retained());
        var stages=new ArrayList<>(cumulative.stages());var s=stages.get(1);stages.set(1,new ConstraintSequenceStage(s.stageId(),s.constraintId(),s.displayName(),true,s.mode(),ConstraintActivationBehavior.STAGE_ONLY,s.finalRestoringStrength(),s.rampMode(),s.rampIterations(),s.transitionTolerance(),s.stableIterations(),s.maximumIterations(),s.stagnationPolicy(),s.severeRegressionThreshold(),s.rollbackPolicy(),s.notes()));ConstraintActivationPlan.Snapshot plan=ConstraintActivationPlan.at(cumulative.withStages(stages),1);assertEquals(List.of(ConstraintId.FACE_ORIENTATION),plan.active());assertTrue(plan.retained().isEmpty());assertTrue(plan.experimentalStageOnly());
    }

    @Test void prerequisiteWarningsDoNotReorderAndOnlyUndefinedOrderBlocks(){
        ConstraintSequence questionable=ConstraintSequencePresets.orientationFirst();List<String> before=ids(questionable);List<ConstraintSequenceIssue> issues=new ConstraintSequenceValidator().validate(questionable);assertTrue(issues.stream().anyMatch(i->i.severity()==ConstraintSequenceIssue.Severity.WARNING));assertEquals(before,ids(questionable));new ConstraintSequenceValidator().requireExecutable(questionable);
        var stages=new ArrayList<>(questionable.stages());Collections.swap(stages,0,stages.size()-1);ConstraintSequence invalid=questionable.withStages(stages);assertTrue(new ConstraintSequenceValidator().validate(invalid).stream().anyMatch(i->i.severity()==ConstraintSequenceIssue.Severity.BLOCKING));assertThrows(IllegalArgumentException.class,()->new ConstraintSequenceValidator().requireExecutable(invalid));assertEquals(ConstraintId.FINAL_VALIDATION,invalid.stages().getFirst().constraintId());
    }

    @Test void customSettingsCannotFallBackToSpectral(){
        var settings=remframework.geometry.discovery.realization.session.ContinuationSettings.custom(ConstraintSequencePresets.edgeFirst());assertEquals(TopologyInitialization.RANDOM_SPHERICAL,settings.initialization());assertTrue(settings.constraintSequence()!=null);assertEquals(remframework.geometry.discovery.realization.session.SpectralPresentationMode.INSTANT,settings.spectralInitialization().mode());
        assertThrows(IllegalArgumentException.class,()->settings.withInitialization(TopologyInitialization.SPECTRAL_EMBEDDING));
    }
    private static List<String> ids(ConstraintSequence sequence){return sequence.stages().stream().map(ConstraintSequenceStage::stageId).toList();}
}
