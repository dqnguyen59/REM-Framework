# Identity XYZ orbit

The Identity editor now has **XYZ orbit (coupled rotations)**. Uncheck it to retain the existing separate-axis oscillators. The choice, amplitudes, phase increments and start angles are persisted with the Identity and cloned with it. Legacy files default to separate-axis mode.

For a quick centered source, use Tools → Isotropy → Probe measurements → **Start centered Identity XYZ orbit (existing force mechanics)**. This creates or reuses the named source, cancels prescribed excitation, enables Identity excitation and starts simulation. It leaves other Identities and the pressure/transport configuration intact. Source width applies only to prescribed excitation. The shared amplitude field supplies Identity force amplitude when starting this mode; changing it requires clicking the Identity start button again.

## Equation and mechanics

Every update advances the existing degree-valued phases by their configured increments. The output is

`f = diag(Ax, Ay, Az) Rz(phaseZ + offsetZ) Ry(phaseY + offsetY) Rx(phaseX + offsetX) (0,1,0)`.

Equal amplitudes give constant force-vector magnitude. Independent Active and waveform controls apply only to separate-axis mode; orbit mode uses all three rotations. The existing start-angle and increment controls configure those rotations. Sampling the displayed trail does not advance the Identity.

This output uses the unchanged `Identity.generateExcitation()` → `Occupancy.updateOscillation()` → `Node.addForce()` path. Node v0.1 includes this vector in its existing neighbor-imbalance acceleration calculation. The source node evolves through the existing mechanics; it is not prescribed to follow the displayed orbit. The display shows force direction. The Occupancy remains on its node unless existing transport moves it. No force, pressure, transport or damping rule was changed.

The earlier prescribed-position preview is still available. A visible orbit alone did not establish how far its disturbance propagated. This addition verifies propagation through the Identity force path numerically rather than inferring it from the trajectory display.

## Saved live demonstration

`measurements/diamond-shells/presets/identity-xyz-orbit-60cube.ini` contains the saved 60³ setup, center index (30,30,30), Identity force amplitude 20 on all axes, and periods 64/256/1024 updates. Increments are 5.625°, 1.40625° and 0.3515625° per update. Constant Frequency is 0.9746794344808963 and Electric Field is 1. These coupling settings were selected by the preceding prescribed-source comparison, not optimized separately for the Identity source. The latest saved experiment contains the same configuration; load it and run. The GUI was observed around 55–60 FPS with this view and continuous excitation. Frame rate varies with visibility and machine load. Reflections are included.

The previous live configuration was saved before restarting to `measurements/diamond-shells/presets/before-identity-orbit-60cube.ini`.

## Validation

`mvn package` passed with IdentityOrbitTest, DiamondRotatingOrbitTest, DiamondSphericalRuntimeTest, AppSettingsStartupTest and FrameIsotropyLayoutTest: 20 tests, no failures or errors. Tests cover the complete orbit trajectory, constant magnitude, reset, cloning, persistence and legacy default mode. The real Node worker integration test uses a 60³ diamond lattice, k=0.5, gamma=1, amplitude=1 and 128 updates. It applies excitation through Occupancy and checks finite positions and nonzero displacement away from the source.

| Receiver index offset along X | Peak displacement |
|---:|---:|
| 4 | 0.04420345551402841 |
| 8 | 0.01907773755598737 |
| 12 | 0.011918729007193579 |

Offsets are lattice indices, not shell bond-length distances. Peaks cover the entire run, without excluding reflections. Raw output: `measurements/diamond-shells/identity-orbit-propagation.csv`. This confirms disturbance reaches those receivers under the tested configuration; it does not establish isotropy or maximum range.

## Files changed in this addition

- `src/main/java/remframework/opengl/model/entity/Identity.java`
- `src/main/java/remframework/gui/view/frames/FrameIdentities.java`
- `src/main/java/remframework/gui/view/frames/FrameIsotropy.java`
- `src/test/java/remframework/opengl/model/entity/IdentityOrbitTest.java` (new)
- `src/test/java/remframework/lattice/DiamondSphericalRuntimeTest.java`
- This document.
- `measurements/diamond-shells/identity-orbit-propagation.csv`
- `measurements/diamond-shells/presets/before-identity-orbit-60cube.ini`
- `measurements/diamond-shells/presets/identity-xyz-orbit-60cube.ini`
- `measurements/diamond-shells/presets/last-experiment.ini`
- `measurements/diamond-shells/presets/experiment-1793998282735.ini`
- `measurements/diamond-shells/presets/experiment-1793998687548.ini`
- `docs/guided-spherical-test/10-identity-xyz-orbit.png`

Normal application autosave also updated the shared orbit amplitude in `qfs.ini`. Existing unrelated working-tree changes were retained.

![Identity orbit running on 60³](guided-spherical-test/10-identity-xyz-orbit.png)
