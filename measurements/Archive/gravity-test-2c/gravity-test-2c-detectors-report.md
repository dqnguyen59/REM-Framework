# Gravity Test 2C — Passive Detector Isotropy

## Implementation and physics isolation

Detectors are immutable instrumentation records resolved to fixed lattice indices. Sampling copies node position, equilibrium position, velocity, acceleration, and a pure neighbor-distance gradient read. It creates no Identity or Occupancy and performs no physics write. No physics formula was changed.

Pressure and transport momentum are intentionally absent: those quantities belong to Occupancy, while this analysis is entirely node-level. The earlier active-receiver Test 2C is superseded because its receivers oscillated and generated fields.

## Empty-lattice equilibrium gate

- Rows: 10560 (11 detectors × 960 updates)
- Maximum absolute component: `0.0`
- Maximum vector magnitude: `0.0`
- First non-zero update: `None`
- Result: exact zero
- Derived numerical tolerance: `0.0`

## Collection, arrival, and reflection

- Rows: 28800 (30 detectors × 960 updates)
- Arrival criterion: first 3 consecutive updates with a finite node response strictly above the validated empty-lattice tolerance.
- Increasing-radius arrival ordering: PASS
- Conservative absorption-zone reflection estimate: update `1020`
- Every 30-cycle window ends before the reflection estimate: PASS

| Detector | Arrival | Cycle 30 end | Reflection | Gate |
|---|---:|---:|---:|:---:|
| R020_A00 | 21 | 558 | 1020 | PASS |
| R020_A30 | 27 | 558 | 1020 | PASS |
| R020_A45 | 28 | 558 | 1020 | PASS |
| R020_A60 | 27 | 558 | 1020 | PASS |
| R020_A90 | 21 | 558 | 1020 | PASS |
| R040_A00 | 47 | 576 | 1020 | PASS |
| R040_A30 | 57 | 594 | 1020 | PASS |
| R040_A45 | 57 | 594 | 1020 | PASS |
| R040_A60 | 57 | 594 | 1020 | PASS |
| R040_A90 | 47 | 576 | 1020 | PASS |
| R060_A00 | 81 | 612 | 1020 | PASS |
| R060_A30 | 91 | 630 | 1020 | PASS |
| R060_A45 | 92 | 630 | 1020 | PASS |
| R060_A60 | 91 | 630 | 1020 | PASS |
| R060_A90 | 81 | 612 | 1020 | PASS |
| R080_A00 | 119 | 648 | 1020 | PASS |
| R080_A30 | 128 | 666 | 1020 | PASS |
| R080_A45 | 132 | 666 | 1020 | PASS |
| R080_A60 | 128 | 666 | 1020 | PASS |
| R080_A90 | 119 | 648 | 1020 | PASS |
| R100_A00 | 158 | 684 | 1020 | PASS |
| R100_A30 | 168 | 702 | 1020 | PASS |
| R100_A45 | 171 | 702 | 1020 | PASS |
| R100_A60 | 168 | 702 | 1020 | PASS |
| R100_A90 | 158 | 684 | 1020 | PASS |
| R120_A00 | 198 | 720 | 1020 | PASS |
| R120_A30 | 208 | 738 | 1020 | PASS |
| R120_A45 | 211 | 738 | 1020 | PASS |
| R120_A60 | 208 | 738 | 1020 | PASS |
| R120_A90 | 198 | 720 | 1020 | PASS |

## Isotropy ratios

### rawGradient

| Radius | Q30 | Q45 | Q60 | Q90 | E(r) |
|---:|---:|---:|---:|---:|---:|
| 20 | 0.408832026153 | 0.334445997444 | 0.408832026153 | 1 | 0.665554002556 |
| 40 | 0.517674074607 | 1.55849899 | 0.517674074607 | 1 | 0.558498990002 |
| 60 | 1.04697560388 | 1.64039627784 | 1.04697560388 | 1 | 0.640396277842 |
| 80 | 0.548399380953 | 0.585069285332 | 0.548399380953 | 1 | 0.451600619047 |
| 100 | 0.697830696325 | 0.569181815965 | 0.697830696325 | 1 | 0.430818184035 |
| 120 | 0.764556539114 | 0.610105682648 | 0.764556539114 | 1 | 0.389894317352 |

Radius trend: **decreases**.

### cycleDerivedGradient

| Radius | Q30 | Q45 | Q60 | Q90 | E(r) |
|---:|---:|---:|---:|---:|---:|
| 20 | 0.442914631727 | 0.239225712926 | 0.442914631727 | 1 | 0.760774287074 |
| 40 | 0.723651585724 | 0.758637596662 | 0.723651585724 | 1 | 0.276348414276 |
| 60 | 0.6962132526 | 0.586000012274 | 0.6962132526 | 1 | 0.413999987726 |
| 80 | 0.634259675723 | 0.653394559358 | 0.634259675722 | 1 | 0.365740324278 |
| 100 | 0.668168230688 | 0.547979971549 | 0.668168230688 | 1 | 0.452020028451 |
| 120 | 0.637101110931 | 0.523174865488 | 0.637101110931 | 1 | 0.476825134512 |

Radius trend: **decreases**.

### acceleration

| Radius | Q30 | Q45 | Q60 | Q90 | E(r) |
|---:|---:|---:|---:|---:|---:|
| 20 | nan | nan | nan | nan | nan |
| 40 | nan | nan | nan | nan | nan |
| 60 | nan | nan | nan | nan | nan |
| 80 | nan | nan | nan | nan | nan |
| 100 | nan | nan | nan | nan | nan |
| 120 | nan | nan | nan | nan | nan |

Radius trend: **not assessable**.

Node acceleration has no in-plane radial component in this transverse-Z experiment, so its axis-normalized ratios and isotropy error are `NaN`; this is reported, not replaced with an Occupancy-derived transport acceleration.

## Symmetry and tangential leakage

| Radius | Metric | 0°/90° symmetry | 30°/60° symmetry | max tangential/radial |
|---:|---|---:|---:|---:|
| 20 | rawGradient | 1 | 1 | 0.32855396263 |
| 20 | cycleDerivedGradient | 1 | 1 | 1.10567532981 |
| 20 | acceleration | nan | nan | nan |
| 40 | rawGradient | 1 | 1 | 0.474722970947 |
| 40 | cycleDerivedGradient | 1 | 1 | 0.452690674475 |
| 40 | acceleration | nan | nan | nan |
| 60 | rawGradient | 1 | 1 | 0.292378862946 |
| 60 | cycleDerivedGradient | 1 | 1 | 0.387171920808 |
| 60 | acceleration | nan | nan | nan |
| 80 | rawGradient | 1 | 1 | 0.35271920933 |
| 80 | cycleDerivedGradient | 1 | 1 | 0.335173393557 |
| 80 | acceleration | nan | nan | nan |
| 100 | rawGradient | 1 | 1 | 0.38287717607 |
| 100 | cycleDerivedGradient | 1 | 1 | 0.3656994659 |
| 100 | acceleration | nan | nan | nan |
| 120 | rawGradient | 1 | 1 | 0.292073673086 |
| 120 | cycleDerivedGradient | 1 | 1 | 0.370929632455 |
| 120 | acceleration | nan | nan | nan |

## Convergence assessment and limitations

The tables report measured angular ratios without claiming that visual output proves an emergent physical behavior. Coordinate rounding means requested and actual angles/radii differ at non-axis points. Only 30 complete cycles are analyzed, the lattice is a one-node-thick plane, and conclusions are limited to Node v0.1, this update order, precision, source amplitude, boundary implementation, and the pre-reflection windows above.
