package remframework.geometry.discovery.research.benchmark;

import remframework.geometry.discovery.configuration.Configuration;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;
import remframework.geometry.discovery.experiment.ExperimentProfile;
import remframework.geometry.discovery.experiment.FaceComposition;
import remframework.geometry.discovery.reference.ReferenceTopologyMatch;
import remframework.geometry.discovery.reference.ReferenceTopologyRecognizer;
import remframework.geometry.discovery.research.realization.ResearchAttemptResult;

/** One-way post-run evaluator. Solver and strategy-execution packages must never import this package. */
public final class PostRunBenchmarkEvaluator {
    public PostRunBenchmarkEvaluation evaluate(ResearchAttemptResult completed,Configuration topology,int faceSize){
        ReferenceTopologyMatch match=new ReferenceTopologyRecognizer().recognize(topology);
        var faces=new TopologyEligibilityAnalyzer().analyze(topology,
                ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,FaceComposition.uniform(faceSize)).faceComposition();
        double squared=completed.dihedralCoefficientOfVariation()*completed.dihedralCoefficientOfVariation()
                +completed.vertexRadiusCoefficientOfVariation()*completed.vertexRadiusCoefficientOfVariation()
                +completed.facePlaneRadiusCoefficientOfVariation()*completed.facePlaneRadiusCoefficientOfVariation();
        double similarity=Double.isFinite(squared)?1/(1+Math.sqrt(squared)):Double.NaN;
        return new PostRunBenchmarkEvaluation(completed.configurationId(),match.topology().name(),
                match.exactMatch(),faces.actualFaceCount(),faceSize,similarity,
                "Reference evaluation occurred after attempt "+completed.key()+" ended; it did not affect realization or ranking.");
    }
}
