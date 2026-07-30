package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Deterministically constructs the finite initial graph and coordinates for a
 * discovery experiment. It does not perform relaxation or topology search.
 */
public final class DiscoveryExperimentGenerator {
    private final DiscoveryExperimentValidator validator = new DiscoveryExperimentValidator();

    public GeometryDiscoveryModel generate(DiscoveryExperimentConfiguration configuration) {
        DiscoveryExperimentValidation validation = validator.validate(configuration);
        List<String> errors = new ArrayList<>(validation.errors());
        validateTopologySpecific(configuration, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        if (configuration.initialTopology() == DiscoveryInitialTopology.LOAD_CANDIDATE
                || configuration.initialTopology() == DiscoveryInitialTopology.LOAD_TOML) {
            try {
                return new GeometryDiscoveryCandidateLoader()
                        .load(configuration.sourceCandidate()).model().copy();
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Could not load initial candidate: " + exception.getMessage(), exception);
            }
        }
        List<DiscoveryConnection> edges = generateEdges(configuration);
        List<DiscoveryNode> nodes = generateNodes(configuration);
        return new GeometryDiscoveryModel(configuration.name(), configuration.description(),
                nodes, edges);
    }

    private void validateTopologySpecific(DiscoveryExperimentConfiguration c,
                                          List<String> errors) {
        int n = c.nodeCount();
        switch (c.initialTopology()) {
            case LINE -> {
                if (c.minimumDegree() > 1 || c.maximumDegree() < 2)
                    errors.add("Line topology requires degree bounds that include 1 and 2.");
            }
            case RING -> {
                if (c.minimumDegree() > 2 || c.maximumDegree() < 2)
                    errors.add("Ring topology requires degree 2.");
            }
            case RANDOM_TREE -> {
                if (c.minimumDegree() > 1)
                    errors.add("A finite tree necessarily has leaf nodes of degree 1.");
                if (c.maximumDegree() < 2 && n > 2)
                    errors.add("A connected tree with more than two nodes needs maximum degree 2.");
            }
            case COMPLETE_GRAPH -> {
                if (c.minimumDegree() > n - 1 || c.maximumDegree() < n - 1)
                    errors.add("Complete graph requires degree " + (n - 1) + ".");
            }
            case CUBIC_GRID -> {
                if (c.minimumDegree() > 3)
                    errors.add("A finite cubic grid has corner nodes with degree at most 3.");
            }
            default -> { }
        }
    }

    private List<DiscoveryConnection> generateEdges(DiscoveryExperimentConfiguration c) {
        return switch (c.initialTopology()) {
            case LINE -> line(c.nodeCount());
            case RING -> ring(c.nodeCount());
            case CUBIC_GRID -> cubicGrid(c.nodeCount(), c.embeddingDimension());
            case RANDOM_TREE -> randomTree(c.nodeCount(), c.maximumDegree(), c.randomSeed());
            case RANDOM_CONNECTED_GRAPH -> randomConnected(c);
            case COMPLETE_GRAPH -> complete(c.nodeCount());
            default -> throw new IllegalStateException("File topology handled before generation.");
        };
    }

    private List<DiscoveryNode> generateNodes(DiscoveryExperimentConfiguration c) {
        SplittableRandom random = new SplittableRandom(c.randomSeed() ^ 0x6A09E667F3BCC909L);
        List<DiscoveryNode> nodes = new ArrayList<>();
        for (int id = 0; id < c.nodeCount(); id++) {
            Vector3d position = switch (c.geometryInitialization()) {
                case RANDOM_SPHERE -> sphere(random, c.initialExtent(), c.embeddingDimension());
                case RANDOM_CUBE -> cube(random, c.initialExtent(), c.embeddingDimension());
                case CUBIC_GRID -> gridPosition(id, c.nodeCount(), c.initialExtent(),
                        c.embeddingDimension());
                case PLANAR_CIRCLE -> circle(id, c.nodeCount(), c.initialExtent());
                case EXISTING_COORDINATES -> gridPosition(id, c.nodeCount(), c.initialExtent(),
                        c.embeddingDimension());
            };
            if (c.perturbationMagnitude() > 0 && id != 0) {
                position.add((random.nextDouble() * 2 - 1) * c.perturbationMagnitude(),
                        (random.nextDouble() * 2 - 1) * c.perturbationMagnitude(),
                        c.embeddingDimension() == 2 ? 0
                                : (random.nextDouble() * 2 - 1) * c.perturbationMagnitude());
            }
            if (c.embeddingDimension() == 2) position.z = 0;
            if (id == 0) position.zero();
            nodes.add(new DiscoveryNode(id, position, id == 0));
        }
        return nodes;
    }

    private List<DiscoveryConnection> randomConnected(DiscoveryExperimentConfiguration c) {
        int n = c.nodeCount();
        int desired = c.neighborCountPolicy()
                == DiscoveryNeighborCountPolicy.UNRESTRICTED_WITHIN_BOUNDS
                ? Math.max(2, c.minimumDegree())
                : Math.max(c.minimumDegree(),
                Math.min(c.maximumDegree(), (int) Math.round(c.targetInitialDegree())));
        List<Integer> order = permutation(n, c.randomSeed());
        Set<Long> keys = new HashSet<>();
        List<DiscoveryConnection> edges = new ArrayList<>();
        // A relabelled circulant graph gives a deterministic connected degree floor.
        for (int offset = 1; offset <= desired / 2; offset++)
            for (int i = 0; i < n; i++)
                add(edges, keys, order.get(i), order.get((i + offset) % n));
        if ((desired & 1) == 1) {
            if ((n & 1) == 1)
                throw new IllegalArgumentException(
                        "An odd regular target degree requires an even node count.");
            for (int i = 0; i < n / 2; i++)
                add(edges, keys, order.get(i), order.get(i + n / 2));
        }
        int[] degree = degrees(n, edges);
        SplittableRandom random = new SplittableRandom(c.randomSeed());
        List<int[]> pairs = new ArrayList<>();
        for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++)
            if (!keys.contains(key(a, b))) pairs.add(new int[]{a, b});
        for (int i = pairs.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] value = pairs.get(i); pairs.set(i, pairs.get(j)); pairs.set(j, value);
        }
        for (int[] pair : pairs) {
            if (c.neighborCountPolicy() == DiscoveryNeighborCountPolicy.UNIFORM) break;
            if (degree[pair[0]] >= c.maximumDegree()
                    || degree[pair[1]] >= c.maximumDegree()) continue;
            if (random.nextDouble() <= c.edgeProbability()) {
                add(edges, keys, pair[0], pair[1]);
                degree[pair[0]]++; degree[pair[1]]++;
            }
        }
        return edges;
    }

    private List<DiscoveryConnection> randomTree(int n, int maximumDegree, long seed) {
        List<Integer> order = permutation(n, seed);
        int[] degree = new int[n];
        List<DiscoveryConnection> edges = new ArrayList<>();
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = 1; i < n; i++) {
            List<Integer> parents = new ArrayList<>();
            for (int j = 0; j < i; j++)
                if (degree[order.get(j)] < maximumDegree) parents.add(order.get(j));
            if (parents.isEmpty())
                throw new IllegalArgumentException("Maximum degree cannot support the random tree.");
            int child = order.get(i);
            int parent = parents.get(random.nextInt(parents.size()));
            edges.add(new DiscoveryConnection(Math.min(parent, child), Math.max(parent, child)));
            degree[parent]++; degree[child]++;
        }
        return edges;
    }

    private List<Integer> permutation(int n, long seed) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int value = order.get(i); order.set(i, order.get(j)); order.set(j, value);
        }
        return order;
    }

    private List<DiscoveryConnection> line(int n) {
        List<DiscoveryConnection> edges = new ArrayList<>();
        for (int i = 1; i < n; i++) edges.add(new DiscoveryConnection(i - 1, i));
        return edges;
    }
    private List<DiscoveryConnection> ring(int n) {
        List<DiscoveryConnection> edges = line(n);
        edges.add(new DiscoveryConnection(0, n - 1));
        return edges;
    }
    private List<DiscoveryConnection> complete(int n) {
        List<DiscoveryConnection> edges = new ArrayList<>();
        for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++)
            edges.add(new DiscoveryConnection(a, b));
        return edges;
    }
    private List<DiscoveryConnection> cubicGrid(int n, int dimension) {
        int width = (int) Math.ceil(Math.pow(n, 1.0 / dimension));
        List<DiscoveryConnection> edges = new ArrayList<>();
        for (int id = 0; id < n; id++) {
            int x = id % width;
            int y = id / width % width;
            if (x + 1 < width && id + 1 < n) edges.add(new DiscoveryConnection(id, id + 1));
            if (y + 1 < width && id + width < n)
                edges.add(new DiscoveryConnection(id, id + width));
            if (dimension == 3 && id + width * width < n)
                edges.add(new DiscoveryConnection(id, id + width * width));
        }
        return edges;
    }
    private Vector3d sphere(SplittableRandom random, double radius, int dimension) {
        double angle = random.nextDouble() * Math.PI * 2;
        if (dimension == 2) return new Vector3d(
                Math.cos(angle) * radius, Math.sin(angle) * radius, 0);
        double z = random.nextDouble(-1, 1);
        double planar = Math.sqrt(Math.max(0, 1 - z * z));
        return new Vector3d(planar * Math.cos(angle), planar * Math.sin(angle), z).mul(radius);
    }
    private Vector3d cube(SplittableRandom random, double extent, int dimension) {
        return new Vector3d(random.nextDouble(-extent, extent),
                random.nextDouble(-extent, extent),
                dimension == 2 ? 0 : random.nextDouble(-extent, extent));
    }
    private Vector3d circle(int id, int n, double radius) {
        double angle = Math.PI * 2 * id / n;
        return new Vector3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0);
    }
    private Vector3d gridPosition(int id, int n, double extent, int dimension) {
        int width = (int) Math.ceil(Math.pow(n, 1.0 / dimension));
        double spacing = width <= 1 ? extent : extent * 2 / (width - 1);
        int x = id % width;
        int y = id / width % width;
        int z = dimension == 2 ? 0 : id / (width * width);
        return new Vector3d(-extent + x * spacing, -extent + y * spacing,
                dimension == 2 ? 0 : -extent + z * spacing);
    }
    private void add(List<DiscoveryConnection> edges, Set<Long> keys, int a, int b) {
        if (a == b || !keys.add(key(a, b))) return;
        edges.add(new DiscoveryConnection(Math.min(a, b), Math.max(a, b)));
    }
    private long key(int a, int b) {
        return ((long) Math.min(a, b) << 32) ^ (Math.max(a, b) & 0xffffffffL);
    }
    private int[] degrees(int n, List<DiscoveryConnection> edges) {
        int[] degree = new int[n];
        for (DiscoveryConnection edge : edges) {
            degree[edge.firstNodeId()]++; degree[edge.secondNodeId()]++;
        }
        return degree;
    }
}
