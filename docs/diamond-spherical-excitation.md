# Spherical sweep excitation and whole-period measurements

The previous work was committed and pushed before implementation:

- Commit `7752c0c` on `optimize-memory`.
- Annotated tag `checkpoint/pre-spherical-excitation-20261025`.
- Remote `https://github.com/dqnguyen59/REM-Framework`.

Generated logs and measurement exports were left local. This implementation is
subsequent working-tree work; it was not included in that checkpoint.

## Controls

In Isotropy -> Probe measurements -> Propagation arrival, select **Spherical sweep**
under **Excitation mode**. Existing X/Y/Z axis packets remain available.

- **Source width** controls the compact spherical source envelope, not a temporal wavelength.
- **Sweep radius / peak amplitude** is the maximum displacement radius on actual movable source nodes.
- **Updates per spherical cycle** sets the period, independently of rendering FPS.
- **Spiral turns per half cycle** controls spherical coverage.
- **Complete measurement cycles** sets the number of whole source periods per probe.
- **Maximum recording updates** caps the recording. The conservative boundary cutoff
  may stop a run sooner; incomplete windows remain explicitly incomplete.

The sweep settings persist in `[DiamondProbe]` as `sphericalSweep`, `sweepPeriod`,
`sweepTurns`, and `measurementCycles`. Settings are frozen into a new request.
Defaults are 128 updates, four turns per half cycle, and one measurement cycle.
The period must be even, at least 32 times the turn count, and at most 1,000,000;
turns range from 1 to 64 and measurement cycles from 1 to 1000.

## Source trajectory and mechanics

For source period T and integer update n, let s = (n mod T) / T and
q = 2s for s <= 1/2, otherwise q = 2(1-s). With m spiral turns:

```
z = 1 - 2q
phi = 2*pi*m*q
d(n) = (sqrt(1-z*z)*cos(phi), sqrt(1-z*z)*sin(phi), z)
```

The unit vector sweeps north to south and retraces the spiral south to north.
Linear increments in z represent equal spherical area bands. The reverse half
balances rotation sense. A finite spiral does not cover every circumference or
give exactly equal rotation-axis coverage. Equal XYZ second moments are a useful
coverage check, not proof of those stronger properties. In particular, the local
rotation axis `d(n) cross d(n+1)` can have a nonuniform distribution.

Each movable source node follows `equilibrium + A*w*d(n)`, where

```
support = 1.5 * width
sigma = width / 2
rawWeight(r) = exp(-r*r/(2*sigma*sigma)) * (1-r*r/(support*support))^2
w = rawWeight / maximum rawWeight on actual source nodes
```

The weight is zero outside the support. This driven experiment uses a positive
envelope, rather than the old packet's spatial cosine. Displacement radius remains
constant at each driven node. Velocity is the finite difference between prescribed
positions. The trajectory has sharper turns at the poles, and initialization
starts with a displaced source, so startup and turning transients are possible.

Only source nodes are prescribed, after physics workers finish and before positions
are published. Fixed nodes remain fixed and all other nodes retain the existing
experimental diamond Node integration. The canonical REM Framework cubic Node
equation, pressure and transport rules are unchanged. This source is externally
driven; it is not a freely evolving initial packet or an energy-conserving closed
experiment.

## Measurement completion and vector report

The existing threshold-arrival charts and Z zero-crossing cycle reports retain
their meaning. They do not become measurements of complete spherical sweeps.

For the new measurement, each probe first waits until both existing position and
imbalance norm thresholds have been detected. If this happens at update a, its
window begins at `start = T * ceil(a/T)` and ends at `start + N*T`. It records
exactly the consecutive samples `(start, end]`, where N is the requested cycle
count. These are whole **source-period windows**, not a claim that local waveform
features or individual Z crossings match one-to-one. No steady-state assumption
is verified automatically; a full-period window can still contain transients.

Since detection occurs after update zero, earliest possible window completion is
`(N+1)*T` probe updates. More distant probes can start later. The runtime reports
this minimum alongside its actual cutoff. For example, a 105-update recording
cannot complete the default 128-update sweep window; increasing maximum updates
does not override the boundary cutoff.

The driver continues until all applied probes finish their configured windows,
or until the recording/boundary limit is reached. Completion is checked at the
existing physics-step boundary; the runtime step count can be one update beyond
the last probe sample. Source cycling therefore continues during the travel and
measurement time; N is not a limit on the number of cycles emitted at the source.

For imbalance change `g = measured imbalance - baseline imbalance`, and fixed
source-to-probe unit radius e, the new report exports:

- Signed vector mean: `sum(g) / samples` as XYZ components.
- Vector RMS: `sqrt(mean(g dot g))`.
- Signed radial mean: `mean(g dot e)`.
- Perpendicular RMS: `sqrt(mean(g dot g - (g dot e)^2))`.

Perpendicular here means perpendicular to the source-to-probe radius, not to
instantaneous source velocity. Values are local mechanical imbalance diagnostics;
they are not automatically interpreted as gravity or as an isotropy verdict.

Use **Export spherical-cycle vector report CSV** for `shell-sweeps-*.csv`.
It includes requested/completed cycles, exact window bounds, sample counts,
statistics, status, and frozen source width/amplitude/turn count. Incomplete rows
contain statistics of their available partial samples and must not be compared
as complete windows. Measurements use constant memory and work without diagnostic
trace recording; optional raw traces retain their existing size limit and Z channels.

## Validation

`mvn package` with focused tests covers trajectory closure, radius, equal-area
latitude increments, reversal balance, approximate XYZ second moments, invalid
sampling configurations, source-period window counts, vector/radial/perpendicular
statistics, missing samples, persistence and CSV columns.

A real 48-cube headless test verifies prescribed source motion across axes,
preserved fixed nodes, incomplete cutoff handling, and automatic stopping once
all 26 probe windows complete. Legacy slab and XYZ packet tests remain included.
No live GUI run or emergent physical proof is claimed.

Files changed in this implementation:

- `src/main/java/remframework/geometry/discovery/research/diamond/DiamondSphericalSweep.java`
- `src/main/java/remframework/geometry/discovery/research/diamond/DiamondRuntimeDisturbance.java`
- `src/main/java/remframework/opengl/model/ProbeSweepMeasurement.java`
- `src/main/java/remframework/opengl/model/ProbeArrivalMeasurement.java`
- `src/main/java/remframework/opengl/model/ProbeShellArrivalReport.java`
- `src/main/java/remframework/settings/AppSettings.java`
- `src/main/java/remframework/gui/view/frames/FrameIsotropy.java`
- `src/test/java/remframework/geometry/discovery/research/diamond/DiamondSphericalSweepTest.java`
- `src/test/java/remframework/lattice/DiamondSphericalRuntimeTest.java`
- `src/test/java/remframework/opengl/model/ProbeSweepMeasurementTest.java`
- `src/test/java/remframework/opengl/model/ProbeShellArrivalReportTest.java`
- `src/test/java/remframework/settings/AppSettingsStartupTest.java`
- `docs/diamond-spherical-excitation.md`
