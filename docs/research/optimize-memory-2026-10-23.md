# Memory optimization — 2026-10-23

## Version checkpoint

The existing diamond work was committed as `623b63e`, merged into local `main` as `fd157b0`, and tagged `diamond-lattice-v0.5.0` on that merge. Optimization work is on `optimize-memory` (Git branch names cannot contain spaces). Neither the merge/tag nor this feature branch has been pushed.

## Measured gain

Same-machine, isolated-JVM headless Node-runtime benchmark, 64x64x64, 262144 nodes, explicit GC before live-heap readings, five accepted mechanical updates, MaxRAMPercentage=60. Values below are decimal MB, not MiB; they exclude GPU/native allocations and are not whole-process working-set figures.

| Measurement | v0.5.0 baseline | Optimized Node runtime | Reduction |
|---|---:|---:|---:|
| Incremental live heap | 744.54 MB | 277.68 MB | **62.70%** |
| Live bytes per node | 2840.18 | 1059.27 | **62.70%** |
| Heap-pool build peak envelope | 1114.36 MB | 423.60 MB | **61.99%** |

The peak envelope sums individual heap-pool peaks and is conservative; it is not a time-synchronized process RSS peak. A later full-suite repeat measured 277.65 MB live, consistent with the main result. Build/update timings are included in raw CSVs but are not promoted as speed benchmarks: GC/JIT and other applications vary between runs.

JDK live object histograms identify the allocations removed. Vector3d count decreased from 10,223,638 to 3,145,750 at 64^3. Mechanical-only nodes no longer eagerly allocate six-slot detector cache vectors/arrays, two electromagnetic AVPs, an unused magnetic scratch vector, or renderer matrices for every hidden node. Optional state is still available through the existing APIs and preserves initialization semantics.

## Validated sizes and limits

| Backend | Actual validated size | Nodes | Incremental live heap | Validation |
|---|---:|---:|---:|---|
| Optimized full Node runtime | 400x400x4 | 640000 | 677.22 MB | Build, 5-update audit, and a separate 200-update XY probe run |
| Optimized full Node runtime | 400x400x8 | 1280000 | 1362.41 MB | Build and 5-update audit |
| Compact mechanical backend | 400x400x80 | 12800000 | 1518.78 MB | Initialized packet, 5 updates, streamed finite probe outputs |
| Compact mechanical backend | 400x400x200 | 32000000 | 3641.09 MB | Initialized packet, 5 updates, streamed finite probe outputs |

The older full Node implementation had rejected 400x400x4: 5000 MiB estimated build peak versus 4311 MiB then available safe budget. The new measured implementation admits that size normally. The conservative per-node build estimate is recalibrated from 8192 to 3072 bytes after measuring the allocation reduction; the existing 25% reserve, reclaimed-heap accounting and Java index limits remain. At an identical budget the admission capacity increases about 2.67x in node count. This is a capacity ratio, not an assertion that every dimension is 2.67x larger.

Largest validated is not a universal maximum. The 112^3 full-runtime attempt was refused at a moment with 3153 MiB available safe budget (4116 MiB estimated requirement), and the compact 400x400x240 attempt was refused before allocation (4,905,508,864 bytes including allocation margin). No out-of-memory crash or budget bypass was used to establish these limits. Other applications and current free RAM affect admission. A 5-update capacity smoke check does not establish long-duration stability or isotropy.

The compact path has **112 primitive payload bytes per node**: twelve double arrays (position buffers, velocity and stored acceleration) plus four integer neighbor slots. External force arrays are optional, adding 24 bytes/node only when requested; they have their own admission check. Compact admission additionally reserves 12.5% of payload plus 64 MiB for allocation overhead, within the existing safe-memory budget. The measured compact per-node heap at 32 million nodes is about 113.78 bytes, about 96% below the old Node graph's measured per-node heap. That comparison is for mechanical-only experiments, not equivalent feature sets.

## Implementation and preserved equations

1. Optional Node detector caches and electromagnetic state are lazy. Lazy AVP initialization retains the initial buffer coordinates and last explicitly assigned position. Mechanical disturbance reset clears optional fields only if allocated.
2. Node render matrices are lazy. A synchronous hidden-node render refresh can release them; visible/inspected nodes recreate both matrix buffers. Existing visibility-filtered GPU instance buffers are retained rather than claimed as a new optimization.
3. `DiamondTopology` computes the regular topology using integer addresses, eliminating temporary string-ID hash maps from diamond construction. Direct FCC site resolution replaces per-node coordinate hash maps in runtime probe samplers. Neighbor ordering, equilibrium positions and probe weights match the historical implementation.
4. `CompactDiamondMechanics` and a streamed headless runner provide the larger mechanical experiment path. It does not allocate Node/Entity/rendering objects, identities, occupancies, pressure or transport. It is not silently substituted into the interactive simulation. Interactive occupancy-local pressure, residual transfer and all existing cubic paths retain their implementations.

For each node, the backend reproduces the *actual* Node v0.1 operation order (not a replacement Verlet integrator): S starts at zero, then add each direct neighbor position and subtract the current position in historical slot order. With force F, gamma and c=k^2/4 for the experimental diamond, A=(S+F)*gamma; V'=V+A*c; P'=(P+V')+(A*c)*0.5; stored acceleration becomes (A*c)*0.5, and force is cleared. Fixed nodes retain their explicitly assigned anchor position while velocity/stored acceleration follow the same existing update rule. No damping, clipping, saturation, velocity cap or precision reduction was introduced. Double precision is retained.

An exact-equality test compares positions, velocities and stored accelerations against the Node implementation over 40 updates with seeded irregular displacements, velocities and nonzero forces, including boundary nodes. Raw bond-imbalance and legacy-versus-direct probe stencil weights are checked too. These are implementation-parity tests, not evidence that emergent isotropy is proven.

## Use

Interactive, with the memory-efficient existing features:

```powershell
mvn exec:exec '-Dexec.args=-XX:MaxRAMPercentage=60 -classpath %classpath remframework.Main --diamond-size=400x400x4'
```

The regular dimension dialog remains available. Keep scale1.0 and a physical slice to avoid rendering every node. The compact runner is separate and starts without OpenGL:

```powershell
mvn exec:exec '-Dexec.args=-XX:MaxRAMPercentage=60 -classpath %classpath remframework.Main --compact-diamond=400,400,200,5,8,16,4,xyz,compact-signals.csv'
```

Compact arguments are X,Y,Z,updates,wavelength,firstRadius,shellSpacing,xy-or-xyz,outputCSV. The amplitude is 0.001, k=0.5 and gamma=1 in this controlled runner. It streams two shells of position and raw-imbalance signals and a sibling `.metrics.csv`; history storage therefore scales with probes, not nodes. Sources/receivers must fit the selected geometry. Fixed surfaces are included: this runner deliberately provides no reflection-free or isotropy verdict. Increase updates explicitly for scientific measurements; the capacity checks above used only five.

## Validation and artifacts

Full-suite verification completed: 526 tests, 524 passed, 2 skipped, zero failures/errors and zero flaky reruns recorded in the final reports. The command allowed up to two flaky retries but none were used in that final run. Earlier iterations found an out-of-domain sampler exception regression, which was corrected; a separate run hit an unchanged asynchronous Geometry Discovery playback publication-count assertion, whose isolated six-test rerun passed.

The subsequent actual GUI launch exposed a lazy-initialization recursion through Light.updateMatrix(), not covered by the earlier suite. Non-Node entity initialization was restored to allocate its matrix storage before virtual dispatch; the lazy getter is also safe for overridden initializers. Added a regression test for actual Light construction. After that final change, CompactDiamondMechanicsTest, EntityTransformMatrixTest, RendererOverlaySynchronizationTest and FrameIsotropyLayoutTest passed. The memory audit is finally made opt-in with `-DrunMemoryAudit=true` so normal CI does not run allocation/GC profiling or retain its benchmark graph. Its default skip was verified separately. Test inventories therefore combine the full-suite run with the post-fix targeted runs, rather than claiming a new one-shot full-suite run after those final changes.

Native UI screenshot verification confirmed the actual 400x400x4 application renders and remains responsive, paused at scale1.0 with pressure/transport/excitation disabled. A JDK live-object histogram in the running application totals 731,839,368 bytes (~731.84 MB), including matrices for roughly 160000 visible nodes instead of all 640000. This is live Java object size, not total Windows RAM or GPU memory. The application is left running at that size. No user data or Windows settings were changed through automation.

Raw evidence: diamond-memory-before.csv / diamond-memory-after.csv; diamond-memory-before-histogram.txt / diamond-memory-after-histogram.txt / diamond-memory-interactive-histogram.txt; diamond-memory-400x400x4.csv / diamond-memory-400x400x8.csv; compact-diamond-400x400x80.csv.metrics.csv / compact-diamond-400x400x200.csv.metrics.csv. Streamed compact signal CSVs are retained beside those metrics. Failed admission attempts are in diamond-memory-112cube.log and compact-diamond-capacity-limit.log. Test commands use the existing Maven dependency cache; no new dependency was added.

## Exact changed source/test files on optimize-memory

- src/main/java/remframework/Main.java
- src/main/java/remframework/geometry/discovery/research/diamond/CompactDiamondExperiment.java (new)
- src/main/java/remframework/geometry/discovery/research/diamond/DiamondReceiverSampling.java
- src/main/java/remframework/geometry/discovery/research/diamond/DiamondRuntimeDisturbance.java
- src/main/java/remframework/geometry/discovery/research/diamond/DiamondRuntimeReceivers.java
- src/main/java/remframework/lattice/CompactDiamondMechanics.java (new)
- src/main/java/remframework/lattice/DiamondTopology.java (new)
- src/main/java/remframework/lattice/DiamondMemoryBudget.java
- src/main/java/remframework/opengl/model/DiamondProbePlacement.java
- src/main/java/remframework/opengl/model/Lattice.java
- src/main/java/remframework/opengl/model/Scene.java
- src/main/java/remframework/opengl/model/entity/Entity.java
- src/main/java/remframework/opengl/model/entity/Node.java
- src/test/java/remframework/lattice/CompactDiamondMechanicsTest.java (new)
- src/test/java/remframework/lattice/DiamondMemoryAuditTest.java (new)

Documentation/artifacts added: this report, docs/research/diamond-lattice-rem-compatibility.md report link, and the raw CSV/histogram files listed above. Generated application logs and the regenerated cubic-lattice template are not part of the feature commit.
