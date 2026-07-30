package remframework.geometry.discovery;

import java.util.Locale;

public enum TopologyScoringProfile {
    GENERAL_GEOMETRY,
    WAVE_PROPAGATION,
    CENTROSYMMETRIC,
    TETRAHEDRAL,
    CUBIC_RUNTIME;

    public static TopologyScoringProfile parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "general", "general-geometry" -> GENERAL_GEOMETRY;
            case "wave", "wave-propagation" -> WAVE_PROPAGATION;
            case "centrosymmetric" -> CENTROSYMMETRIC;
            case "tetrahedral" -> TETRAHEDRAL;
            case "cubic", "cubic-runtime" -> CUBIC_RUNTIME;
            default -> throw new IllegalArgumentException("Unknown scoring profile: " + value);
        };
    }
}
