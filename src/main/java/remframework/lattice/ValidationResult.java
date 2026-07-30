package remframework.lattice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationResult {
    private final List<ValidationMessage> messages = new ArrayList<>();

    public void error(String message) {
        messages.add(new ValidationMessage(ValidationMessage.Severity.ERROR, message));
    }

    public void warning(String message) {
        messages.add(new ValidationMessage(ValidationMessage.Severity.WARNING, message));
    }

    public boolean isValid() {
        return messages.stream().noneMatch(m -> m.severity() == ValidationMessage.Severity.ERROR);
    }

    public List<ValidationMessage> messages() {
        return Collections.unmodifiableList(messages);
    }
}
