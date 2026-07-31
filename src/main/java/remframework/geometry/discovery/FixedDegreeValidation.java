package remframework.geometry.discovery;

import java.util.List;

public record FixedDegreeValidation(boolean valid, List<String> messages,
                                    Integer nearestLowerValid,
                                    Integer nearestHigherValid) {
    public FixedDegreeValidation {
        messages = List.copyOf(messages);
    }
}
