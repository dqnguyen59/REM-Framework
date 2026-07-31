package remframework.geometry.discovery;

import java.util.ArrayList;
import java.util.List;

public final class DynamicElasticBoundedSearch {
    public List<DynamicElasticSearchRecord> run(DynamicElasticSearchSettings settings) {
        List<DynamicElasticSearchRecord> records = new ArrayList<>();
        FixedDegreeValidator validator = new FixedDegreeValidator();
        FixedDegreeGraphGenerator generator = new FixedDegreeGraphGenerator();
        for (int n = Math.max(1, settings.startingNodeCount());
             n <= settings.maximumNodeCount(); n++) {
            FixedDegreeValidation validation =
                    validator.validate(n, settings.exactNeighborsPerNode());
            if (!validation.valid()) {
                records.add(new DynamicElasticSearchRecord(n, -1,
                        settings.elasticSettings().randomSeed(), null,
                        String.join(" ", validation.messages())));
                continue;
            }
            boolean stableForN = false;
            int requestedAttempts = settings.exactNeighborsPerNode() == 2 ? 1
                    : settings.topologyAttemptsPerNodeCount();
            int uniqueAttempts = 0;
            java.util.Set<String> signatures = new java.util.HashSet<>();
            for (int attempt = 0; uniqueAttempts < requestedAttempts && !stableForN
                    && attempt < requestedAttempts * 100; attempt++) {
                FixedDegreeGraph graph;
                try {
                    graph = generator.generate(n, settings.exactNeighborsPerNode(),
                            settings.elasticSettings().randomSeed(), attempt);
                } catch (RuntimeException exception) {
                    records.add(new DynamicElasticSearchRecord(n, attempt,
                            settings.elasticSettings().randomSeed(), null,
                            exception.getMessage()));
                    continue;
                }
                if (!signatures.add(graph.topologySignature())) continue;
                uniqueAttempts++;
                for (int seedIndex = 0; seedIndex < settings.seedsPerTopology(); seedIndex++) {
                    long seed = settings.elasticSettings().randomSeed() + seedIndex;
                    DynamicElasticSettings elastic = withSeed(settings.elasticSettings(), seed);
                    DynamicElasticResult result =
                            new DynamicElasticSession(graph, elastic).runUntilFinished();
                    records.add(new DynamicElasticSearchRecord(n, attempt, seed, result, ""));
                    if (result.resultStatus()
                            == DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND) {
                        stableForN = settings.policy()
                                == DynamicElasticSearchPolicy.FIND_FIRST_STABLE_CANDIDATE_PER_NODE_COUNT;
                        if (settings.policy()
                                == DynamicElasticSearchPolicy.STOP_AT_FIRST_STABLE_CANDIDATE)
                            return List.copyOf(records);
                        if (stableForN) break;
                    }
                }
            }
            if (uniqueAttempts == 0)
                records.add(new DynamicElasticSearchRecord(n, -1,
                        settings.elasticSettings().randomSeed(), null,
                        "No valid unique fixed-degree topology was generated."));
        }
        return List.copyOf(records);
    }

    public List<Integer> validNodeCounts(DynamicElasticSearchSettings settings) {
        List<Integer> result = new ArrayList<>();
        FixedDegreeValidator validator = new FixedDegreeValidator();
        for (int n = settings.startingNodeCount(); n <= settings.maximumNodeCount(); n++)
            if (validator.validNodeCount(n, settings.exactNeighborsPerNode())) result.add(n);
        return result;
    }

    private DynamicElasticSettings withSeed(DynamicElasticSettings s, long seed) {
        return new DynamicElasticSettings(s.targetDistance(), s.requiredAccuracy(),
                s.springStrength(), s.updateStrength(), s.velocityRetention(),
                s.linearVelocityDamping(), s.angularVelocityDamping(), s.maximumVelocity(),
                s.maximumForce(), s.maximumEquilibriumSpeed(),
                s.requiredStableIterations(), s.maximumIterations(),
                s.workerUpdatesPerPublication(), seed);
    }
}
