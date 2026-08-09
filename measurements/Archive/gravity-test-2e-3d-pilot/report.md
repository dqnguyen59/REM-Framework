# Gravity Test 2E: 3D feasibility pilot

## Scope

Gravity Test 2E is a feasibility pilot. It tests whether the existing REM
Framework passive-detector architecture can execute a practical 3D,
reflection-controlled experiment. It does not establish or reject an
inverse-square gravitational law.

## Configuration

- Lattice: 161 × 161 × 161 (4,173,281 nodes)
- Source: one Identity at (80, 80, 80)
- Oscillation: Z-axis sine, amplitude 200, 20° per update, period 18
- Transport: disabled
- Node equation: canonical v0.1, unchanged
- Boundary: fixed outer nodes, 30 cosine absorption layers
- Detectors: 54 passive nodes; PX/NX/PY/NY/PZ/NZ at radii
  5, 10, 15, 20, 25, 30, 35, 40, 45
- Updates: 180

## Memory feasibility

The JVM maximum heap was 29.969 GiB.
A conservative 3 KiB/node object-graph estimate predicted 11.94 GiB for the
node graph and 14.92 GiB including 25% transient/JVM headroom. Measured used
heap after lattice initialization was
10.820 GiB; peak observed heap was
11.110 GiB. The peak was
37.07%
of maximum heap, below the 60% safety target.

JVM object layout varies with runtime, compressed references, alignment, and
allocation behavior; estimates are intentionally conservative.

| Candidate | Nodes | Conservative node graph | With 25% headroom |
|---:|---:|---:|---:|
| 101³ | 1,030,301 | 2.95 GiB | 3.68 GiB |
| 121³ | 1,771,561 | 5.07 GiB | 6.34 GiB |
| 141³ | 2,803,221 | 8.02 GiB | 10.03 GiB |
| 161³ | 4,173,281 | 11.94 GiB | 14.92 GiB |
| 181³ | 5,929,741 | 16.97 GiB | 21.21 GiB |
| 201³ | 8,120,601 | 23.23 GiB | 29.04 GiB |

Used heap before lattice allocation was
22.830 MiB. Used heap after detector
configuration was unchanged within telemetry resolution; detector metadata is
negligible compared with the lattice graph.

## Runtime

- Lattice initialization: 5408.036 ms
- Simulation: 45912.375 ms
- Analysis: 4816.021 ms
- Mean update: 254.727 ms
- Median update: 253.176 ms
- Minimum update: 244.760 ms
- Maximum update: 480.911 ms
- Updates/second: 3.926
- GC count: 19
- GC elapsed: 1305 ms

## Collection integrity

The raw collection contains exactly 54 × 180 = 9,720 unique
detector/update rows. Every detector has updates 1–180, all numeric values are
finite, geometry is exact, all detectors are outside the absorption layer, and
the detector logger contains no Identity or Occupancy measurements.

## Arrival and reflection

Primary arrival is the first complete 18-update source cycle with a finite,
non-zero cycle-derived radial gradient. Threshold-free first non-zero
displacement and raw-gradient updates are retained separately. Conservative
reflection estimates minimize the source → first non-unity absorption
interface → detector mirror path using at most one lattice edge per update.

Reflection-safe radii with at least one complete post-arrival cycle:
5, 10, 15, 20, 25, 30.
The common six-direction reflection-safe range is
5, 10, 15, 20, 25.
Radius 30 is safe for one cycle only along ±Z and is excluded from common
six-axis radial fits.

No detector reaches the preferred ten reflection-safe post-arrival cycles.
This is a pilot design constraint, not a relaxed gate.

| Radius | PX arrival | NX | PY | NY | PZ | NZ | Reflection | Safe cycles PX/NX/PY/NY/PZ/NZ |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 5 | 18 | 18 | 18 | 18 | 18 | 18 | 96 | 4/4/4/4/4/4 |
| 10 | 18 | 18 | 18 | 18 | 18 | 18 | 91 | 4/4/4/4/4/4 |
| 15 | 18 | 18 | 18 | 18 | 18 | 18 | 86 | 3/3/3/3/3/3 |
| 20 | 36 | 36 | 36 | 36 | 36 | 36 | 81 | 2/2/2/2/2/2 |
| 25 | 36 | 36 | 36 | 36 | 36 | 36 | 76 | 2/2/2/2/2/2 |
| 30 | 54 | 54 | 54 | 54 | 36 | 36 | 71 | 0/0/0/0/1/1 |
| 35 | 54 | 54 | 54 | 54 | 54 | 54 | 66 | 0/0/0/0/0/0 |
| 40 | 72 | 72 | 72 | 72 | 54 | 54 | 61 | 0/0/0/0/0/0 |
| 45 | 72 | 72 | 72 | 72 | 72 | 72 | 56 | 0/0/0/0/0/0 |

## Symmetry

Maximum opposite-direction relative difference among defined safe-cycle RMS
metrics: 0.952128. Maximum X/Y/Z cross-axis relative difference:
1. X/Y/Z comparisons are polarization-sensitive axis
comparisons, not claims of spherical isotropy.

| Opposite pair | Maximum relative RMS difference | Maximum arrival-update difference |
|---|---:|---:|
| PX/NX | 5.77097e-14 | 0 |
| PY/NY | 1.97384e-14 | 0 |
| PZ/NZ | 0.952128 | 0 |

X and Y responses agree far more closely than either agrees with Z. The
largest reported opposite-direction difference occurs for the ±Z raw radial
gradient at radius 5. With only four safe cycles there, this is a pilot
observation requiring a longer reflection-safe final design.

## Exploratory radial diagnostics

The model-fit CSV compares inverse-linear, inverse-square, free power law,
exponential, and offset power law only where at least four finite, positive,
reflection-safe radii exist. Fits are feasibility diagnostics. No fitted
exponent is interpreted as a physical law, and zero/non-positive values are
excluded from logarithmic interpretation.

| Metric | Lowest-BIC pilot model | BIC | Free-power exponent n | Usable common radii |
|---|---|---:|---:|---:|
| rmsDisplacementMagnitude | exponential | -26.0478 | 1.45758 | 5 |
| rmsRadialDisplacement | exponential | -37.0339 | 1.45758 | 5 |
| rmsTransverseDisplacement | exponential | -30.1025 | 1.45758 | 5 |
| rmsRawGradientRadial | exponential | -36.6914 | 2.4902 | 5 |
| rmsCycleDerivedGradientRadial | offset-power-law | -58.2797 | 2.73974 | 5 |
| rmsRadialAcceleration | free-power-law | -66.9687 | 2.16293 | 5 |

The common fits use only radii 5, 10, 15, 20, and 25, with between two and four
safe cycles per detector. The fitted exponents and model rankings are too
sensitive to short windows, polarization, and near-source structure for a
physical-law conclusion.

## Feasibility conclusion

The current implementation can allocate and run a 161³ passive-detector
lattice within the configured heap, with stable update throughput and valid
3D X/Y/Z detector sampling. However, the 30-layer absorption geometry leaves
too little center-to-interface distance for ten reflection-safe post-arrival
cycles. A final 3D radial-falloff design needs a larger physical interior,
fewer required cycles, or an explicitly re-evaluated boundary strategy.

Gravity Test 2E demonstrates whether a practical, passive,
reflection-controlled 3D radial-falloff experiment can be executed with the
current implementation. It does not by itself establish or reject an
inverse-square gravitational law.

## Limitations

- The primary range was constrained by conservative causal reflection bounds.
- Arrivals are quantized to 18-update cycle boundaries.
- The source is Z-polarized, so Z versus X/Y differences may be physical
  polarization response rather than lattice-axis asymmetry.
- The headless runner omits rendering only; it retains source, node update,
  position commit, and detector sampling order.
- The initial GUI launch and two pre-update headless setup attempts failed
  before simulation because no display/OpenGL context was available. Their
  diagnostics are preserved; only one run executed physics updates.

## Chart validation

All 10 PNG/SVG chart pairs passed standard Matplotlib,
Pillow, PNG-signature, OS `file`, size, dimension, extrema, and `/tmp` reopen
validation. See `charts/chart-validation.txt`.
