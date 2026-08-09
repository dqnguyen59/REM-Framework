# Gravity Test 2D radial-falloff analysis amendment (v2)

## Status and scope

This is an analysis-only amendment to the existing passive-node run. The simulation was not rerun and no physics or detector-sampling code was changed.

The raw run passed collection integrity: 112 detectors × 960 updates = 107,520 rows, with complete update sequences, finite values, exact axis geometry, and zero coordinate-rounding error.

The originally requested common 10–400 analysis window failed: no positive cycle count is simultaneously post-arrival and reflection-safe at all radii. Therefore no radial-law conclusion is permitted for the complete 10–400 set.

The corrected primary analysis is the post-run, reflection-safe 10–180 subset. All four directions at its 17 radii support exactly 30 complete, contiguous post-arrival cycles ending before their detector-specific reflection estimate. This gives 68 accepted detectors and 2,040 cycle-metric rows. Because this range was selected after the run, it is explicitly an analysis amendment rather than a new simulation result.

Radii 200–400 remain in the raw file and detector summary. They are excluded from primary fits because one or more of their requested 30-cycle windows is not reflection-safe.

## Arrival and reflection safety

Meaningful arrival is the end of the first complete 18-update source cycle with finite, non-zero cycle-derived gradient. Selection begins with the following cycle. The conservative reflected arrival uses the minimum source → first non-unity absorption interface → detector path and a direct-neighbor bound of at most one edge per update.

The accepted 30th-cycle safety margin ranges from 63 to 575 updates. All 68 accepted gates pass.

| Radius | Arrival | Safe cycles | Reflection estimate | 30-cycle margin | Status |
|---:|---:|---:|---:|---:|---|
| 10 | 18 | 52 | 1133 | 575 | accepted |
| 20 | 36 | 51 | 1123 | 547 | accepted |
| 30 | 54 | 50 | 1113 | 519 | accepted |
| 40 | 72 | 49 | 1103 | 491 | accepted |
| 50 | 90 | 48 | 1093 | 463 | accepted |
| 60 | 108 | 47 | 1083 | 435 | accepted |
| 70 | 126 | 46 | 1073 | 407 | accepted |
| 80 | 144 | 45 | 1063 | 379 | accepted |
| 90 | 162 | 44 | 1053 | 351 | accepted |
| 100 | 198 | 42 | 1043 | 305 | accepted |
| 110 | 216 | 41 | 1033 | 277 | accepted |
| 120 | 234 | 40 | 1023 | 249 | accepted |
| 130 | 252 | 39 | 1013 | 221 | accepted |
| 140 | 270 | 38 | 1003 | 193 | accepted |
| 150 | 306 | 36 | 993 | 147 | accepted |
| 160 | 324 | 35 | 983 | 119 | accepted |
| 180 | 360 | 33 | 963 | 63 | accepted |
| 200 | 414 | 29 | 943 | -11 | excluded |
| 220 | 450 | 26 | 923 | -67 | excluded |
| 240 | 504 | 22 | 903 | -141 | excluded |
| 260 | 540 | 19 | 883 | -197 | excluded |
| 280 | 594 | 14 | 863 | -271 | excluded |
| 300 | 630 | 11 | 843 | -327 | excluded |
| 320 | 684 | 7 | 823 | -401 | excluded |
| 340 | 720 | 4 | 803 | -457 | excluded |
| 360 | 774 | 0 | 783 | -531 | excluded |
| 380 | 828 | 0 | 763 | -605 | excluded |
| 400 | 864 | 0 | 743 | -661 | excluded |

## Directional symmetry

Directional comparisons are performed before four-axis averaging. The v2 axis-average CSV records PX/NX, PY/NY, and X/Y absolute and relative differences, parsed-float/bitwise equality classifications, outward-radial sign parity, arrival-cycle differences, and reflection-margin differences for every accepted radius.

- **rawGradient:** maximum opposite-direction relative difference 1.11518e-13; maximum X/Y relative difference 5.56616e-14; X/Y equality classes: bitwise-identical, equal-within-1e-12-relative-tolerance.
- **cycleDerivedGradient:** maximum opposite-direction relative difference 6.46739e-14; maximum X/Y relative difference 6.39024e-14; X/Y equality classes: bitwise-identical, equal-within-1e-12-relative-tolerance.
- **acceleration:** maximum opposite-direction relative difference 0; maximum X/Y relative difference 0; X/Y equality classes: bitwise-identical.

## Radial measurements

| Radius | Raw-gradient RMS | Cycle-derived-gradient RMS | Acceleration RMS |
|---:|---:|---:|---:|
| 10 | 1.02192 | 0.337034 | 0 |
| 20 | 0.551323 | 0.0820318 | 0 |
| 30 | 0.177225 | 0.0252077 | 0 |
| 40 | 0.0183768 | 0.00708541 | 0 |
| 50 | 0.00637778 | 0.00141689 | 0 |
| 60 | 0.00173084 | 0.00166469 | 0 |
| 70 | 0.00228099 | 0.00108264 | 0 |
| 80 | 0.00152042 | 0.000457418 | 0 |
| 90 | 0.000297528 | 0.000494978 | 0 |
| 100 | 0.000602315 | 0.00033484 | 0 |
| 110 | 0.000608133 | 0.000240942 | 0 |
| 120 | 0.000139271 | 0.000209133 | 0 |
| 130 | 0.000278627 | 0.00014228 | 0 |
| 140 | 0.000282214 | 0.000119043 | 0 |
| 150 | 5.82937e-05 | 0.000119105 | 0 |
| 160 | 0.000168734 | 7.97427e-05 | 0 |
| 180 | 3.92396e-05 | 7.85989e-05 | 0 |

## Power-law fits and cutoff sensitivity

Fits use finite, strictly positive four-axis-average responses. Free-power-law uncertainty is the covariance-derived 95% interval. Model RMSE is evaluated in measurement space; R² is evaluated in log space where predictions are positive. AIC and BIC use measurement-space residuals with the fitted parameter count.

| Quantity | Minimum radius | Free exponent n | 95% interval | log R² | RMSE |
|---|---:|---:|---:|---:|---:|
| rawGradient | 10 | 1.5978 | [1.27675, 1.91886] | -0.50825 | 0.0629496 |
| rawGradient | 20 | 3.3728 | [2.97186, 3.77374] | 0.646895 | 0.0139195 |
| rawGradient | 40 | 4.66825 | [4.16633, 5.17018] | 0.684534 | 0.000465707 |
| rawGradient | 60 | 2.4232 | [1.37905, 3.46735] | 0.646006 | 0.000341641 |
| rawGradient | 80 | 4.12019 | [2.01625, 6.22414] | 0.688156 | 0.000207333 |
| rawGradient | 100 | 3.68229 | [1.68231, 5.68227] | 0.657128 | 0.000102253 |
| rawGradient | 120 | 1.94503 | [-2.09901, 5.98907] | 0.245517 | 8.29614e-05 |
| cycleDerivedGradient | 10 | 2.24228 | [2.10769, 2.37687] | 0.564117 | 0.00419462 |
| cycleDerivedGradient | 20 | 3.21932 | [3.01629, 3.42234] | 0.958284 | 0.00114154 |
| cycleDerivedGradient | 40 | 4.6538 | [3.54853, 5.75907] | -0.275909 | 0.000387665 |
| cycleDerivedGradient | 60 | 3.25018 | [2.86403, 3.63632] | 0.943408 | 6.48474e-05 |
| cycleDerivedGradient | 80 | 2.32813 | [1.78795, 2.86831] | 0.950016 | 4.03661e-05 |
| cycleDerivedGradient | 100 | 2.80471 | [2.454, 3.15542] | 0.950969 | 1.171e-05 |
| cycleDerivedGradient | 120 | 2.51298 | [1.6692, 3.35677] | 0.894177 | 1.28828e-05 |
| acceleration | — | — | — | — | — |

## Alternative-model comparison

| Quantity | Lowest-BIC 10–180 model | BIC | Runner-up | ΔBIC |
|---|---|---:|---|---:|
| rawGradient | exponential | -113.005 | power-law-with-offset | 5.48007 |
| cycleDerivedGradient | power-law-with-offset | -244.68 | exponential | 24.4388 |
| acceleration | no finite-positive fit | — | — | — |

Lowest-BIC model by minimum-radius cutoff:

| Quantity | rmin | Winner | BIC |
|---|---:|---|---:|
| rawGradient | 10 | exponential | -113.005 |
| rawGradient | 20 | exponential | -147.315 |
| rawGradient | 40 | power-law-with-offset | -210.621 |
| rawGradient | 60 | exponential | -188.635 |
| rawGradient | 80 | free-power-law | -165.019 |
| rawGradient | 100 | exponential | -142.851 |
| rawGradient | 120 | inverse-square | -110.972 |
| cycleDerivedGradient | 10 | power-law-with-offset | -244.68 |
| cycleDerivedGradient | 20 | exponential | -240.146 |
| cycleDerivedGradient | 40 | power-law-with-offset | -226.299 |
| cycleDerivedGradient | 60 | free-power-law | -226.474 |
| cycleDerivedGradient | 80 | exponential | -200.507 |
| cycleDerivedGradient | 100 | free-power-law | -177.522 |
| cycleDerivedGradient | 120 | free-power-law | -131.532 |
| acceleration | 10 | no finite-positive fit | — |
| acceleration | 20 | no finite-positive fit | — |
| acceleration | 40 | no finite-positive fit | — |
| acceleration | 60 | no finite-positive fit | — |
| acceleration | 80 | no finite-positive fit | — |
| acceleration | 100 | no finite-positive fit | — |
| acceleration | 120 | no finite-positive fit | — |

Model rankings and parameter stability for every requested minimum radius are retained in the v2 model-fit CSV. Changes in the winning model across cutoffs count against model stability. A lowest BIC by itself is not treated as proof of a physical law.

## Local exponents and residual behavior

- **rawGradient:** adjacent local n ranges from -16.4682 to 22.8599; variability and the centered three-point series are retained in the local-exponent CSV and charts.
- **cycleDerivedGradient:** adjacent local n ranges from -0.884003 to 7.21316; variability and the centered three-point series are retained in the local-exponent CSV and charts.
- **acceleration:** no local logarithmic exponent is defined because the accepted radial RMS is not strictly positive.

Selected 10–180 model residual diagnostics:

| Quantity | Model | Lag-1 autocorrelation | Sign changes | Peak-to-trough / mean response |
|---|---|---:|---:|---:|
| rawGradient | exponential | 0.0184738 | 5 | 1.52272 |
| cycleDerivedGradient | power-law-with-offset | -0.0528411 | 4 | 0.10426 |
| acceleration | no fit | — | — | — |

The model-residual chart preserves signed measurement-space residuals. Any oscillatory structure is descriptive only; 17 accepted radii are insufficient to establish its physical origin or distinguish lattice dispersion from source-cycle sampling effects.

## Scientific decisions

- **rawGradient: C.** Radial attenuation is present, but the strongly oscillatory local exponents, cutoff-sensitive global exponents, and competing model rankings do not demonstrate a stable power-law exponent or two stable radial regimes.
- **cycleDerivedGradient: C.** Radial attenuation is present, but the strongly oscillatory local exponents, cutoff-sensitive global exponents, and competing model rankings do not demonstrate a stable power-law exponent or two stable radial regimes.
- **acceleration: D.** The accepted in-plane radial response is zero, so no radial attenuation law can be fitted.

This experiment measures passive node-level lattice position-derived gradients and node acceleration. It does not measure Occupancy pressure, transport momentum, Identity acceleration, attraction, or orbital behavior. No conclusion applies to the excluded 200–400 range, and no result establishes a universal gravitational law.

## Chart validation

All charts were generated with Matplotlib using separate figures, 180 dpi PNG output, white backgrounds, major grids, tight layout, and no subplots. Pillow verified and successfully reopened every PNG, including an independently copied `/tmp` file. Standards-compliant SVG companions were saved from the same figures.

| PNG | Bytes | Dimensions | Mode | Pillow | OS `file` | SVG bytes |
|---|---:|---:|---|---|---|---:|
| `raw-gradient-rms-v2.png` | 82991 | 2142×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/raw-gradient-rms-v2.png: PNG image data, 2142 x 1241, 8-bit/color RGBA, non-interlaced` | 55285 |
| `cycle-gradient-rms-v2.png` | 86533 | 2141×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/cycle-gradient-rms-v2.png: PNG image data, 2141 x 1241, 8-bit/color RGBA, non-interlaced` | 58405 |
| `raw-gradient-loglog-v2.png` | 103424 | 2141×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/raw-gradient-loglog-v2.png: PNG image data, 2141 x 1241, 8-bit/color RGBA, non-interlaced` | 54026 |
| `cycle-gradient-loglog-v2.png` | 107475 | 2141×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/cycle-gradient-loglog-v2.png: PNG image data, 2141 x 1241, 8-bit/color RGBA, non-interlaced` | 54949 |
| `raw-gradient-local-exponent-v2.png` | 174820 | 2140×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/raw-gradient-local-exponent-v2.png: PNG image data, 2140 x 1241, 8-bit/color RGBA, non-interlaced` | 48833 |
| `cycle-gradient-local-exponent-v2.png` | 196690 | 2140×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/cycle-gradient-local-exponent-v2.png: PNG image data, 2140 x 1241, 8-bit/color RGBA, non-interlaced` | 50631 |
| `directional-symmetry-v2.png` | 176447 | 2142×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/directional-symmetry-v2.png: PNG image data, 2142 x 1241, 8-bit/color RGBA, non-interlaced` | 64584 |
| `arrival-update-v2.png` | 104108 | 2141×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/arrival-update-v2.png: PNG image data, 2141 x 1241, 8-bit/color RGBA, non-interlaced` | 58160 |
| `reflection-margin-v2.png` | 118219 | 2140×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/reflection-margin-v2.png: PNG image data, 2140 x 1241, 8-bit/color RGBA, non-interlaced` | 62192 |
| `model-residuals-v2.png` | 107309 | 2141×1241 | RGBA | PASS/PASS | `measurements/charts/test-2d-v2/model-residuals-v2.png: PNG image data, 2141 x 1241, 8-bit/color RGBA, non-interlaced` | 42592 |

Every PNG exceeds 20 KB, is at least 1600×900, has non-zero pixel extrema, contains measured data, has the standard eight-byte PNG signature, and is not an empty-data message. Detailed results are in `../charts/test-2d-v2/png-validation.txt`.

## Limitations

- The 10–180 primary range was selected after inspecting reflection safety and must be treated as a post-run amendment.
- Source-locked arrivals are quantized to 18-update cycle boundaries.
- Only lattice-axis directions are measured.
- The full 10–400 protocol remains invalid for a common 30-cycle fit.
- Model uncertainty does not include systematic boundary, discretization, or post-selection uncertainty.
