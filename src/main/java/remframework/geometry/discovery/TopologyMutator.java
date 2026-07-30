package remframework.geometry.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

public final class TopologyMutator {
    private final SplittableRandom random;
    private final TopologyConstraintValidator validator = new TopologyConstraintValidator();

    public TopologyMutator(long seed) { random = new SplittableRandom(seed); }

    public TopologyMutationResult next(GeometryDiscoveryModel parent, TopologySearchSettings settings) {
        TopologyMutationType[] types = TopologyMutationType.values();
        return propose(parent, types[random.nextInt(types.length)], settings);
    }

    public TopologyMutationResult propose(GeometryDiscoveryModel parent, TopologyMutationType type,
                                          TopologySearchSettings settings) {
        List<DiscoveryConnection> edges = new ArrayList<>(parent.connections());
        List<Integer> ids = parent.sortedNodes().stream().map(DiscoveryNode::id).toList();
        TopologyMutation mutation;
        switch (type) {
            case ADD_EDGE -> {
                DiscoveryConnection edge = randomMissingEdge(ids, edges);
                if (edge == null) return rejected(parent, type, "No edge can be added.");
                mutation = new TopologyMutation(type, List.of(), List.of(edge), "add " + label(edge));
            }
            case REMOVE_EDGE -> {
                if (edges.isEmpty()) return rejected(parent, type, "No edge can be removed.");
                DiscoveryConnection edge = edges.get(random.nextInt(edges.size()));
                mutation = new TopologyMutation(type, List.of(edge), List.of(), "remove " + label(edge));
            }
            case REWIRE_EDGE -> {
                if (edges.isEmpty()) return rejected(parent, type, "No edge can be rewired.");
                DiscoveryConnection old = edges.get(random.nextInt(edges.size()));
                int retained = random.nextBoolean() ? old.firstNodeId() : old.secondNodeId();
                List<Integer> choices = ids.stream().filter(id -> id != retained
                        && !contains(edges, retained, id)).toList();
                if (choices.isEmpty()) return rejected(parent, type, "No valid rewire endpoint.");
                DiscoveryConnection replacement = new DiscoveryConnection(retained,
                        choices.get(random.nextInt(choices.size())));
                mutation = new TopologyMutation(type, List.of(old), List.of(replacement),
                        "rewire " + label(old) + " to " + label(replacement));
            }
            case SWAP_EDGE_ENDPOINTS -> {
                if (edges.size() < 2) return rejected(parent, type, "Two edges are required.");
                DiscoveryConnection first = edges.get(random.nextInt(edges.size()));
                DiscoveryConnection second = edges.get(random.nextInt(edges.size()));
                if (first.equals(second)) return rejected(parent, type, "The same edge was selected twice.");
                DiscoveryConnection a = new DiscoveryConnection(first.firstNodeId(), second.secondNodeId());
                DiscoveryConnection b = new DiscoveryConnection(second.firstNodeId(), first.secondNodeId());
                mutation = new TopologyMutation(type, List.of(first, second), List.of(a, b),
                        "swap endpoints of " + label(first) + " and " + label(second));
            }
            default -> throw new IllegalStateException("Unsupported mutation " + type);
        }
        return apply(parent, mutation, settings);
    }

    public TopologyMutationResult apply(GeometryDiscoveryModel parent, TopologyMutation mutation,
                                        TopologySearchSettings settings) {
        List<DiscoveryConnection> edges = new ArrayList<>(parent.connections());
        for (DiscoveryConnection removed : mutation.removedEdges())
            edges.removeIf(edge -> edge.undirectedKey() == removed.undirectedKey());
        edges.addAll(mutation.addedEdges());
        edges.sort(Comparator.comparingInt(DiscoveryConnection::lowerId)
                .thenComparingInt(DiscoveryConnection::upperId));
        GeometryDiscoveryModel changed = new GeometryDiscoveryModel(parent.name(), parent.description(),
                parent.nodes().stream().map(DiscoveryNode::copy).toList(), edges);
        DiscoveryValidationResult validation = validator.validate(changed, settings);
        if (!validation.isValid())
            return new TopologyMutationResult(parent, mutation, false,
                    validation.messages().stream().map(DiscoveryValidationMessage::message)
                            .reduce((a, b) -> a + "; " + b).orElse("Invalid mutation"));
        return new TopologyMutationResult(changed, mutation, true, "");
    }

    private DiscoveryConnection randomMissingEdge(List<Integer> ids, List<DiscoveryConnection> edges) {
        List<DiscoveryConnection> missing = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++)
            for (int j = i + 1; j < ids.size(); j++)
                if (!contains(edges, ids.get(i), ids.get(j)))
                    missing.add(new DiscoveryConnection(ids.get(i), ids.get(j)));
        return missing.isEmpty() ? null : missing.get(random.nextInt(missing.size()));
    }

    private static boolean contains(List<DiscoveryConnection> edges, int a, int b) {
        long key = new DiscoveryConnection(a, b).undirectedKey();
        return edges.stream().anyMatch(edge -> edge.undirectedKey() == key);
    }

    private static TopologyMutationResult rejected(GeometryDiscoveryModel parent,
                                                   TopologyMutationType type, String reason) {
        return new TopologyMutationResult(parent,
                new TopologyMutation(type, List.of(), List.of(), reason), false, reason);
    }

    private static String label(DiscoveryConnection edge) {
        return edge.lowerId() + "-" + edge.upperId();
    }
}
