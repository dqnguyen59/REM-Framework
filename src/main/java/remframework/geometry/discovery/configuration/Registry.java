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
import java.util.*;
public final class Registry {
    private final Map<ConfigurationId,Configuration> configurations=new LinkedHashMap<>();
    private final Map<ConfigurationId,List<TestResult>> tests=new HashMap<>();
    public synchronized void registerAll(Collection<Configuration> values){values.forEach(v->configurations.put(v.id(),v));}
    public synchronized Optional<Configuration> find(ConfigurationId id){return Optional.ofNullable(configurations.get(id));}
    public synchronized List<Configuration> configurations(){return List.copyOf(configurations.values());}
    public synchronized void attach(TestResult result){if(!configurations.containsKey(result.configurationId()))throw new IllegalArgumentException("Unknown configuration ID.");tests.computeIfAbsent(result.configurationId(),ignored->new ArrayList<>()).add(result);}
    public synchronized void clearTests(ConfigurationId id){tests.remove(id);}
    public synchronized List<TestResult> tests(ConfigurationId id){return List.copyOf(tests.getOrDefault(id,List.of()));}
}
