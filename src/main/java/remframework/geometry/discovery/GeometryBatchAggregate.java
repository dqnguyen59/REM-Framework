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

public record GeometryBatchAggregate(int totalRuns, int convergedRuns, double convergenceRate,
                                     double meanIterationCount, int bestRunId, int worstRunId,
                                     int fastestConvergedRunId,
                                     String highestConvergenceParameterCombination) {
}
