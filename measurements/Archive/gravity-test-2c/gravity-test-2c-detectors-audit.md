# Passive-detector Gravity Test 2C audit

## Decision: ANALYSIS CORRECTION

Run-001 raw measurements pass integrity and passivity review. The original analysis used cancellation-scale strict-nonzero tails and included a cycle that was only partly post-arrival; its shared reflection constant was also not detector-specific. The simulation does not need to be rerun. Original analysis files are preserved and superseded by v2.

## Detector passivity and source trace

- `Detector.java:13-48` contains only descriptive values. Its constructor defensively copies `Vector3i`; it constructs neither `Identity` nor `Occupancy`.
- `Project.java:173-180` creates Occupancies only from `[OscillatorN]`; detector parsing at `Project.java:180` only creates instrumentation records.
- The sampling call chain is `Simulator.java:735` → `Simulator.java:785-800` → `DetectorExperimentController.java:43-51` → `DetectorMeasurementLogger.java:67-84`.
- `DetectorMeasurementLogger.java:123-131` copies position, equilibrium position, velocity, and acceleration, then invokes `Node.readGradientImbalance()`. It never calls the mutating `measureGradientImbalance()` used by transport at `TransportSystem.java:177-183`.
- `Node.readGradientImbalance()` (`Node.java:186-196`) allocates a new result and only reads this node's and its direct neighbors' committed position components through JOML `distance`. It does not write position, position buffer, velocity, acceleration, force, flow arrays, previous-flow arrays, gradient caches, neighbors, or transport state.
- Detector cycle minima/maxima are logger-owned vectors (`DetectorMeasurementLogger.java:191-209`), not Node caches. CSV formatting and flushing mutate only instrumentation state.
- Physics tasks finish and join at `Simulator.java:723-724`; position commits finish and join at `Simulator.java:732-734`; sampling then runs serially at line 735. Detectors add no physics task, change no segment, and cannot race the lattice. They can increase wall-clock duration through CSV I/O but not update order or lattice values.
- Existing tests do not fully prove whole-lattice equivalence: `DetectorInfrastructureTest.java:54-62` compares only position, velocity, and acceleration of synthetic nodes and does not execute two complete simulations or compare buffers, force, arrays, caches, neighbors, and transport state. Passivity is confirmed here by the complete call trace; the test is a safeguard, not the proof.

## Update and logging semantics

For a logged update N the order is:

1. `Node.swapBuffer()` changes only the render matrix buffer (`Simulator.java:699-704`).
2. Source transport diagnostics run, then `Occupancy.update()` advances source phase and applies the source force (`Simulator.java:754-770`; `Identity.java:141-170,195-207`; `Occupancy.java:261-274`).
3. Node v0.1 reads committed neighbor positions, calculates the force-derived acceleration, clears force, updates velocity and position buffer (`Node.java:269-302`).
4. All physics threads join; `Entity.updatePosition()` commits position buffer to position (`Scene.java:436-460`; `Entity.java:245-249`).
5. The detector logger detects a source phase wrap, increments its update counter, reads the committed node state and gradient, updates/completes its cycle accumulator, and writes the row (`DetectorMeasurementLogger.java:67-84,96-144`).
6. After row 960 is written, the active detector controller owns the limit check and sets termination (`Simulator.java:777-800`). The legacy counter is explicitly excluded while detectors are enabled at lines 777-779.

Thus row N is post-update-N state, not a mixed pre/post row. Row 1 has source phase 20°, row 18 has phase 0° and closes cycle 1, and row 960 is written before termination. The raw file has every per-detector update 1..960, confirming the former 959-row defect is fixed rather than masked.

## Measured quantity definitions

- `positionDeviation`: committed position minus immutable equilibrium position at sampling.
- `velocity`: Node's velocity after the current v0.1 update.
- `acceleration`: the Node acceleration vector as it exists after the in-place v0.1 integration expression. Because `acceleration.mul(cws)` and then `.mul(0.5)` mutate that vector at `Node.java:295`, the CSV value is the retained integration working value (for this project, the initially calculated vector scaled by `cws * 0.5 = 1/12`), not Identity or transport acceleration. Its in-plane components are zero here.
- `rawGradient`: `(distance(right)-distance(left), distance(top)-distance(bottom), distance(front)-distance(back))` from committed positions.
- `cycleDerivedGradient`: per component, `(cycle maximum + cycle minimum)/2` over all 18 raw-gradient samples in one source cycle. It preserves sign, is neither sum, mean, difference nor integral, is reset after each completion, has no duration normalization, and represents a cycle response rather than an instantaneous field. The completed value persists in rows until the next cycle completion.
- No detector pressure, momentum, Occupancy transport acceleration, or other transport quantity is logged or derived.

## Raw and baseline integrity

- Baseline: {'rows': 10560, 'updates': 960, 'detectors': 11, 'maxAbsComponent': 0.0, 'firstNonzero': None}
- Raw rows: 28800; detectors: 30; sequences 1..960: True
- Non-finite values: 0; signed negative zeros: 0
- Source-locked effective arrival speeds span `0.495378121282` to `0.571428571429` lattice edges/update. Reflection uses the stricter causal upper bound `1.0`, not these slower measured effective values.

## Arrival audit

| Detector | pos≠0 | vel≠0 | acc≠0 | raw≠0 | first nonzero cycle | old arrival | v2 arrival | signal at v2 arrival | next-six growth |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| R020_A00 | 21 | 21 | 21 | 23 | 36 | 21 | 36 | 0.0186042016424 | 0.0186, 0.03639, 0.06772, 0.1202, 0.204, 0.3317 |
| R020_A30 | 28 | 28 | 28 | 27 | 36 | 27 | 36 | 0.00827017594544 | 0.00827, 0.02045, 0.04603, 0.09522, 0.1826, 0.3268 |
| R020_A45 | 29 | 29 | 29 | 28 | 36 | 28 | 36 | 0.0042087132245 | 0.004209, 0.0117, 0.02908, 0.06554, 0.1352, 0.2574 |
| R020_A60 | 28 | 28 | 28 | 27 | 36 | 27 | 36 | 0.00827017594544 | 0.00827, 0.02045, 0.04603, 0.09522, 0.1826, 0.3268 |
| R020_A90 | 21 | 21 | 21 | 23 | 36 | 21 | 36 | 0.0186042016424 | 0.0186, 0.03639, 0.06772, 0.1202, 0.204, 0.3317 |
| R040_A00 | 47 | 47 | 47 | 59 | 72 | 47 | 72 | 0.000265428376725 | 0.0002654, 0.000501, 0.000921, 0.00165, 0.002883, 0.004917 |
| R040_A30 | 57 | 57 | 57 | 65 | 72 | 57 | 72 | 1.73746006813e-05 | 1.737e-05, 4.331e-05, 0.0001025, 0.0002311, 0.0004976, 0.001026 |
| R040_A45 | 58 | 57 | 57 | 65 | 72 | 57 | 72 | 2.64476899486e-05 | 2.645e-05, 6.867e-05, 0.0001682, 0.0003904, 0.0008605, 0.001807 |
| R040_A60 | 57 | 57 | 57 | 65 | 72 | 57 | 72 | 1.73746006813e-05 | 1.737e-05, 4.331e-05, 0.0001025, 0.0002311, 0.0004976, 0.001026 |
| R040_A90 | 47 | 47 | 47 | 59 | 72 | 47 | 72 | 0.000265428376725 | 0.0002654, 0.000501, 0.000921, 0.00165, 0.002883, 0.004917 |
| R060_A00 | 82 | 81 | 81 | 99 | 108 | 81 | 108 | 4.78384425062e-06 | 4.784e-06, 8.909e-06, 1.629e-05, 2.928e-05, 5.172e-05, 8.98e-05 |
| R060_A30 | 92 | 91 | 91 | 104 | 108 | 91 | 108 | 2.24688834372e-07 | 2.247e-07, 5.35e-07, 1.231e-06, 2.74e-06, 5.911e-06, 1.237e-05 |
| R060_A45 | 93 | 92 | 92 | 105 | 108 | 92 | 108 | 2.03749292604e-07 | 2.037e-07, 5.161e-07, 1.257e-06, 2.948e-06, 6.67e-06, 1.457e-05 |
| R060_A60 | 92 | 91 | 91 | 104 | 108 | 91 | 108 | 2.24688834372e-07 | 2.247e-07, 5.35e-07, 1.231e-06, 2.74e-06, 5.911e-06, 1.237e-05 |
| R060_A90 | 82 | 81 | 81 | 99 | 108 | 81 | 108 | 4.78384425068e-06 | 4.784e-06, 8.909e-06, 1.629e-05, 2.928e-05, 5.172e-05, 8.98e-05 |
| R080_A00 | 120 | 119 | 119 | 140 | 144 | 119 | 144 | 9.55203655062e-08 | 9.552e-08, 1.766e-07, 3.222e-07, 5.799e-07, 1.03e-06, 1.805e-06 |
| R080_A30 | 129 | 128 | 128 | 145 | 162 | 128 | 162 | 0.000409709537412 | 0.0004097, 0.0006576, 0.001038, 0.001613, 0.002466, 0.003712 |
| R080_A45 | 133 | 132 | 132 | 149 | 162 | 132 | 162 | 9.6077137075e-05 | 9.608e-05, 0.0001658, 0.0002806, 0.000466, 0.0007597, 0.001216 |
| R080_A60 | 129 | 128 | 128 | 145 | 162 | 128 | 162 | 0.000409709537412 | 0.0004097, 0.0006576, 0.001038, 0.001613, 0.002466, 0.003712 |
| R080_A90 | 120 | 119 | 119 | 140 | 144 | 119 | 144 | 9.55203655617e-08 | 9.552e-08, 1.766e-07, 3.222e-07, 5.799e-07, 1.03e-06, 1.805e-06 |
| R100_A00 | 159 | 158 | 158 | 183 | 198 | 158 | 198 | 2.4304538819e-05 | 2.43e-05, 3.73e-05, 5.671e-05, 8.543e-05, 0.0001275, 0.0001886 |
| R100_A30 | 169 | 168 | 168 | 189 | 198 | 168 | 198 | 3.08768836821e-06 | 3.088e-06, 5.325e-06, 9.051e-06, 1.517e-05, 2.507e-05, 4.088e-05 |
| R100_A45 | 172 | 171 | 171 | 191 | 198 | 171 | 198 | 1.2667802487e-06 | 1.267e-06, 2.307e-06, 4.131e-06, 7.276e-06, 1.261e-05, 2.151e-05 |
| R100_A60 | 169 | 168 | 168 | 189 | 198 | 168 | 198 | 3.08768836821e-06 | 3.088e-06, 5.325e-06, 9.051e-06, 1.517e-05, 2.507e-05, 4.088e-05 |
| R100_A90 | 159 | 158 | 158 | 183 | 198 | 158 | 198 | 2.4304538819e-05 | 2.43e-05, 3.73e-05, 5.671e-05, 8.543e-05, 0.0001275, 0.0001886 |
| R120_A00 | 199 | 198 | 198 | 226 | 234 | 198 | 234 | 6.49389861163e-07 | 6.494e-07, 1.022e-06, 1.595e-06, 2.469e-06, 3.794e-06, 5.783e-06 |
| R120_A30 | 208 | 208 | 208 | 231 | 234 | 208 | 234 | 5.60842181585e-08 | 5.608e-08, 9.985e-08, 1.756e-07, 3.048e-07, 5.229e-07, 8.861e-07 |
| R120_A45 | 212 | 211 | 211 | 234 | 234 | 211 | 234 | 1.54538693509e-08 | 1.545e-08, 2.925e-08, 5.455e-08, 1.003e-07, 1.817e-07, 3.247e-07 |
| R120_A60 | 208 | 208 | 208 | 231 | 234 | 208 | 234 | 5.6084218214e-08 | 5.608e-08, 9.985e-08, 1.756e-07, 3.048e-07, 5.229e-07, 8.861e-07 |
| R120_A90 | 199 | 198 | 198 | 226 | 234 | 198 | 234 | 6.49389861329e-07 | 6.494e-07, 1.022e-06, 1.595e-06, 2.469e-06, 3.794e-06, 5.783e-06 |

## Source-cycle selection

Configured increment is 20° per update; observed phase wraps and cycle-completion spacing are exactly 18 updates. Selected cycle numbers are contiguous and start one cycle after meaningful arrival.

| Detector | Arrival | First cycle | First end | 30th cycle | 30th end | Skipped | Reason |
|---|---:|---:|---:|---:|---:|---:|---|
| R020_A00 | 36 | 3 | 54 | 32 | 576 | 0 | none |
| R020_A30 | 36 | 3 | 54 | 32 | 576 | 0 | none |
| R020_A45 | 36 | 3 | 54 | 32 | 576 | 0 | none |
| R020_A60 | 36 | 3 | 54 | 32 | 576 | 0 | none |
| R020_A90 | 36 | 3 | 54 | 32 | 576 | 0 | none |
| R040_A00 | 72 | 5 | 90 | 34 | 612 | 0 | none |
| R040_A30 | 72 | 5 | 90 | 34 | 612 | 0 | none |
| R040_A45 | 72 | 5 | 90 | 34 | 612 | 0 | none |
| R040_A60 | 72 | 5 | 90 | 34 | 612 | 0 | none |
| R040_A90 | 72 | 5 | 90 | 34 | 612 | 0 | none |
| R060_A00 | 108 | 7 | 126 | 36 | 648 | 0 | none |
| R060_A30 | 108 | 7 | 126 | 36 | 648 | 0 | none |
| R060_A45 | 108 | 7 | 126 | 36 | 648 | 0 | none |
| R060_A60 | 108 | 7 | 126 | 36 | 648 | 0 | none |
| R060_A90 | 108 | 7 | 126 | 36 | 648 | 0 | none |
| R080_A00 | 144 | 9 | 162 | 38 | 684 | 0 | none |
| R080_A30 | 162 | 10 | 180 | 39 | 702 | 0 | none |
| R080_A45 | 162 | 10 | 180 | 39 | 702 | 0 | none |
| R080_A60 | 162 | 10 | 180 | 39 | 702 | 0 | none |
| R080_A90 | 144 | 9 | 162 | 38 | 684 | 0 | none |
| R100_A00 | 198 | 12 | 216 | 41 | 738 | 0 | none |
| R100_A30 | 198 | 12 | 216 | 41 | 738 | 0 | none |
| R100_A45 | 198 | 12 | 216 | 41 | 738 | 0 | none |
| R100_A60 | 198 | 12 | 216 | 41 | 738 | 0 | none |
| R100_A90 | 198 | 12 | 216 | 41 | 738 | 0 | none |
| R120_A00 | 234 | 14 | 252 | 43 | 774 | 0 | none |
| R120_A30 | 234 | 14 | 252 | 43 | 774 | 0 | none |
| R120_A45 | 234 | 14 | 252 | 43 | 774 | 0 | none |
| R120_A60 | 234 | 14 | 252 | 43 | 774 | 0 | none |
| R120_A90 | 234 | 14 | 252 | 43 | 774 | 0 | none |

## Independent recomputation of original outputs

Comparison tolerances: absolute `1e-15`, relative `1e-12`. No material mismatch was found.

### Cycle output discrepancies

| Column | max absolute | max relative |
|---|---:|---:|
| accelerationRadial | 0 | 0 |
| accelerationTangential | 0 | 0 |
| actualAngleDegrees | 0 | 0 |
| actualEuclideanRadius | 0 | 0 |
| cycleDerivedGradientRadial | 0 | 0 |
| cycleDerivedGradientTangential | 0 | 0 |
| rawGradientRadial | 0 | 0 |
| rawGradientTangential | 0 | 0 |
| requestedAngleDegrees | 0 | 0 |
| requestedRadius | 0 | 0 |
| update | 0 | 0 |

### Summary output discrepancies

| Column | max absolute | max relative |
|---|---:|---:|
| accelerationAxisRatio | 0 | 0 |
| accelerationIsotropyError | 0 | 0 |
| accelerationTangentialToRadial | 0 | 0 |
| actualAngleDegrees | 0 | 0 |
| actualEuclideanRadius | 0 | 0 |
| arrivalUpdate | 0 | 0 |
| coordinateRoundingErrorMagnitude | 0 | 0 |
| cycleDerivedGradientAxisRatio | 0 | 0 |
| cycleDerivedGradientIsotropyError | 0 | 0 |
| cycleDerivedGradientTangentialToRadial | 0 | 0 |
| meanSignedAccelerationRadial | 0 | 0 |
| meanSignedAccelerationTangential | 0 | 0 |
| meanSignedCycleDerivedGradientRadial | 0 | 0 |
| meanSignedCycleDerivedGradientTangential | 0 | 0 |
| meanSignedRawGradientRadial | 0 | 0 |
| meanSignedRawGradientTangential | 0 | 0 |
| nodeX | 0 | 0 |
| nodeY | 0 | 0 |
| nodeZ | 0 | 0 |
| rawGradientAxisRatio | 0 | 0 |
| rawGradientIsotropyError | 0 | 0 |
| rawGradientTangentialToRadial | 0 | 0 |
| reflectionEstimate | 0 | 0 |
| requestedAngleDegrees | 0 | 0 |
| requestedRadius | 0 | 0 |
| rmsAccelerationRadial | 0 | 0 |
| rmsAccelerationTangential | 0 | 0 |
| rmsCycleDerivedGradientRadial | 0 | 0 |
| rmsCycleDerivedGradientTangential | 0 | 0 |
| rmsRawGradientRadial | 0 | 0 |
| rmsRawGradientTangential | 0 | 0 |
| windowEndUpdate | 0 | 0 |

## Geometry

| Detector | requested r | actual r | radial error | requested angle | actual angle | angular error |
|---|---:|---:|---:|---:|---:|---:|
| R020_A00 | 20 | 20 | 0 | 0 | 0 | 0 |
| R020_A30 | 20 | 19.7230829233 | -0.276917076684 | 30 | 30.4655449195 | 0.46554491946 |
| R020_A45 | 20 | 19.7989898732 | -0.201010126777 | 45 | 45 | 0 |
| R020_A60 | 20 | 19.7230829233 | -0.276917076684 | 60 | 59.5344550805 | -0.46554491946 |
| R020_A90 | 20 | 20 | 0 | 90 | 90 | 0 |
| R040_A00 | 40 | 40 | 0 | 0 | 0 | 0 |
| R040_A30 | 40 | 40.3112887415 | 0.311288741493 | 30 | 29.7448812969 | -0.255118703058 |
| R040_A45 | 40 | 39.5979797464 | -0.402020253553 | 45 | 45 | 0 |
| R040_A60 | 40 | 40.3112887415 | 0.311288741493 | 60 | 60.2551187031 | 0.255118703058 |
| R040_A90 | 40 | 40 | 0 | 90 | 90 | 0 |
| R060_A00 | 60 | 60 | 0 | 0 | 0 | 0 |
| R060_A30 | 60 | 60.0333240792 | 0.0333240792145 | 30 | 29.9816393688 | -0.0183606311507 |
| R060_A45 | 60 | 59.3969696197 | -0.60303038033 | 45 | 45 | 0 |
| R060_A60 | 60 | 60.0333240792 | 0.0333240792145 | 60 | 60.0183606312 | 0.0183606311507 |
| R060_A90 | 60 | 60 | 0 | 90 | 90 | 0 |
| R080_A00 | 80 | 80 | 0 | 0 | 0 | 0 |
| R080_A30 | 80 | 79.7558775264 | -0.244122473638 | 30 | 30.1013045656 | 0.1013045656 |
| R080_A45 | 80 | 80.6101730553 | 0.610173055266 | 45 | 45 | 0 |
| R080_A60 | 80 | 79.7558775264 | -0.244122473638 | 60 | 59.8986954344 | -0.1013045656 |
| R080_A90 | 80 | 80 | 0 | 90 | 90 | 0 |
| R100_A00 | 100 | 100 | 0 | 0 | 0 | 0 |
| R100_A30 | 100 | 100.344406919 | 0.344406919369 | 30 | 29.8865269404 | -0.113473059576 |
| R100_A45 | 100 | 100.409162928 | 0.40916292849 | 45 | 45 | 0 |
| R100_A60 | 100 | 100.344406919 | 0.344406919369 | 60 | 60.1134730596 | 0.113473059576 |
| R100_A90 | 100 | 100 | 0 | 90 | 90 | 0 |
| R120_A00 | 120 | 120 | 0 | 0 | 0 | 0 |
| R120_A30 | 120 | 120.066648158 | 0.0666481584291 | 30 | 29.9816393688 | -0.0183606311507 |
| R120_A45 | 120 | 120.208152802 | 0.208152801713 | 45 | 45 | 0 |
| R120_A60 | 120 | 120.066648158 | 0.0666481584291 | 60 | 60.0183606312 | 0.0183606311507 |
| R120_A90 | 120 | 120 | 0 | 90 | 90 | 0 |

Radius-corrected ratios use a local empirical log slope fitted only from neighboring measured 0° axis radii; no inverse-square law is assumed. Both uncorrected and corrected values are in the v2 report and summary.
