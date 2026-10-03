# Face-sharing tetrahedral Geometry Discovery experiment

This is independent REM Framework research, with no change to the Fundamental Node Equation, existing configuration enumeration, or scientific assumptions. Node IDs 0–3 are ABCD. Faces are unordered triples; tetrahedra and their incidences are explicitly retained. No coordinates, lengths, volumes, geometric boundary qualification, or physical isotropy are assigned.

## Implemented step

FaceSharingTetrahedralExperiment.generate(generation, maximumNodes) starts with K4 and attaches one fresh node to each face exposed at the start of a generation. New faces wait until the next generation. Each attachment consumes one exposed face and introduces three exposed faces. Node IDs and iteration order are deterministic; ordering does not select which faces grow in the naive construction.

Snapshots retain all edges, node birth generations, tetrahedra with birth generations, and face incidence (1 exposed, 2 shared). Adjacency reuses Configuration.adjacency and edges reuse DiscoveryConnection. Configuration itself requires exact degree K and therefore cannot represent this variable-degree construction. No registry or enumeration strategy is replaced.

Invalid requests return INVALID_PARAMETERS. If the next whole generation would exceed maximumNodes, RESOURCE_LIMIT_REACHED retains all completed snapshots, states the required node count, and stops. maximumNodes is a node budget, not a guarantee against all possible JVM memory exhaustion; history retains repeated topology and consumes additional memory. Use modest declared budgets. No generation is partially emitted.

The command-line entry point accepts generation and maximumNodes (defaults 5 and 10000), prints generation metrics and terminal ConfigurationStatus. The Java API also exposes every node degree and all topology.

## Verified equations and their meaning

At generation n, F_n = 4*3^n because each exposed face is replaced by three exposed faces.
T_n = 2*3^n - 1 and V_n = T_n + 3 = 2*3^n + 2.
E_n = 6 + 3*(V_n-4) = 6*3^n.
The connected graph's independent cycle rank is E_n-V_n+1 = 4*3^n-3.
This is not a count of all simple cycles. Each attachment adds two independent graph cycles, recorded as attachmentReconnections; the seed already has cycle rank 3.

An original vertex initially belongs to three exposed faces. Each exposed face containing that vertex produces two exposed child faces containing it. Its frontier incidence doubles, and each attachment adds one neighbor. Thus its degree is 3*2^n. A vertex born at generation b has degree 3*2^(n-b). Tests compute degrees from adjacency instead of using these formulas to generate topology.

Cycles connect shared existing vertices through a fresh node. This construction never identifies distinct existing vertices and never glues two existing exposed faces together. It does not establish geometric closure or 3D realizability. The tetrahedral adjacency graph is a tree; the shared-node graph has cycles and increasing degrees.

## Prepared bounded-degree research — choices remain open

DegreeCapacity(K) is an opt-in capacity check for a proposed fresh attachment. It reports INVALID_DEGREE and affected nodes if a face vertex is already at K. It is not a generation policy, and maximum degree K does not mean every node has exact degree K.

Before implementing a bounded run, explicitly select and record:
1. K, seed, generation/node/attempt budgets, and whether exact degree is a terminal requirement.
2. A named candidate rule: sequential admissible attachments, a synchronous non-conflicting batch, exhaustive alternatives, or an explicitly specified closure/reuse operation. These are distinct experiments. Fresh-node-only growth cannot perform identification or face gluing.
3. Tie handling: label order is deterministic but may break symmetry; orbit-wide attachment may exceed aggregate capacities. A synchronous rule must check the total increment at each shared vertex, not merely check each face independently against the same snapshot.
4. Closure semantics: a rule reusing vertices requires separate duplicate-edge, tetrahedron, face-incidence and manifold checks. Do not silently substitute it for the one-fresh-node rule.

For each candidate preserve its proposal, accepted attachments, rejected attachments with ConfigurationStatus, terminal topology, provenance and stop reason. Reuse AutomorphismAnalyzer and existing graph symmetry classification only after checking their fixed-degree assumptions; do not construct Configuration for a nonregular graph. Compare degree distribution, exact-K completion, vertex/edge orbit structure, cycle rank and tetrahedral dual cycles. Symmetry and graph isotropy require explicit definitions and tests; neither follows from a symmetric seed. Keep 3D realizability in the later geometry-test stage.

Required next tests include competing faces sharing a saturated vertex, invariance under relabeling for any claimed symmetric rule, all rejected candidates retained, no forbidden degree increment, duplicate/closure rejection, and finite exhaustion with honest status. No bounded candidate rule has been selected or executed.
