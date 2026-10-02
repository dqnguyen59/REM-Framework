# Experiment 3E — Angular Evolution with Radius

## 1. Objective

Measure whether normalized cubic angular structure weakens, persists, or
strengthens from exact radius 13 through 39. This is not a radial-law test.

## 2. Relationship to Experiment 3D

Experiment 3D measured five cubic orbits at radius 25. Experiment 3E follows
the same 78 exact base rays at three collinear integer scales.

## 3. Exact collinear detector construction

The base shell x²+y²+z²=169 contains 78 directions in orbit counts 6, 24,
and 48. Integer scaling produces 234 unique detectors at radii 13, 26, and 39.

## 4. Simulation configuration

Three direct X/Y/Z runs used a 211³ lattice centered at (105,105,105), amplitude
200, 20-degree phase increments, period 18, and 180 updates.

## 5. Lattice-sizing diagnostics

The 181³ bound was rejected analytically because radius 39 would not retain
two safe cycles. The 211³ X run served as the sizing diagnostic and was
retained as valid data.

## 6. Arrival and reflection-safe intervals

- r=13 arrival range: 18–36
- r=26 arrival range: 36–54
- r=39 arrival range: 54–72
- Global common cycles: 5, 6
- Global update range: 73–108
- Selected detector/source/cycle rows: 1404

## 7. Source-relative rotational consistency

All source-relative mappings matched at every radius. Median and maximum
rotational CV values are recorded separately by radius and metric.

## 8. Composite metric definitions

Pooled source RMS is primary. Mean-of-source-RMS and equal-sample true pooled
RMS are retained separately; their differences are not interpreted as radial
physics.

## 9. Absolute response by radius

Absolute shell and orbit means are preserved for a future radial experiment.
They are not fitted to or described as an inverse-square law here.

## 10. Normalized angular response

Each positive shell response is divided by its own all-direction mean.

## 11. Directional CV and spread evolution

| Radius | Shell mean | Directional CV | Spread | a4 | I4 R² |
|---:|---:|---:|---:|---:|---:|
| 13 | 0.0200432266 | 1.716197% | 6.487106% | -0.248225684 | 0.999381002 |
| 26 | 0.00393453054 | 2.257404% | 8.530855% | -0.3264917 | 0.99930417 |
| 39 | 0.000787927711 | 3.366518% | 12.745044% | -0.487023747 | 0.999793431 |

The acceleration directional-CV trend is **increasing**. The relative CV change
from r=13 to r=39 is 96.161523%.

## 12. Within-orbit cubic symmetry

Within-orbit CV remains at numerical scale. This tests cubic-orbit
consistency, not spherical isotropy.

## 13. Between-orbit evolution

- r=13: ORBIT_13_0_0=0.940599744, ORBIT_12_5_0=1.00390847, ORBIT_12_4_3=1.0054708
- r=26: ORBIT_13_0_0=0.921862984, ORBIT_12_5_0=1.00519119, ORBIT_12_4_3=1.00717153
- r=39: ORBIT_13_0_0=0.883536518, ORBIT_12_5_0=1.00714195, ORBIT_12_4_3=1.01098696

Pairwise orbit differences are reported for all three radii.

## 14. Cubic-invariant evolution

The fitted a4 coefficient changes from
-0.248225684 through -0.3264917 to
-0.487023747.

The I4 model is descriptive only. Cross-radius Pearson/Spearman correlations,
RMS differences, worst directions, and a4 evolution are in the CSV evidence.

## 15. Cross-radius pattern correlation

Matched base-direction correlations and normalized pattern differences are
reported for 13–26, 26–39, and 13–39.

## 16. Same-direction attenuation ratios

Every base ray retains Q26/Q13, Q39/Q26, Q39/Q13, log differences, and
monotonicity. These are descriptive and are not a radial-law fit.

## 17. Opposite-direction parity

Every radius contains 39 opposite pairs. Even residuals are used for
non-negative metrics and odd residuals for signed radial metrics.

## 18. Per-cycle stability

Each selected cycle retains shell CV, spread, orbit structure, and the
descriptive I4 fit, allowing cycle sensitivity to be inspected.

## 19. Metric-definition sensitivity

Pooled-source RMS, mean-of-source-RMS, and true equal-sample pooled RMS remain
distinct in the evidence.

## 20. Runtime and memory

- X: 101.670 s, peak 24.681 GiB.
- Y: 101.967 s, peak 24.806 GiB.
- Z: 101.685 s, peak 24.798 GiB.

## 21. Limitations

Only three radii, three cubic orbits, three discrete source orientations, and
two common cycles are measured. Absolute attenuation is descriptive evidence
for a future experiment, not an inverse-square result.

## 22. Conclusions

The normalized acceleration structure strengthens over the sampled radii:
directional CV rises from {float(primary[13]['directionalCV']):.6%} to
{float(primary[39]['directionalCV']):.6%}, while the normalized pattern remains
highly correlated. The evidence characterizes sampled cubic angular evolution.
It does not establish spherical isotropy, continuous
rotational invariance, gravitational equivalence, monopole behavior, or a
fundamental I4 equation.

## 23. Recommended next experiment

Repeat selected exact collinear rays on a larger reflection-safe lattice with
more cycles and additional exact integer shells before attempting a radial law.
