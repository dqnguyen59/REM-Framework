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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Consumer;

public final class TopologySearchSession {
    private static final Comparator<TopologyCandidate> CANDIDATE_ORDER =
            Comparator.comparingDouble((TopologyCandidate c) -> c.score().total())
                    .thenComparing(TopologyCandidate::topologySignature)
                    .thenComparingLong(TopologyCandidate::candidateId);
    private final TopologySearchSettings settings;
    private final TopologyMutator mutator;
    private final TopologyEvaluator evaluator = new TopologyEvaluator();
    private final SplittableRandom acceptanceRandom;
    private final Set<String> evaluated = new HashSet<>();
    private final List<TopologyCandidate> archive = new ArrayList<>();
    private final List<TopologySearchHistoryEntry> history = new ArrayList<>();
    private final long started = System.nanoTime();
    private TopologyCandidate initial;
    private TopologyCandidate current;
    private TopologyCandidate best;
    private long nextCandidateId = 1;
    private int iteration;
    private int accepted;
    private int rejected;
    private int invalid;
    private int duplicate;
    private int relaxationFailures;
    private double temperature;

    public TopologySearchSession(GeometryDiscoveryModel initialModel, TopologySearchSettings settings) {
        this.settings = settings;
        new TopologyConstraintValidator().validate(initialModel, settings).messages().stream()
                .filter(m -> m.severity() == DiscoveryValidationMessage.Severity.ERROR)
                .findFirst().ifPresent(m -> { throw new IllegalArgumentException(m.message()); });
        mutator = new TopologyMutator(settings.randomSeed());
        acceptanceRandom = new SplittableRandom(settings.randomSeed() ^ 0x5DEECE66DL);
        temperature = settings.initialTemperature();
        initial = evaluator.evaluate(0, null, initialModel, null, settings);
        current = initial;
        best = initial;
        evaluated.add(initial.topologySignature());
        addToArchive(initial);
    }

    public TopologySearchResult run() { return run(null); }

    public TopologySearchResult run(Consumer<TopologySearchSession> progress) {
        while (iteration < settings.maximumSearchIterations()) {
            step();
            if (progress != null && (iteration == 1 || iteration % settings.checkpointInterval() == 0
                    || iteration == settings.maximumSearchIterations())) progress.accept(this);
        }
        return result();
    }

    public void step() {
        if (iteration >= settings.maximumSearchIterations()) return;
        iteration++;
        boolean acceptedThisIteration = false;
        String mutationName = "";
        for (int attempt = 0; attempt < settings.mutationsAttemptedPerIteration(); attempt++) {
            TopologyMutationResult proposal = mutator.next(current.model(), settings);
            mutationName = proposal.mutation().type().name();
            if (!proposal.valid()) { invalid++; continue; }
            String signature = TopologySignature.of(proposal.model());
            if (!evaluated.add(signature)) { duplicate++; continue; }
            TopologyCandidate candidate = evaluator.evaluate(nextCandidateId++, current,
                    proposal.model(), proposal.mutation(), settings);
            if (!candidate.geometryResult().metrics().allFinite()) {
                relaxationFailures++;
                rejected++;
                continue;
            }
            addToArchive(candidate);
            if (accept(candidate.score().total(), current.score().total())) {
                current = candidate;
                accepted++;
                acceptedThisIteration = true;
                if (CANDIDATE_ORDER.compare(candidate, best) < 0) best = candidate;
                break;
            }
            rejected++;
        }
        if (settings.searchMode() == TopologySearchMode.SIMULATED_ANNEALING)
            temperature = Math.max(settings.minimumTemperature(), temperature * settings.coolingRate());
        history.add(new TopologySearchHistoryEntry(iteration, current.score().total(),
                best.score().total(), acceptedThisIteration, mutationName, temperature, elapsed(),
                current.score().details()));
    }

    public boolean accept(double candidateScore, double currentScore) {
        double difference = candidateScore - currentScore;
        if (difference < -settings.scoreImprovementTolerance()) return true;
        if (settings.searchMode() == TopologySearchMode.HILL_CLIMBING) return false;
        if (difference <= settings.scoreImprovementTolerance()) return true;
        return acceptanceRandom.nextDouble() < Math.exp(-difference / Math.max(temperature, 1e-15));
    }

    private void addToArchive(TopologyCandidate candidate) {
        archive.add(candidate);
        archive.sort(CANDIDATE_ORDER);
        if (archive.size() > settings.archiveSize()) archive.remove(archive.size() - 1);
    }

    private long elapsed() { return (System.nanoTime() - started) / 1_000_000; }

    public TopologySearchResult result() {
        return new TopologySearchResult(settings, initial, current, best, List.copyOf(archive),
                List.copyOf(history), new TopologySearchMetrics(iteration, accepted, rejected,
                invalid, duplicate, relaxationFailures, elapsed()));
    }

    public int iteration() { return iteration; }
    public double temperature() { return temperature; }
    public TopologyCandidate currentCandidate() { return current; }
    public TopologyCandidate bestCandidate() { return best; }
    public TopologySearchMetrics metrics() { return result().metrics(); }
}
