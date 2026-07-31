package remframework.geometry.discovery;
import java.util.*;
public final class GeometryConfigurationRegistry {
    private final Map<GeometryConfigurationId,GeometryConfiguration> configurations=new LinkedHashMap<>();
    private final Map<GeometryConfigurationId,List<GeometryTestResult>> tests=new HashMap<>();
    public synchronized void registerAll(Collection<GeometryConfiguration> values){values.forEach(v->configurations.put(v.id(),v));}
    public synchronized Optional<GeometryConfiguration> find(GeometryConfigurationId id){return Optional.ofNullable(configurations.get(id));}
    public synchronized List<GeometryConfiguration> configurations(){return List.copyOf(configurations.values());}
    public synchronized void attach(GeometryTestResult result){if(!configurations.containsKey(result.configurationId()))throw new IllegalArgumentException("Unknown configuration ID.");tests.computeIfAbsent(result.configurationId(),ignored->new ArrayList<>()).add(result);}
    public synchronized List<GeometryTestResult> tests(GeometryConfigurationId id){return List.copyOf(tests.getOrDefault(id,List.of()));}
}
