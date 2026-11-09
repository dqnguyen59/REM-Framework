# Cycle-completed directional arrival statistics

For each shell and signal, retain each probe's interpolated first threshold arrival t_i,
relative to source start at update zero. Release the shell summary only after every
probe completes the configured whole spherical-cycle window. No mechanical equations change.

- Mean arrival: mu = sum(t_i) / N.
- Range: max(t_i) - min(t_i).
- Population standard deviation: sqrt(sum((t_i - mu)^2) / N).
- Timing spread (%): 100 * range / mu.

This is a directional average, gated by cycle completion. It is not a received-cycle
phase detector or an average of multiple matched received cycles. Increasing the cycle
count delays publication but does not change the stored first threshold arrivals.
The source period and common window end are not added to the denominator.
The existing apparent speed-spread chart remains separately labeled and unchanged.

UI: Tools -> Isotropy -> Probe measurements -> Propagation arrival, below Whole
spherical-cycle measurements. Export cycle-completed arrival statistics CSV.

Files changed for this request:
- src/main/java/remframework/opengl/model/ProbeShellArrivalReport.java
- src/main/java/remframework/gui/view/frames/FrameIsotropy.java
- src/test/java/remframework/opengl/model/ProbeShellArrivalReportTest.java
- docs/diamond-cycle-arrival-statistics.md (this document)

Validation: Maven package passed with 19 tests: ProbeShellArrivalReportTest (10),
ProbeSweepMeasurementTest (2), FrameIsotropyLayoutTest (6), DiamondSphericalRuntimeTest (1).
The new test verifies incomplete windows do not publish results and fractional
arrivals 0.5 and 2.5 retain their range 2, mean 1.5, standard deviation 1.

Live saved-preset run: 120 x 120 x 120, width 4, amplitude 1, period 64,
2 turns/half-cycle, 1 measurement cycle, thresholds 1e-9, radii 12/16/20.
All 78 probes completed at update 129 before cutoff 190.
Export: measurements/diamond-shells/shell-cycle-arrivals-1792966260016.csv.
Screenshot: docs/guided-spherical-test/08-cycle-arrival-statistics.png.
These two generated files are also added for this request. qfs.ini is autosaved by the app.

| Radius | Position mean | Position range | Position timing spread | Gradient mean | Gradient range | Gradient timing spread |
|---:|---:|---:|---:|---:|---:|---:|
| 12 | 9.8877 | 4.6458 | 46.9854% | 8.0378 | 4.9603 | 61.7118% |
| 16 | 23.9343 | 7.2651 | 30.3544% | 22.1970 | 7.6903 | 34.6457% |
| 20 | 39.1241 | 6.3791 | 16.3048% | 37.6247 | 7.1240 | 18.9345% |

Relative spread decreases here, while absolute ranges do not consistently decrease.
This is not evidence that cycle averaging removed the earlier threshold spikes.
