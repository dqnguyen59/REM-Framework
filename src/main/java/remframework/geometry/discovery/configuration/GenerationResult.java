package remframework.geometry.discovery.configuration;
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

import java.util.List;

public record GenerationResult(String setId,
        ConfigurationRequest request, List<Configuration> configurations,
        Metrics metrics, GeometryConfigurationStatus status,GenerationDiagnostics generationDiagnostics) {
    public GenerationResult { configurations = List.copyOf(configurations); }
    public GenerationResult(String setId,ConfigurationRequest request,List<Configuration> configurations,
            Metrics metrics,GeometryConfigurationStatus status){this(setId,request,configurations,metrics,status,
            legacy(request,configurations,metrics,status));}
    private static GenerationDiagnostics legacy(ConfigurationRequest request,List<Configuration> configurations,Metrics metrics,GeometryConfigurationStatus status){GenerationStrategy strategy=request!=null&&request.exactDegree()==2?GenerationStrategy.DIRECT_CYCLE_CONSTRUCTION:GenerationStrategy.EXHAUSTIVE_BACKTRACKING;return new GenerationDiagnostics(strategy,"1",ConstructionMethod.PRIMAL_GRAPH,metrics.enumerationComplete()?GenerationCompleteness.COMPLETE_WITHIN_CONFIGURED_SEARCH_LIMITS:GenerationCompleteness.INCOMPLETE_SEARCH_LIMIT_REACHED,metrics.configurationsExamined(),0,metrics.configurationsExamined(),metrics.isomorphicDuplicatesRemoved(),configurations.size(),java.util.Map.of(),null,status.explanation());}
}
