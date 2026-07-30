package remframework.geometry.discovery;

import org.joml.Vector3i;

import java.util.Map;

public record RuntimeCompatibility(boolean compatible, String reason,
                                   Vector3i dimensions, Map<Integer, Vector3i> indices) {
}
