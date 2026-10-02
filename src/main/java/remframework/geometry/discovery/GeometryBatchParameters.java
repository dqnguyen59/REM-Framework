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
