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
