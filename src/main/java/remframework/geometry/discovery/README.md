# Geometry Discovery Engine

The Geometry Discovery Engine is an independent REM Framework experiment for
generating and relaxing candidate finite graph geometries. It does not execute
inside the REM simulation loop and does not use or modify REM node mechanics,
occupancy, transport, rendering, or the active runtime lattice.

Version 0.1 optimizes coordinates for a supplied topology. Connected pairs use
a squared edge-length objective, while unconnected pairs receive bounded
short-range repulsion. Node 0 is anchored at the origin and node 1 is constrained
to the positive x-axis to remove rigid translation and rotation freedom.

Built-in examples are `line`, `square`, `tetrahedron`, and `cube`. Run one with:

```shell
mvn package
java -cp target/rem.jar remframework.geometry.discovery.GeometryDiscoveryMain \
  --model tetrahedron --target 10 --iterations 20000 --seed 1 \
  --output lattice/candidates/tetrahedron.toml
```

Candidate files use format 1.0 with `model.type = "explicit"`, reciprocal
neighbor lists, convergence metrics, generator metadata, and a runtime
compatibility assessment. Outputs belong under `lattice/candidates`; the engine
refuses to overwrite `lattice/cubic-lattice.toml`.

Current limitations:

- topology is supplied rather than discovered;
- gradient descent is deterministic but not a global optimizer;
- collision repulsion is pairwise and scales quadratically with node count;
- effective dimensionality is only an extent-based diagnostic;
- most candidate graphs are intentionally incompatible with the current
  rectangular six-neighbor REM runtime.

Future versions can add topology mutation/search, alternative optimizers,
symmetry diagnostics, and standalone visualization without coupling discovery
to REM simulation mechanics.

## Standalone visualizer

After packaging, launch a built-in model or candidate file without starting the
REM simulation:

```shell
java -cp target/rem.jar remframework.geometry.discovery.GeometryDiscoveryVisualizerMain \
  --model tetrahedron --relax false

java -cp target/rem.jar remframework.geometry.discovery.GeometryDiscoveryVisualizerMain \
  --file lattice/candidates/tetrahedron-candidate.toml
```

The standalone GLFW/OpenGL window uses a Nuklear information/control panel.
Mouse drag or arrow keys orbit, right-drag or WASD pans, the wheel or `+/-`
zooms, `F` fits, `R` resets, `P` toggles projection, and Tab selects nodes.
Space starts/pauses relaxation, `N` steps, and `C` runs to convergence. The
panel also exposes playback speed, relaxation parameters, rendering toggles,
candidate export, and a plain-text metrics report.

## Batch experiments

```shell
java -cp target/rem.jar remframework.geometry.discovery.GeometryDiscoveryBatchMain \
  --model tetrahedron --seeds 1:5 \
  --learning-rates 0.04,0.08 \
  --repulsion-strengths 0.1,0.25 \
  --maximum-corrections 2.0 \
  --output lattice/experiments/tetrahedron
```

Batch combinations are ordered by seed, learning rate, repulsion strength, and
maximum correction. `runs.csv` contains one row per run and `aggregate.txt`
contains convergence rate, mean iteration count, best/worst runs, fastest
converged run, and the parameter combination with the highest convergence rate.

## Constrained topology search

`TopologySearchMain` performs deterministic small-graph connection search without
changing node count or entering the REM simulation. It supports hill climbing,
simulated annealing, add/remove/rewire/endpoint-swap mutations, geometry relaxation
through the existing engine, explicit score components, and a deterministic archive.

```shell
java -cp target/rem.jar remframework.geometry.discovery.TopologySearchMain \
  --input-model small-cubic --search-mode hill-climbing --seed 42 \
  --iterations 1000 --output lattice/topology-search/cube
```

`TopologySearchBatchMain` accepts `--seeds 1:5`,
`--search-modes hill-climbing,annealing`, and `--weight-scales 0.5,1,2`.
Outputs include explicit candidates,
history and score CSV files, a summary, and a summary checkpoint. The checkpoint
does not restore exact PRNG state, so resume is not bit-for-bit supported.
Canonical signatures normalize edge ordering but do not solve graph isomorphism.
