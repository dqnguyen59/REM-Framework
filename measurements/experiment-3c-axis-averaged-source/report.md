# Experiment 3C — Composite Axis-Averaged Source Symmetry

## 1. Experiment title

Experiment 3C analyzes an equal statistical combination of the existing
X-, Y-, and Z-polarized source responses.

## 2. Objective

The objective is to determine whether equal orientation averaging reduces
source-polarization dependence across the 26 sampled directions.

## 3. Composite versus physical source

This is an analysis-layer ensemble average of three independent simulations.
It is not an in-phase XYZ oscillator, breathing source, or newly implemented
physical source mechanism.

## 4. Input Experiment 3B validation

All three 161³ datasets passed configuration, detector geometry, source phase,
row uniqueness, finite-value, and cycle-safety validation. Each contributes
the same 26 detector labels and cycles 3–4.

## 5. Composite metric definitions

The primary non-negative definition is the arithmetic mean of per-run,
per-detector cycle RMS values. Pooled RMS is
`sqrt(mean(RMS_axis,cycle²))`; orientation-level RMS is calculated from the
three per-axis aggregated values. Signed means remain signed and also receive
separate RMS-across-orientations and mean-absolute summaries. The largest
pooled-minus-mean difference for acceleration magnitude is
0.000342669.

## 6. Common safe-cycle range

Only complete reflection-safe cycles 3–4, updates 37–72, are used. Results are
reported by cycle and aggregated across both cycles.

## 7. Composite directional response

Raw composite acceleration-magnitude RMS has directional CV
0.108352 and spread
0.257431, classified as
**strongly directional**. The minimum is
`NX_R020` and maximum is
`PXNY_R020`.

## 8. Opposite-direction symmetry

Acceleration-magnitude even-parity residuals have median
2.11084e-15 and
maximum 7.07753e-15.
Signed metrics use odd residuals rather than automatic absolute values.

## 9. Cubic symmetry

Within-class cubic groups and their worst detectors are reported in
`composite-cubic-groups.csv`. Opposite symmetry, cyclic rotation consistency,
cubic class symmetry, and sampled-direction spherical isotropy remain
distinct claims.

## 10. Direction-class comparison

- AXIS mean: 0.00536830133
- FACE_DIAGONAL mean:
  0.00698543079
- BODY_DIAGONAL mean:
  0.00591146409

These raw classes occur at different actual radii and cannot alone establish
cubic anisotropy.

## 11. Actual-radius confounding

Strict `1e-9` radius grouping yields three separate radius groups: axis,
face-diagonal, and body-diagonal. No cross-class radius-matched subset exists,
so a direct radius-controlled class comparison is unavailable on this shell.

## 12. Radius-normalization sensitivity

For acceleration-magnitude RMS:

- n=1: CV 0.0977999,
  spread 0.244737
- n=2: CV 0.0911026,
  spread 0.232196

These are exploratory shell equalizations, not radial-law fits. No exponent is
treated as physically preferred.

## 13. Per-cycle consistency

Cycle 3 and cycle 4 composites are retained separately in
`composite-cycle-summary.csv` and plotted by detector.

## 14. Limitations

Only 26 high-symmetry directions, one requested shell, three discrete source
orientations, and two safe cycles are available. Actual radii differ by class,
and polarization averaging is statistical rather than physical.

## 15. Conclusions

The equal X/Y/Z ensemble average remains **strongly directional**
across the sampled directions using raw acceleration-magnitude RMS. Its
direction-class structure must be interpreted together with the radius
normalization sensitivity and the lack of cross-class radius-matched samples.
This result does not show that one Identity emits isotropically and does not
establish continuous rotational invariance, inverse-square behavior,
gravitational equivalence, or a monopole field.

## 16. Recommended next experiment

Use detector coordinates designed to match actual radii across direction
classes, or repeat multiple nearby shells so angular structure and radial
attenuation can be separated without assuming a decay exponent.
