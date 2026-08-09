# Gravity Test 2D — Passive-detector radial falloff

## Scientific objective

Measure passive node-level axis response versus radius without using receiver Identities, Occupancy pressure, transport momentum, Identity acceleration, attraction, or orbital motion.

## Configuration and collection integrity

- Node v0.1; 1201×1201×1 lattice; source `(600,600,0)`; Z amplitude 200; 20° increment; observed period 18 updates.
- 112 unique exact-axis detectors at 28 radii; zero coordinate rounding error; all outside the absorption region.
- Raw rows: 107,520 = 112×960; every detector has updates 1..960.
- Duplicate detector/update rows: 0; NaN/infinity/signed-negative-zero anomalies: 0; geometry inconsistencies: 0.
- Empty-lattice baseline remains exact zero.

## Arrival and cycle rule

Meaningful arrival is the end of the first complete source cycle with finite non-zero cycle-derived gradient. A candidate window starts with the following complete cycle. Arrivals are quantized to 18-update boundaries, so effective and interval speeds are diagnostic rather than exact propagation constants.

## Reflection gate: FAIL for a common positive cycle count

The first non-unity absorption interfaces are x/y=29 and 1171. Each reflected path is independently minimized and converted with the causal speed bound ≤1 edge/update.

- Requested common cycles: 30
- Maximum common reflection-safe cycles across all 112 detectors: **0**
- Detectors failing a 30-cycle gate: 44
- PX/NX/PY/NY radius 400: meaningful arrival update 864, reflection estimate 743, safe post-arrival cycles 0.
- Because the required common count is zero, no response values were admitted to axis averages, logarithmic fits, local exponents, alternative-model comparison, or residual inference.

| Detector | Arrival | Arrival cycle | Available cycles | Safe cycles | Reflection | Candidate 30th end | Margin | Gate |
|---|---:|---:|---:|---:|---:|---:|---:|:---:|
| NX_R010 | 18 | 1 | 52 | 52 | 1133 | 558 | 575 | PASS |
| NX_R020 | 36 | 2 | 51 | 51 | 1123 | 576 | 547 | PASS |
| NX_R030 | 54 | 3 | 50 | 50 | 1113 | 594 | 519 | PASS |
| NX_R040 | 72 | 4 | 49 | 49 | 1103 | 612 | 491 | PASS |
| NX_R050 | 90 | 5 | 48 | 48 | 1093 | 630 | 463 | PASS |
| NX_R060 | 108 | 6 | 47 | 47 | 1083 | 648 | 435 | PASS |
| NX_R070 | 126 | 7 | 46 | 46 | 1073 | 666 | 407 | PASS |
| NX_R080 | 144 | 8 | 45 | 45 | 1063 | 684 | 379 | PASS |
| NX_R090 | 162 | 9 | 44 | 44 | 1053 | 702 | 351 | PASS |
| NX_R100 | 198 | 11 | 42 | 42 | 1043 | 738 | 305 | PASS |
| NX_R110 | 216 | 12 | 41 | 41 | 1033 | 756 | 277 | PASS |
| NX_R120 | 234 | 13 | 40 | 40 | 1023 | 774 | 249 | PASS |
| NX_R130 | 252 | 14 | 39 | 39 | 1013 | 792 | 221 | PASS |
| NX_R140 | 270 | 15 | 38 | 38 | 1003 | 810 | 193 | PASS |
| NX_R150 | 306 | 17 | 36 | 36 | 993 | 846 | 147 | PASS |
| NX_R160 | 324 | 18 | 35 | 35 | 983 | 864 | 119 | PASS |
| NX_R180 | 360 | 20 | 33 | 33 | 963 | 900 | 63 | PASS |
| NX_R200 | 414 | 23 | 30 | 29 | 943 | 954 | -11 | FAIL |
| NX_R220 | 450 | 25 | 28 | 26 | 923 | N/A | N/A | FAIL |
| NX_R240 | 504 | 28 | 25 | 22 | 903 | N/A | N/A | FAIL |
| NX_R260 | 540 | 30 | 23 | 19 | 883 | N/A | N/A | FAIL |
| NX_R280 | 594 | 33 | 20 | 14 | 863 | N/A | N/A | FAIL |
| NX_R300 | 630 | 35 | 18 | 11 | 843 | N/A | N/A | FAIL |
| NX_R320 | 684 | 38 | 15 | 7 | 823 | N/A | N/A | FAIL |
| NX_R340 | 720 | 40 | 13 | 4 | 803 | N/A | N/A | FAIL |
| NX_R360 | 774 | 43 | 10 | 0 | 783 | N/A | N/A | FAIL |
| NX_R380 | 828 | 46 | 7 | 0 | 763 | N/A | N/A | FAIL |
| NX_R400 | 864 | 48 | 5 | 0 | 743 | N/A | N/A | FAIL |
| NY_R010 | 18 | 1 | 52 | 52 | 1133 | 558 | 575 | PASS |
| NY_R020 | 36 | 2 | 51 | 51 | 1123 | 576 | 547 | PASS |
| NY_R030 | 54 | 3 | 50 | 50 | 1113 | 594 | 519 | PASS |
| NY_R040 | 72 | 4 | 49 | 49 | 1103 | 612 | 491 | PASS |
| NY_R050 | 90 | 5 | 48 | 48 | 1093 | 630 | 463 | PASS |
| NY_R060 | 108 | 6 | 47 | 47 | 1083 | 648 | 435 | PASS |
| NY_R070 | 126 | 7 | 46 | 46 | 1073 | 666 | 407 | PASS |
| NY_R080 | 144 | 8 | 45 | 45 | 1063 | 684 | 379 | PASS |
| NY_R090 | 162 | 9 | 44 | 44 | 1053 | 702 | 351 | PASS |
| NY_R100 | 198 | 11 | 42 | 42 | 1043 | 738 | 305 | PASS |
| NY_R110 | 216 | 12 | 41 | 41 | 1033 | 756 | 277 | PASS |
| NY_R120 | 234 | 13 | 40 | 40 | 1023 | 774 | 249 | PASS |
| NY_R130 | 252 | 14 | 39 | 39 | 1013 | 792 | 221 | PASS |
| NY_R140 | 270 | 15 | 38 | 38 | 1003 | 810 | 193 | PASS |
| NY_R150 | 306 | 17 | 36 | 36 | 993 | 846 | 147 | PASS |
| NY_R160 | 324 | 18 | 35 | 35 | 983 | 864 | 119 | PASS |
| NY_R180 | 360 | 20 | 33 | 33 | 963 | 900 | 63 | PASS |
| NY_R200 | 414 | 23 | 30 | 29 | 943 | 954 | -11 | FAIL |
| NY_R220 | 450 | 25 | 28 | 26 | 923 | N/A | N/A | FAIL |
| NY_R240 | 504 | 28 | 25 | 22 | 903 | N/A | N/A | FAIL |
| NY_R260 | 540 | 30 | 23 | 19 | 883 | N/A | N/A | FAIL |
| NY_R280 | 594 | 33 | 20 | 14 | 863 | N/A | N/A | FAIL |
| NY_R300 | 630 | 35 | 18 | 11 | 843 | N/A | N/A | FAIL |
| NY_R320 | 684 | 38 | 15 | 7 | 823 | N/A | N/A | FAIL |
| NY_R340 | 720 | 40 | 13 | 4 | 803 | N/A | N/A | FAIL |
| NY_R360 | 774 | 43 | 10 | 0 | 783 | N/A | N/A | FAIL |
| NY_R380 | 828 | 46 | 7 | 0 | 763 | N/A | N/A | FAIL |
| NY_R400 | 864 | 48 | 5 | 0 | 743 | N/A | N/A | FAIL |
| PX_R010 | 18 | 1 | 52 | 52 | 1133 | 558 | 575 | PASS |
| PX_R020 | 36 | 2 | 51 | 51 | 1123 | 576 | 547 | PASS |
| PX_R030 | 54 | 3 | 50 | 50 | 1113 | 594 | 519 | PASS |
| PX_R040 | 72 | 4 | 49 | 49 | 1103 | 612 | 491 | PASS |
| PX_R050 | 90 | 5 | 48 | 48 | 1093 | 630 | 463 | PASS |
| PX_R060 | 108 | 6 | 47 | 47 | 1083 | 648 | 435 | PASS |
| PX_R070 | 126 | 7 | 46 | 46 | 1073 | 666 | 407 | PASS |
| PX_R080 | 144 | 8 | 45 | 45 | 1063 | 684 | 379 | PASS |
| PX_R090 | 162 | 9 | 44 | 44 | 1053 | 702 | 351 | PASS |
| PX_R100 | 198 | 11 | 42 | 42 | 1043 | 738 | 305 | PASS |
| PX_R110 | 216 | 12 | 41 | 41 | 1033 | 756 | 277 | PASS |
| PX_R120 | 234 | 13 | 40 | 40 | 1023 | 774 | 249 | PASS |
| PX_R130 | 252 | 14 | 39 | 39 | 1013 | 792 | 221 | PASS |
| PX_R140 | 270 | 15 | 38 | 38 | 1003 | 810 | 193 | PASS |
| PX_R150 | 306 | 17 | 36 | 36 | 993 | 846 | 147 | PASS |
| PX_R160 | 324 | 18 | 35 | 35 | 983 | 864 | 119 | PASS |
| PX_R180 | 360 | 20 | 33 | 33 | 963 | 900 | 63 | PASS |
| PX_R200 | 414 | 23 | 30 | 29 | 943 | 954 | -11 | FAIL |
| PX_R220 | 450 | 25 | 28 | 26 | 923 | N/A | N/A | FAIL |
| PX_R240 | 504 | 28 | 25 | 22 | 903 | N/A | N/A | FAIL |
| PX_R260 | 540 | 30 | 23 | 19 | 883 | N/A | N/A | FAIL |
| PX_R280 | 594 | 33 | 20 | 14 | 863 | N/A | N/A | FAIL |
| PX_R300 | 630 | 35 | 18 | 11 | 843 | N/A | N/A | FAIL |
| PX_R320 | 684 | 38 | 15 | 7 | 823 | N/A | N/A | FAIL |
| PX_R340 | 720 | 40 | 13 | 4 | 803 | N/A | N/A | FAIL |
| PX_R360 | 774 | 43 | 10 | 0 | 783 | N/A | N/A | FAIL |
| PX_R380 | 828 | 46 | 7 | 0 | 763 | N/A | N/A | FAIL |
| PX_R400 | 864 | 48 | 5 | 0 | 743 | N/A | N/A | FAIL |
| PY_R010 | 18 | 1 | 52 | 52 | 1133 | 558 | 575 | PASS |
| PY_R020 | 36 | 2 | 51 | 51 | 1123 | 576 | 547 | PASS |
| PY_R030 | 54 | 3 | 50 | 50 | 1113 | 594 | 519 | PASS |
| PY_R040 | 72 | 4 | 49 | 49 | 1103 | 612 | 491 | PASS |
| PY_R050 | 90 | 5 | 48 | 48 | 1093 | 630 | 463 | PASS |
| PY_R060 | 108 | 6 | 47 | 47 | 1083 | 648 | 435 | PASS |
| PY_R070 | 126 | 7 | 46 | 46 | 1073 | 666 | 407 | PASS |
| PY_R080 | 144 | 8 | 45 | 45 | 1063 | 684 | 379 | PASS |
| PY_R090 | 162 | 9 | 44 | 44 | 1053 | 702 | 351 | PASS |
| PY_R100 | 198 | 11 | 42 | 42 | 1043 | 738 | 305 | PASS |
| PY_R110 | 216 | 12 | 41 | 41 | 1033 | 756 | 277 | PASS |
| PY_R120 | 234 | 13 | 40 | 40 | 1023 | 774 | 249 | PASS |
| PY_R130 | 252 | 14 | 39 | 39 | 1013 | 792 | 221 | PASS |
| PY_R140 | 270 | 15 | 38 | 38 | 1003 | 810 | 193 | PASS |
| PY_R150 | 306 | 17 | 36 | 36 | 993 | 846 | 147 | PASS |
| PY_R160 | 324 | 18 | 35 | 35 | 983 | 864 | 119 | PASS |
| PY_R180 | 360 | 20 | 33 | 33 | 963 | 900 | 63 | PASS |
| PY_R200 | 414 | 23 | 30 | 29 | 943 | 954 | -11 | FAIL |
| PY_R220 | 450 | 25 | 28 | 26 | 923 | N/A | N/A | FAIL |
| PY_R240 | 504 | 28 | 25 | 22 | 903 | N/A | N/A | FAIL |
| PY_R260 | 540 | 30 | 23 | 19 | 883 | N/A | N/A | FAIL |
| PY_R280 | 594 | 33 | 20 | 14 | 863 | N/A | N/A | FAIL |
| PY_R300 | 630 | 35 | 18 | 11 | 843 | N/A | N/A | FAIL |
| PY_R320 | 684 | 38 | 15 | 7 | 823 | N/A | N/A | FAIL |
| PY_R340 | 720 | 40 | 13 | 4 | 803 | N/A | N/A | FAIL |
| PY_R360 | 774 | 43 | 10 | 0 | 783 | N/A | N/A | FAIL |
| PY_R380 | 828 | 46 | 7 | 0 | 763 | N/A | N/A | FAIL |
| PY_R400 | 864 | 48 | 5 | 0 | 743 | N/A | N/A | FAIL |

## Directional symmetry

Geometry, arrival cycles, available-cycle counts, and reflection windows are exactly symmetric under +X/−X/+Y/−Y exchange at every radius. Scientific response symmetry is not reported because no positive common accepted response window exists.

## Power laws, local exponents, and model comparison

No fit is scientifically admissible. The model-fit CSV explicitly records all requested ranges/models as excluded. Consequently there is no exponent, confidence interval, RMSE/AIC/BIC ranking, residual autocorrelation, dominant spacing, or near/far regime claim.

## Decision by primary metric

- Raw gradient radial RMS: **D — no reliable radial attenuation law can be determined from this dataset.**
- Cycle-derived gradient radial RMS: **D — no reliable radial attenuation law can be determined from this dataset.**
- In-plane acceleration radial RMS: **D — no reliable radial attenuation law can be determined; the measured in-plane components are zero in this transverse-Z setup.**

## Supported conclusion and limitations

Test 2D collected a complete, passive 960-update raw dataset and verified exact axis geometry and arrival symmetry. It cannot support a reflection-safe radial falloff or power-law conclusion over radii 10–400 because the common admissible cycle count is zero. No inverse-square or other attenuation law is demonstrated.

A further simulation is required for the stated 10–400 objective: the lattice/boundary distance and/or approved reflection protocol must provide a pre-reflection post-arrival window at radius 400. Merely increasing updates would not fix the radius-400 reflection ordering.
