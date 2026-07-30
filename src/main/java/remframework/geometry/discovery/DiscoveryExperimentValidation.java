package remframework.geometry.discovery;

import java.util.List;

public record DiscoveryExperimentValidation(List<String> errors) {
    public DiscoveryExperimentValidation {
        errors = List.copyOf(errors);
    }
    public boolean valid() { return errors.isEmpty(); }
}
