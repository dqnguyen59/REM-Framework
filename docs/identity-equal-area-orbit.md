# Equal-area Identity orbit

Select **XYZ orbit**, then **Equal-area spherical orbit** in the Identity editor.
The option is saved and cloned with the Identity; older presets retain coupled rotations.
The Identity uses the existing force, occupancy and transport mechanics.

For the phases in turns, u = fractional_part(Z), longitude = X + Y (converted to radians),
height z = 1 - 4 |u - 1/2| and r = sqrt(1-z*z).
The unit excitation is (r cos(longitude), r sin(longitude), z), multiplied componentwise
by the existing amplitude vector. Equal amplitudes preserve constant magnitude.
Uniform longitude and height correspond to equal spherical areas, dA = d(longitude) dz.
The continuous path reverses its height sweep at the poles. Angular speed is not constant.

The tested preset uses periods 32/512/8192, equal amplitudes 20, a 120-cube lattice,
30 absorber layers, four shells at 16/20/24/28 bonds, one warm-up and one measured cycle.
Discrete mean squared unit components are approximately 0.333332/0.333335/0.333333;
the mean vector magnitude is approximately 0.0001034. Cross moments are near zero.
An 8-height by 16-longitude equal-area histogram has approximately 64 samples per cell.
These finite-resolution checks do not prove uniformity at every angular scale.
Arbitrary user increments may cause poor coverage; this is not guaranteed for every setting.

The measured response may still depend on source frequency content, interpolation,
lattice mechanics, the 0.5-bond source offset and residual reflections. Balanced source
moments do not force zero response spread. Compare the saved preset against the previous
run without changing thresholds or accepting a run merely because its spread is low.

## Completed application test

The desktop application completed 16,384 updates: 8,192 warm-up updates followed by
8,192 measured updates. All 104 probes completed the measurement. Export:
`measurements/diamond-shells/identity-orbit-spread-1794053922725.csv` and its
`-probes.csv` companion. The summary records `equal_area_orbit=true`.
The baseline is `identity-orbit-spread-1794034247021.csv` in the same directory.

| Radius (bonds) | Previous RMS spread (%) | Equal-area RMS spread (%) | Change (percentage points) |
| --- | ---: | ---: | ---: |
| 16 | 34.5632 | 35.8508 | +1.2876 |
| 20 | 32.6500 | 34.5617 | +1.9117 |
| 24 | 34.9292 | 38.1034 | +3.1742 |
| 28 | 34.0331 | 37.7222 | +3.6892 |

Each spread was independently recomputed from its 26 per-probe RMS values using
100 * (maximum - minimum) / mean. Each probe has 8,192 samples and complete status.
These are whole-cycle gradient RMS amplitude spreads, not propagation-speed spreads.
Balanced source coverage did not improve this response metric in this experiment.
The new trajectory changes frequency content as well as directional coverage:
Z varies slowly while X/Y rotate rapidly. Frequency-resolved source/response measurements
are needed to distinguish that effect from lattice, sampling and boundary effects.
This run does not identify a unique cause or establish an isotropy verdict.

Validation: Maven package succeeded. IdentityOrbitTest (3), IdentityOrbitSpreadRunTest (1),
ProbeSweepMeasurementTest (3), and DiamondAbsorberTest (2) passed, nine tests total.

Implementation files changed for this experiment:
- `src/main/java/remframework/opengl/model/entity/Identity.java`
- `src/main/java/remframework/gui/view/frames/FrameIdentities.java`
- `src/main/java/remframework/gui/view/frames/FrameIsotropy.java`
- `src/main/java/remframework/opengl/model/IdentityOrbitSpreadRun.java`
- `src/test/java/remframework/opengl/model/entity/IdentityOrbitTest.java`
- `measurements/diamond-shells/presets/identity-spread-120cube-equal-area.ini`
- `docs/identity-equal-area-orbit.md`

For application launch, `presets/last-experiment.ini` was backed up as
`presets/before-equal-area-launch.ini`, then replaced with the equal-area preset.
