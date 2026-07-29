# Gravity Test 2C — Passive Detector Analysis v2

This analysis supersedes the original summary/report analysis, but retains run-001 as the canonical raw dataset.

## Corrected arrival and cycle rule

Meaningful arrival is the end of the first complete 18-update source cycle whose cycle-derived gradient is finite and non-zero. The 30-cycle window starts with the following source cycle, so every selected cycle is wholly after arrival. This uses the exact-zero baseline, source-cycle structure, persistence over a complete cycle, and no arbitrary magnitude floor.

## Detector-specific reflection gate

The first non-unity absorption layer is index 29/1171. Earliest paths are minimized with the reflected-point construction and converted using the strict direct-neighbor causal bound of at most one lattice edge per update.

| Detector | Arrival | Window end | Boundary | Outbound | Return | Speed bound | Reflection | Margin | Gate |
|---|---:|---:|---|---:|---:|---:|---:|---:|:---:|
| R020_A00 | 36 | 576 | right x=1171 | 571 | 551 | 1 | 1123 | 547 | PASS |
| R020_A30 | 36 | 576 | right x=1171 | 571 | 554.044 | 1 | 1127 | 551 | PASS |
| R020_A45 | 36 | 576 | right x=1171 | 571 | 557.087 | 1 | 1130 | 554 | PASS |
| R020_A60 | 36 | 576 | top y=1171 | 571 | 554.044 | 1 | 1127 | 551 | PASS |
| R020_A90 | 36 | 576 | top y=1171 | 571 | 551 | 1 | 1123 | 547 | PASS |
| R040_A00 | 72 | 612 | right x=1171 | 571 | 531 | 1 | 1103 | 491 | PASS |
| R040_A30 | 72 | 612 | right x=1171 | 571 | 536.181 | 1 | 1109 | 497 | PASS |
| R040_A45 | 72 | 612 | right x=1171 | 571 | 543.352 | 1 | 1116 | 504 | PASS |
| R040_A60 | 72 | 612 | top y=1171 | 571 | 536.181 | 1 | 1109 | 497 | PASS |
| R040_A90 | 72 | 612 | top y=1171 | 571 | 531 | 1 | 1103 | 491 | PASS |
| R060_A00 | 108 | 648 | right x=1171 | 571 | 511 | 1 | 1083 | 435 | PASS |
| R060_A30 | 108 | 648 | right x=1171 | 571 | 519.413 | 1 | 1092 | 444 | PASS |
| R060_A45 | 108 | 648 | right x=1171 | 571 | 529.802 | 1 | 1102 | 454 | PASS |
| R060_A60 | 108 | 648 | top y=1171 | 571 | 519.413 | 1 | 1092 | 444 | PASS |
| R060_A90 | 108 | 648 | top y=1171 | 571 | 511 | 1 | 1083 | 435 | PASS |
| R080_A00 | 144 | 684 | right x=1171 | 571 | 491 | 1 | 1063 | 379 | PASS |
| R080_A30 | 162 | 702 | right x=1171 | 571 | 502.745 | 1 | 1075 | 373 | PASS |
| R080_A45 | 162 | 702 | right x=1171 | 571 | 515.496 | 1 | 1088 | 386 | PASS |
| R080_A60 | 162 | 702 | top y=1171 | 571 | 502.745 | 1 | 1075 | 373 | PASS |
| R080_A90 | 144 | 684 | top y=1171 | 571 | 491 | 1 | 1063 | 379 | PASS |
| R100_A00 | 198 | 738 | right x=1171 | 571 | 471 | 1 | 1043 | 305 | PASS |
| R100_A30 | 198 | 738 | right x=1171 | 571 | 485.184 | 1 | 1058 | 320 | PASS |
| R100_A45 | 198 | 738 | right x=1171 | 571 | 502.351 | 1 | 1075 | 337 | PASS |
| R100_A60 | 198 | 738 | top y=1171 | 571 | 485.184 | 1 | 1058 | 320 | PASS |
| R100_A90 | 198 | 738 | top y=1171 | 571 | 471 | 1 | 1043 | 305 | PASS |
| R120_A00 | 234 | 774 | right x=1171 | 571 | 451 | 1 | 1023 | 249 | PASS |
| R120_A30 | 234 | 774 | right x=1171 | 571 | 468.733 | 1 | 1041 | 267 | PASS |
| R120_A45 | 234 | 774 | right x=1171 | 571 | 489.412 | 1 | 1062 | 288 | PASS |
| R120_A60 | 234 | 774 | top y=1171 | 571 | 468.733 | 1 | 1041 | 267 | PASS |
| R120_A90 | 234 | 774 | top y=1171 | 571 | 451 | 1 | 1023 | 249 | PASS |

## Corrected isotropy ratios

### rawGradient

| r | Q30 | Q45 | Q60 | Q90 | E | corrected Q30 | corrected Q45 | corrected Q60 | corrected Q90 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 0.406721247845 | 0.332553000893 | 0.406721247845 | 1 | 0.667446999107 | 0.379825730464 | 0.316471284925 | 0.379825730464 | 1 |
| 40 | 0.51004863295 | 1.54637688125 | 0.51004863295 | 1 | 0.546376881253 | 0.531220051637 | 1.46655977611 | 0.531220051637 | 1 |
| 60 | 1.05296166489 | 1.6415407989 | 1.05296166489 | 1 | 0.6415407989 | 1.05506579654 | 1.58299317705 | 1.05506579654 | 1 |
| 80 | 0.548419953527 | 0.585100560849 | 0.548419953527 | 1 | 0.451580046473 | 0.544967386591 | 0.594359788099 | 0.544967386591 | 1 |
| 100 | 0.697830963519 | 0.569181805839 | 0.697830963519 | 1 | 0.430818194161 | 0.712119458513 | 0.58304937693 | 0.712119458513 | 1 |
| 120 | 0.764556526445 | 0.61010567168 | 0.764556526445 | 1 | 0.38989432832 | 0.767973757922 | 0.618657629733 | 0.767973757922 | 1 |

Trend: lower at radius 120 than radius 20, but non-monotonic.

### cycleDerivedGradient

| r | Q30 | Q45 | Q60 | Q90 | E | corrected Q30 | corrected Q45 | corrected Q60 | corrected Q90 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 0.443381878052 | 0.239309939163 | 0.443381878052 | 1 | 0.760690060837 | 0.422068908305 | 0.230919420518 | 0.422068908305 | 1 |
| 40 | 0.72148586383 | 0.753324329925 | 0.72148586383 | 1 | 0.27851413617 | 0.741603198475 | 0.726806302908 | 0.741603198475 | 1 |
| 60 | 0.696145968373 | 0.585916723537 | 0.696145968373 | 1 | 0.414083276463 | 0.697675714769 | 0.562980127139 | 0.697675714769 | 1 |
| 80 | 0.634260734229 | 0.653394228398 | 0.634260734229 | 1 | 0.365739265771 | 0.6282041264 | 0.669168156967 | 0.6282041264 | 1 |
| 100 | 0.668168232861 | 0.547979970139 | 0.668168232861 | 1 | 0.452020029861 | 0.672617160552 | 0.552315969079 | 0.672617160552 | 1 |
| 120 | 0.63710111101 | 0.523174865574 | 0.63710111101 | 1 | 0.476825134426 | 0.638015005807 | 0.525520895901 | 0.638015005807 | 1 |

Trend: lower at radius 120 than radius 20, but non-monotonic.

### acceleration

| r | Q30 | Q45 | Q60 | Q90 | E | corrected Q30 | corrected Q45 | corrected Q60 | corrected Q90 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 40 | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 60 | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 80 | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 100 | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 120 | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |

Trend: not assessable.

## Scientific conclusion

The passive lattice response preserves exact cubic X/Y exchange symmetry in this dataset and is anisotropic between axis and off-axis/diagonal directions. The endpoint isotropy error is lower at radius 120 than at radius 20 for raw and cycle-derived gradient, but the six-radius sequence is non-monotonic and does not demonstrate convergence to isotropy. Test 2C does not measure Occupancy pressure, transport momentum, Identity acceleration, attraction, orbital behavior, or prove that gravity is isotropic.
