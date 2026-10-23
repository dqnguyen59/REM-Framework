# XY slab configuration sweep — 2026-10-23

No tested configuration established isotropy within the fixed 1% tolerance. The lowest balanced single-setting result was wavelength 24, radius 48 and position/gradient detection thresholds both 1e-8: position-speed spread 3.097190%, gradient-speed spread 3.030651%. It reproduced exactly on 200x200x4 and 240x240x4, and on a fresh repeat of 240x240x4. It is a reproducible diagnostic minimum, not a converged physical result or a global optimum.

## Fixed experiment

- Experimental diamond Node v0.1 mechanics, k=0.5, gamma=1.
- XY-only probes: eight axes/diagonal directions, both FCC sublattices, fixed-surface samples included.
- Depth Z=4; existing fixed top/bottom surfaces retained. This is not bulk 3D isotropy.
- X-polarized planar radial packet, amplitude 0.001; sigma=lambda/2, support R=1.5*lambda. Source profile and mechanics were not altered to reduce spread.
- Three shells: radii 48, 60 and 72; 200-update recording. All arrival samples are before the graph-causal lateral-boundary cutoff. The nearest cutoff was 206 updates on side200/lambda24, and 275 on side240/lambda28. Top/bottom surface effects are intentionally included.
- Thresholds: 1e-12, 3e-12, 1e-11, 3e-11, 1e-10, 3e-10, 1e-9, 3e-9, 1e-8, 3e-8, 1e-7, 3e-7, 1e-6, 3e-6.
- Apparent speed spread = 100*(max(1/t)-min(1/t))/mean(1/t), separately for displacement and raw mechanical imbalance. An incomplete eight-direction set is NaN, never zero.

## Search and validation

Screened side200 with wavelengths 4, 8, 12, 16, 20, 24. Refined side240 with wavelengths 20, 22, 24, 26, 28. This evaluates 462 wavelength/radius/threshold combinations across the two domains (378 distinct combinations ignoring domain). A further 42 settings were repeated for side240/lambda24. The full 400x400 target was not run: memory admission had already rejected that size; this report does not claim validation at 400x400.

The balanced ranking minimizes the larger of position and gradient spreads, requiring all eight arrivals in both fields. The best complete single-setting cases on the larger domain were:

| Wavelength | Radius | Both thresholds | Position spread | Gradient spread |
|---:|---:|---:|---:|---:|
| 24 | 48 | 1e-8 | 3.097190% | 3.030651% |
| 28 | 60 | 3e-11 | 3.453350% | 3.179163% |
| 20 | 48 | 3e-10 | 2.996477% | 3.691559% |
| 26 | 48 | 1e-9 | 3.765207% | 1.673640% |

These minima do not remain small under nearby settings. For lambda24/radius48, thresholds 3e-9 -> position/gradient 3.722/3.008%; 1e-8 -> 3.097/3.031%; 3e-8 -> 13.740/10.089%. A stricter ranking evaluates both fields across three consecutive tested thresholds and two adjacent radii, requiring all six cells complete. The best such worst-case score was 10.616593%, lambda24, radii48/60, thresholds3e-11/1e-10/3e-10. No robust 1% candidate was found.

An undisturbed side240 control ran 200 updates: zero position or gradient detections in all 42 shell/threshold cells, including 1e-12. This checks baseline noise over this window; it does not prove accuracy of first-arrival timing. Repeating the driven lambda24 run gave identical counts and spreads for every tested cell. Integer arrival quantization, source/receiver distance and threshold sensitivity remain unresolved; the threshold-sensitive minimum should not be advertised as a physical isotropy success.

## Recommended next use

For a reproducible diagnostic benchmark, use 240x240x4, XY-only, lambda24, amplitude0.001, radius48 and threshold1e-8 for both fields. Keep tolerance1%. Use radius60 and neighboring thresholds as checks; expect the result to remain above tolerance. Do not increase the tolerance to turn this experiment into a pass. The next measurement improvement is a validated waveform-feature/two-radius delay estimator, with temporal and distance checks, rather than further selection of a favorable first-detection threshold.

No mechanics, pressure, transport, source, tolerance or application defaults were changed for this sweep. The running app was closed for RAM and restarted afterward. User-entered UI settings are not automatically restored by the headless tests; the benchmark inputs above must be entered explicitly.

## Files produced/changed

- src/test/java/remframework/lattice/DiamondXYConfigurationSweepTest.java — new reusable runtime experiment; configurable side/wavelengths/output and quiet control.
- diamond-xy-configuration-expanded.csv — definitive side200 screening data.
- diamond-xy-configuration-confirmation.csv — side240 refinement data.
- diamond-xy-configuration-repeat.csv — fresh side240/lambda24 repeat.
- diamond-xy-quiet-control.csv — undisturbed side240 control.
- diamond-xy-robustness-ranking.csv — complete six-cell robustness ranking.
- docs/research/diamond-xy-configuration-sweep-2026-10-23.md — this report.
- docs/research/diamond-lattice-rem-compatibility.md — report link.

Build and experiment invocations used Maven test with DiamondXYConfigurationSweepTest and the existing dependency cache. Reproduction properties: xySweepSide (default200), xySweepWavelengths (default4,8,12,16,20,24), xySweepOutput, xySweepQuiet (defaultfalse). All completed sweep/refinement/repeat/control test invocations passed; a physical 1% spread is deliberately not asserted as a software test condition.
