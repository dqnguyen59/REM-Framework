package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.geometry.discovery.realization.session.TestRequest;
import remframework.geometry.discovery.research.realization.*;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RealizationStrategyResearchTest {
    @TempDir Path temporary;

    @Test void searchSpaceIsDeterministicAndExplicitAboutUnavailableAlgorithms(){
        ResearchSearchManifest a=GeneralStrategySearchSpace.initial(77),b=GeneralStrategySearchSpace.initial(77);
        assertEquals(a,b);assertEquals("SUCCESSIVE_HALVING",a.searchMethod());
        assertTrue(a.unavailableAlternatives().stream().anyMatch(x->x.contains("adaptive")));
        assertTrue(a.strategies().stream().allMatch(s->s.experimentalStatus().equals("EXPERIMENTAL_STRATEGY_CANDIDATE")));
    }

    @Test void oneFrozenSpecificationHasNoBenchmarkIdentityAndCanBeReusedUnchanged(){
        RealizationStrategySpecification frozen=GeneralStrategySearchSpace.initial(1).strategies().get(1);
        assertSame(frozen,frozen);assertFalse(frozen.toString().toLowerCase().matches(".*(tetra|cube|dodeca|golden).*"));
        assertEquals(frozen,GeneralStrategySearchSpace.initial(999).strategies().get(1));
    }

    @Test void referenceDataCannotEnterTestRequest(){
        for(var component:TestRequest.class.getRecordComponents()){
            String type=component.getGenericType().getTypeName();
            assertFalse(type.contains(".reference."));assertFalse(type.contains("research.benchmark"));
        }
    }

    @Test void solverSideResearchPackageCannotDependOnBenchmarkEvaluator()throws Exception{
        Path root=Path.of("src/main/java/remframework/geometry/discovery/research/realization");
        try(var files=Files.walk(root)){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
            String source=Files.readString(file);if(source.startsWith("package remframework.geometry.discovery.research.realization;"))
                assertFalse(source.contains("research.benchmark"),file.toString());}}
    }

    @Test void rankingAlwaysPlacesQualifiedStrategyBeforeCloserButInvalidCandidate(){
        ResearchAttemptResult qualified=result("valid",true,20,9);
        ResearchAttemptResult visuallyCloserButInvalid=result("invalid",false,.01,0);
        assertEquals(List.of("valid","invalid"),StrategyRanking.rank(List.of(visuallyCloserButInvalid,qualified)));
    }

    @Test void appendOnlyStoreResumesByExactAttemptKey()throws Exception{
        ResearchStore store=new ResearchStore(temporary);ResearchAttemptResult attempt=result("frozen",false,3,2);
        store.append(attempt);ResearchStore reopened=new ResearchStore(temporary);
        assertEquals(List.of(attempt),reopened.readAttempts());assertEquals(java.util.Set.of(attempt.key()),reopened.completedKeys());
    }

    private static ResearchAttemptResult result(String strategy,boolean qualified,double ratio,int violations){return new ResearchAttemptResult(strategy,"cfg-test",42,500,100,qualified,false,ratio,0,violations,ratio,.1,.01,.2,.01,.3,.01,10,"stage","","PROGRESSING",qualified?"VALID_CANDIDATE":"NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS");}
}
