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

import java.util.Locale;

public final class GeometryDiagnosticReport {
    public String format(TopologyCandidate candidate, TopologyScoringProfile profile) {
        DirectionalBalanceMetrics d = candidate.directionalMetrics();
        StringBuilder out = new StringBuilder();
        out.append("REM Framework Geometry Discovery diagnostic report\n");
        out.append("model=").append(candidate.model().name()).append('\n');
        out.append("scoringProfile=").append(profile).append('\n');
        out.append("totalScore=").append(number(candidate.score().total())).append('\n');
        out.append("globalDimensionality=").append(d.globalDimensionality()).append('\n');
        out.append("meanNormalizedDirectionalBias=")
                .append(number(d.meanNormalizedDirectionalBias())).append('\n');
        out.append("meanTensorIsotropyError=")
                .append(number(d.meanTensorIsotropyError())).append('\n');
        out.append("knownReferenceAngularError=")
                .append(number(d.meanKnownReferenceAngularError()))
                .append(" applicableNodes=").append(d.angularReferenceApplicableNodeCount()).append('\n');
        out.append("meanNearestDirectionAngle=")
                .append(number(d.meanNearestDirectionAngle()))
                .append(" maximumUncoveredAngle=").append(number(d.maximumUncoveredAngle()))
                .append(" approximate=true\n");
        out.append("meanOppositeDirectionMismatch=")
                .append(number(d.meanOppositeDirectionMismatch()))
                .append(" missingDirections=").append(d.missingOppositeDirectionCount()).append('\n');
        out.append("dimensionDeficientNodes=").append(d.dimensionDeficientNodeCount())
                .append(" dimensionMismatchNodes=").append(d.localGlobalDimensionMismatchCount())
                .append('\n');
        out.append("\nscoreExplanation:\n");
        out.append("metric,applicable,raw,normalized,weight,contribution,percent\n");
        double total = candidate.score().total();
        for (TopologyScoreComponent component : candidate.score().details().values()) {
            double percent = total == 0 ? 0 : component.weightedContribution() / total * 100;
            out.append(component.name()).append(',').append(component.applicable()).append(',')
                    .append(number(component.rawValue())).append(',')
                    .append(number(component.normalizedValue())).append(',')
                    .append(number(component.weight())).append(',')
                    .append(number(component.weightedContribution())).append(',')
                    .append(number(percent)).append('\n');
        }
        out.append("\nnodeDiagnostics:\n");
        for (LocalDirectionalDiagnostics local : d.localDiagnostics().values())
            out.append("node=").append(local.nodeId()).append(" degree=").append(local.degree())
                    .append(" dimension=").append(local.localDimensionality())
                    .append(" eigenvalues=").append(local.eigenvalues())
                    .append(" bias=").append(number(local.normalizedDirectionalBias()))
                    .append(" tensor=").append(number(local.tensorIsotropyError()))
                    .append(" angularReference=").append(local.angularReferenceType())
                    .append(" angularApplicable=").append(local.angularReferenceApplicable())
                    .append(" angularError=").append(number(local.knownReferenceAngularError()))
                    .append(" oppositeMismatch=")
                    .append(number(local.meanOppositeDirectionMismatch())).append('\n');
        return out.toString();
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.10g", value) : "unavailable";
    }
}
