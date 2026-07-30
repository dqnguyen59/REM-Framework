# Experiment 3B — Rotational Consistency of the Angular Response

## 1. Experiment title

Experiment 3B tests discrete X/Y/Z rotational consistency of the passive
angular response in the REM Framework.

## 2. Objective

The question is whether the measured pattern follows the active oscillator
axis, remains fixed to lattice coordinates, or exposes orientation-specific
implementation behavior. This is not a radial-law experiment.

## 3. Configuration

All runs use a 161³ lattice, source (80,80,80), requested radius 20, the same
26 detector nodes, amplitude 200, 20° per update, period 18, and 180 updates.
The Fundamental Node Equation, update order, transport, boundaries, and
absorption are unchanged from Experiment 3A.

## 4. Run definitions

X, Y, and Z runs use amplitudes (200,0,0), (0,200,0), and (0,0,200).
Only the active axis, phase-increment axis, and amplitude component differ.

## 5. Detector geometry

The shared geometry has 26 unique nodes: 6 axis, 12 face-diagonal,
and 8 body-diagonal directions. Actual radii and lattice-coordinate theta/phi
are retained unchanged across all runs.

## 6. Rotation convention

Canonical coordinates are `(longitudinal,U,V)`. Bases are X→(X,Y,Z),
Y→(Y,Z,X), and Z→(Z,X,Y). This exact cyclic signed-coordinate permutation is
invertible, norm-preserving, class-preserving, and opposite-preserving.

## 7. Reflection-safe cycle intersection

The all-run, all-detector intersection contains 2 complete cycles:
3–4, updates 37–
72. Only these cycles feed cross-run comparisons.

- X: meaningful arrivals 36–36; per-detector safe cycles 2–2; within-run common cycles 3–4.
- Y: meaningful arrivals 36–36; per-detector safe cycles 2–2; within-run common cycles 3–4.
- Z: meaningful arrivals 36–36; per-detector safe cycles 2–2; within-run common cycles 3–4.

## 8. Per-run parity findings

- X: acceleration even=1.43791e-14; signed radial acceleration odd=4.65845e-13; raw radial gradient even=0.0196656.
- Y: acceleration even=1.02708e-14; signed radial acceleration odd=1.85663e-13; raw radial gradient even=0.0196656.
- Z: acceleration even=1.82575e-14; signed radial acceleration odd=3.62567e-13; raw radial gradient even=0.0196656.

## 9. Source-relative angular comparison

For acceleration-magnitude RMS, median rotational CV is
1.77263e-15 and the
maximum is 1.10275e-14.
The overlay and canonical-direction CSV retain all individual values.

## 10. Rotational residual findings

Across magnitude/RMS metrics, the worst CV is
1.1125e-14 for `radialAccelerationRms` at
`L-1_U+1_V+1`; its maximum relative spread is
2.69384e-14. Categories are descriptive, not proof
thresholds.

## 11. Lattice-fixed comparison

`lattice-fixed-comparison.csv` compares each unchanged physical detector under
all three sources. It is separate from the rotated canonical comparison so
source-following and lattice-fixed patterns are not conflated.

## 12. Longitudinal/transverse comparison

Source-longitudinal and source-transverse acceleration RMS are retained
separately from radial acceleration. The chart reports both against
source-relative polar angle.

## 13. Azimuthal-symmetry comparison

Each run is grouped by equal source-relative polar angle using its own
documented transverse basis. Per-run means, variation, and detector membership
are preserved in the run directories.

## 14. Direction-class findings

Both fixed AXIS/FACE_DIAGONAL/BODY_DIAGONAL summaries and source-polar-aware
summaries are reported. Raw class differences remain confounded by polar angle
and small diagonal radius differences.

## 15. Runtime and memory comparison

- X: 46.355 s, 257.218 ms/update, 11.142 GiB peak heap.
- Y: 46.324 s, 257.109 ms/update, 11.142 GiB peak heap.
- Z: 46.338 s, 257.141 ms/update, 11.110 GiB peak heap.

## 16. Limitations

Only three orthogonal lattice-axis orientations and 2 common safe
cycles are tested. This cannot establish continuous rotational invariance.
Integer-rounded shell radii and cubic propagation remain possible corrections.

## 17. Conclusions

Within the two common reflection-safe cycles, the X/Y/Z source-relative
acceleration-magnitude patterns are numerically consistent under the exact
cyclic axis permutation: maximum CV
1.10275e-14.
The lattice-fixed acceleration-magnitude comparison is likewise numerically
consistent across source axes, while direction-class structure remains in the
shared cubic detector geometry. No orientation-specific implementation
asymmetry is resolved at this precision. This establishes consistency only
under the three tested discrete lattice-axis rotations; it does not establish
continuous rotational invariance, spherical isotropy, inverse-square behavior,
gravitational equivalence, or a monopole field.
