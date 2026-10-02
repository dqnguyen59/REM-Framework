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

import java.util.ArrayList;
import java.util.List;

public final class DiscoveryValidationResult {
    private final List<DiscoveryValidationMessage> messages = new ArrayList<>();

    public void error(String code, String message) {
        messages.add(new DiscoveryValidationMessage(
                DiscoveryValidationMessage.Severity.ERROR, code, message));
    }

    public void warning(String code, String message) {
        messages.add(new DiscoveryValidationMessage(
                DiscoveryValidationMessage.Severity.WARNING, code, message));
    }

    public boolean isValid() {
        return messages.stream().noneMatch(
                message -> message.severity() == DiscoveryValidationMessage.Severity.ERROR);
    }

    public List<DiscoveryValidationMessage> messages() { return List.copyOf(messages); }
}
