package remframework.geometry.discovery;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
/** UI-facing model for the two explicit research stages. Selection never starts geometry. */
public final class GeometryDiscoveryPhasedWorkflow {
    public static final String TOPOLOGY_PREVIEW_LABEL="Graph visualization only - not a geometric realization.";
    private final GeometryConfigurationGenerator generator=new GeometryConfigurationGenerator();
    private final GeometryConfigurationRegistry registry=new GeometryConfigurationRegistry();
    private GeometryConfigurationGenerationResult generation;
    private GeometryConfigurationId selected;
    public synchronized GeometryConfigurationGenerationResult generateConfigurations(GeometryConfigurationRequest request)throws IOException{generation=generator.generate(request);registry.registerAll(generation.configurations());if(request.outputDirectory()!=null)new GeometryConfigurationStore(request.outputDirectory()).save(generation);return generation;}
    public synchronized void reopen(Path setDirectory)throws IOException{List<GeometryConfiguration> values=new GeometryConfigurationStore(setDirectory.getParent()).reopen(setDirectory);registry.registerAll(values);selected=null;}
    public synchronized void selectForGeometryTest(GeometryConfigurationId id){if(registry.find(id).isEmpty())throw new IllegalArgumentException("Unknown configuration ID.");selected=id;}
    public synchronized Optional<GeometryConfigurationId> selectedConfiguration(){return Optional.ofNullable(selected);}
    public synchronized GeometryTestResult runSelectedGeometryTest(GeometryTestRequest request){if(selected==null||!selected.equals(request.configurationId()))throw new IllegalStateException("The user must explicitly select this configuration before geometry testing.");GeometryTestResult result=new GeometryTestSession(registry.find(selected).orElseThrow(),request).run();registry.attach(result);return result;}
    public synchronized List<GeometryConfigurationTableRow> tableRows(){return registry.configurations().stream().map(c->{List<GeometryTestResult> tests=registry.tests(c.id());GeometryTestResult last=tests.isEmpty()?null:tests.get(tests.size()-1);return new GeometryConfigurationTableRow(c.id(),c.nodeCount(),c.exactDegree(),c.edges().size(),c.graphSymmetry(),c.automorphismGroupSize(),c.status().code(),last!=null,last==null?GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED:last.status().code(),c.status().summary());}).toList();}
    public synchronized List<GeometryTestResult> geometryTests(GeometryConfigurationId id){return registry.tests(id);}
    public synchronized GeometryConfigurationRegistry registry(){return registry;}
}
