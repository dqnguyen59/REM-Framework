# Experiment 3D — Exact-Radius Angular Structure

## 1. Experiment title

Experiment 3D measures the three-source statistical composite on an exact
integer radius-25 shell.

## 2. Objective

At fixed actual radius, determine whether composite passive lattice response
depends on direction.

## 3. Relationship to Experiments 3B and 3C

Experiment 3B validated cyclic X/Y/Z source rotations. Experiment 3C found
strong directionality on a radius-mismatched shell. Experiment 3D directly
remeasures all three sources on an exact shell; radius-20 values are not reused.

## 4. Exact-radius shell construction

All signed coordinate permutations of five primitive triples produce 150
unique offsets satisfying `x²+y²+z²=625`, hence actual radius exactly 25.

## 5. Detector-orbit validation

Orbit counts are 6, 24, 24, 48, and 48. Every offset is unique, every opposite
exists in the same orbit, and every orbit is closed under signed permutations.

## 6. Simulation configuration

Three directly measured 181³ runs use source (90,90,90), amplitude magnitude
200, period 18, 20° phase increment, 180 updates, passive detectors, and
unchanged solver, transport, boundary, and absorption behavior.

## 7. Reflection-safe cycle intersection

Meaningful arrival updates span 36–54. The all-run,
all-detector intersection is cycles 4–5, updates
55–90. It contains
900 selected detector/source/cycle rows.

## 8. Source-relative rotational consistency

Acceleration-magnitude rotational CV has median 5.4254e-15 and
maximum 2.46289e-14; unmatched canonical directions: zero.

## 9. Composite metric definitions

Pooled source RMS `sqrt(mean(RMS_source²))` is primary. Mean-of-source-RMS is
retained; true pooled-sample RMS is equal to the reported pooled RMS because
all sources and selected cycles contribute equal sample counts.

## 10. All-direction angular variation

Pooled acceleration RMS has CV 0.0766752, spread
0.251642, minimum
`XP25_YZ00_ZZ00`, and maximum `XN16_YN12_ZN15`.
Mean-of-RMS directional CV is 0.0766752.

## 11. Opposite-direction symmetry

There are 75 pairs. Acceleration even residual median is
5.70991e-15 and maximum 2.50828e-14.

## 12. Within-orbit cubic symmetry

- ORBIT_25_0_0: mean 0.00345206206, within-orbit CV 6.14836e-15.
- ORBIT_24_7_0: mean 0.00364200543, within-orbit CV 7.40055e-15.
- ORBIT_20_15_0: mean 0.00417107214, within-orbit CV 6.31248e-15.
- ORBIT_20_12_9: mean 0.00425332714, within-orbit CV 5.8336e-15.
- ORBIT_16_15_12: mean 0.00450679643, within-orbit CV 4.76791e-15.

## 13. Between-orbit variation

The largest acceleration orbit-mean difference is
0.265047 between
`ORBIT_25_0_0` and `ORBIT_16_15_12`.

## 14. Cubic-invariant analysis

For acceleration RMS, Pearson correlation with I4 is
-0.998418, Spearman correlation is
-0.962447, and the descriptive
`a0+a4*I4` fit has R² 0.996838. This is not a field law.

## 15. Per-cycle consistency

Each common cycle has its own directional CV, spread, orbit means, and parity
evidence in the CSV outputs and charts.

## 16. Metric-definition sensitivity

Pooled RMS is never below mean-of-RMS. Their detector-level differences are
reported without conflating pooled samples with arithmetic RMS averaging.

## 17. Limitations

Only five cubic orbits, three discrete source orientations, and
2 safe cycles are sampled. The composite is statistical rather
than one physical source.

## 18. Conclusions

Exact-radius angular variation remains strongly directional: pooled
acceleration RMS has CV 0.0766752, reduced from
Experiment 3C's 0.108352 but still well above the 5% descriptive threshold.
Members within every cubic orbit are numerically equivalent, while
inequivalent orbit means differ by as much as
0.265047. The cubic invariant I4
describes most of this sampled structure (R² 0.996838),
but is only a descriptive angular model. The evidence supports discrete cubic
symmetry with between-orbit angular structure, not spherical isotropy,
continuous rotational invariance, inverse-square behavior, gravitational
equivalence, or a monopole field.

## 19. Recommended next experiment

Repeat selected exact-radius integer shells at multiple radii to test whether
the measured orbit differences persist, without fitting a radial law first.
