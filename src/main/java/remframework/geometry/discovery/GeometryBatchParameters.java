package remframework.geometry.discovery;

import java.nio.file.Path;
import java.util.List;

public record GeometryBatchParameters(String modelName, long firstSeed, long lastSeed,
                                      double targetDistance, List<Double> learningRates,
                                      List<Double> repulsionStrengths,
                                      List<Double> maximumCorrections,
                                      int maximumIterations,
                                      double convergenceTolerance, Path outputDirectory,
                                      boolean exportCandidates) {
}
