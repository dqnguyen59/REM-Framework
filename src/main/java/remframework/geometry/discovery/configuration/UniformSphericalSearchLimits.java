package remframework.geometry.discovery.configuration;

import java.time.Duration;

public record UniformSphericalSearchLimits(int maximumCompletedConfigurations,
        long maximumPartialStates,int maximumFrontierStates,Duration timeLimit) {
    public UniformSphericalSearchLimits {
        if(maximumCompletedConfigurations<1||maximumPartialStates<1||maximumFrontierStates<1
                ||timeLimit==null||timeLimit.isNegative()||timeLimit.isZero())
            throw new IllegalArgumentException("Specialized generation limits must be positive.");
    }
}
