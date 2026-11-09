# Spread measurements with the saved Identity orbit

Tools → Isotropy → Probe measurements now contains **Measure spread with saved Identity orbit**. Select an XYZ orbit Identity, generate and apply passive shell probes, set warm-up and measurement cycles, then click **Restart Identity and measure all applied shells**. The existing Identity supplies force through Occupancy and Node.addForce; no prescribed-position source is substituted. Starting resets node displacement, velocity, acceleration and force to equilibrium and resets the source phases. Source amplitudes, increments, offsets and coupling are retained. One active Identity and disabled pressure/transport are required.

The current user preset has increments 11.25°, 0.703125°, 0.0439453125° per update, giving periods 32, 512 and 8192 updates. The measurement derives the combined period from the actual saved Identity, not the orbit preview controls. One warm-up plus one measurement cycle takes **16384 probe updates**. The former 1000-update packet cap and early threshold-completion stop do not truncate this run. Pause/resume remains available. Measurement cycles and warm-up count are saved in experiment settings. The cycle count is shared with the existing complete-cycle configuration.

## Distinct reported quantities

**Gradient RMS response spread:** Every probe samples the same source-clock window after the configured number of warm-up periods. For each probe, `G = sqrt(mean(|g(t) - g_baseline|²))` over the configured complete periods. For a shell, `spread = 100 × (max(G) - min(G)) / mean(G)`. This is a response-amplitude statistic. It does not measure arrival-time or speed spread. Signed vectors may average to zero while RMS remains nonzero; cancellation is not used to manufacture a zero spread. A zero mean RMS yields an undefined percentage, not a zero percentage. Results remain unavailable until every probe's window is complete.

**First-arrival timing spread:** Existing interpolated first-threshold position and gradient arrival times are retained. Once the shared windows are complete, the report gives their directional minimum, maximum, mean and `100 × (maximum - minimum) / mean`. Missing detections remain incomplete. These are real-valued first-arrival estimates; they are not averages of repeated received-cycle arrivals. Completing more source cycles does not by itself remove a threshold lobe jump.

The UI shows gradient RMS spread against shell radius, plus per-shell tables for both metrics. The CSV export writes a summary and a companion `-probes.csv` containing per-probe RMS, signed vector means, sample counts, window boundaries, first arrivals, source increments and coupling. That companion makes individual outliers identifiable. Shared-window statistics continue even if arrival thresholds are never reached.

## Geometric and boundary interpretation

The saved center-node Identity is 0.5 bond from the geometric center used to generate shells. This offset is displayed and exported. Nominal equal shell radii therefore do not guarantee equal distances from that node; timing differences include this geometry. The existing interpolated first-arrival speed display remains a separate diagnostic.

Use 26 XYZ directions for a three-dimensional comparison. XY probe layouts measure only XY directions. On the 60³ lattice, the saved shell layout (16 bonds, spacing 4, ten shells) extends to 52 bonds, beyond its approximately 34-bond physical half-width. Generate a fitting layout, for example 16/4/4 (16, 20, 24, 28 bonds). Invalid stencils are rejected rather than silently dropping directions.

Reflections are included and explicitly labelled. An 8192-update source orbit on this finite lattice is much longer than a boundary transit; these measurements include the finite-domain response and cannot establish reflection-free propagation or isotropy. The response spread is not expected to become zero merely because the source orbit completes. The source geometry and finite trajectory also remain relevant.

Changing source amplitude, phase configuration, mechanics, probe layout or source clock invalidates an active run. Its incomplete data is retained when possible. Replacing probes clears the report. No mechanical equation, damping, saturation, velocity cap, pressure-transfer or transport rule was changed.

## Validation

`mvn package` and all 34 selected tests passed. The real Node-worker integration test uses 60³, a force-driven Identity with combined period 128, one warm-up and one measured period, and two shells of 26 XYZ probes. It verifies all windows complete with exactly 128 samples each, nonzero response, incomplete arrivals for deliberately unreachable thresholds, CSV columns and invalidation after a source change. It also starts from a detached Occupancy, as happens when loading a saved preset, and verifies the node is rebound before measurement initialization. Unit tests cover the actual saved 8192-update period, common-period calculation, rejection of invalid periods, shared windows, cancellation of signed means with nonzero RMS, missing samples, persistence and previous measurements. The GUI run loaded the user's source, generated and applied 104 XYZ probes at radii 16/20/24/28, and was observed recording past update 2430 of 16384 without the former 1000-update stop. It was left running; this is not a completed result for the full saved-source run.

Changed source files:

- `src/main/java/remframework/opengl/model/IdentityOrbitSpreadRun.java` (new)
- `src/main/java/remframework/opengl/model/ProbeSweepMeasurement.java`
- `src/main/java/remframework/opengl/model/ProbeArrivalMeasurement.java`
- `src/main/java/remframework/opengl/model/ProbeShellArrivalReport.java`
- `src/main/java/remframework/opengl/model/Scene.java`
- `src/main/java/remframework/opengl/controller/Simulator.java`
- `src/main/java/remframework/settings/AppSettings.java`
- `src/main/java/remframework/gui/view/frames/FrameIsotropy.java`

Changed tests:

- `src/test/java/remframework/opengl/model/IdentityOrbitSpreadRunTest.java` (new)
- `src/test/java/remframework/opengl/model/ProbeSweepMeasurementTest.java`
- `src/test/java/remframework/opengl/model/ProbeShellArrivalReportTest.java`
- `src/test/java/remframework/lattice/DiamondSphericalRuntimeTest.java`
- `src/test/java/remframework/settings/AppSettingsStartupTest.java`

Artifacts: this document; `measurements/diamond-shells/presets/before-identity-spread-user-preset.ini`, a backup of the user's saved preset; `measurements/diamond-shells/presets/identity-spread-60cube.ini`, a copy with four fitting shells and one warm-up cycle; `measurements/diamond-shells/presets/last-experiment.ini`, the copy loaded for GUI validation; and `docs/guided-spherical-test/11-identity-spread-running.png`. Normal application autosave updates `qfs.ini`. Build/test logs and the small integration report are in `target/`. Other pre-existing working-tree changes were retained.
