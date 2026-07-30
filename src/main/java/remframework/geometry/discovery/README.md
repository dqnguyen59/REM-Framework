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

### Angular diagnostics and scoring profiles

Topology metadata version 0.2 separates normalized net directional bias from
isotropy. Bias is `|sum(u)| / degree`; it is useful for centrosymmetric
neighborhoods but is not a universal symmetry error. The principal isotropy
measure is the Frobenius error of the local second moment against `I/d`, where
`d` is estimated from the symmetric tensor eigenvalues.

Known angular references are applied only to opposite degree-2 directions in
1D, equilateral degree-3 directions in 2D, tetrahedral degree-4 directions in
3D, and octahedral degree-6 directions in 3D. Unsupported degrees remain
descriptive and receive no reference penalty. A deterministic Fibonacci-sphere
sample estimates uncovered solid angle. Opposite-direction mismatch is reported
separately and is disabled in the general profile.

Available profiles are `general`, `wave`, `centrosymmetric`, `tetrahedral`, and
`cubic-runtime`. Override a profile weight with options such as
`--weight-tensor-isotropy 5` or `--weight-opposite-direction 0`.

Analyze without relaxation or topology mutation:

```shell
java -cp target/rem.jar remframework.geometry.discovery.GeometryDiagnosticMain \
  --model tetrahedron --profile general

java -cp target/rem.jar remframework.geometry.discovery.GeometryDiagnosticMain \
  --file lattice/topology-search/run-001/best-candidate.toml --profile wave
```

Scores use dimensionless edge error relative to target distance, edge variance
relative to squared target distance, collision counts relative to all node
pairs, angular errors relative to pi where appropriate, and graph counts
relative to graph size. Reports retain raw values, normalized values, weights,
applicability, weighted contributions, and percentage of total.

## Main application workspace

Open `Tools > Geometry Discovery` to use the same discovery sessions and OpenGL
renderer inside the main REM Framework window. Entering the workspace records
and pauses the REM simulation state; returning to REM restores that state
without rebuilding or replacing the active lattice. The experiment remains
available while the workspace is inactive.

Relaxation and topology mutation run on one daemon worker named
`geometry-discovery-worker`. The render thread only reads copied result
snapshots. Workspace shutdown requests worker cancellation before deleting its
OpenGL resources.

The workspace exposes built-in and diagnostic models, candidate TOML loading,
relaxation playback, incremental topology search, scoring profiles and weight
overrides, aggregate and selected-node diagnostics, archive preview/comparison,
and the existing candidate, metrics, and topology-search exporters. Workspace
UI preferences are stored separately in
`geometry-discovery-workspace.properties`; active operations are never restored.

For automated window smoke checks, the normal application accepts:

```shell
java -jar target/rem.jar --geometry-discovery --frames=5
```

The `--frames` option only limits the application render loop and is not a
simulation update limit.
