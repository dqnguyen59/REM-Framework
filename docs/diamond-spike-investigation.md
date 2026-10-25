# Diamond shell arrival spike investigation

## Fractional threshold arrival implemented

Fractional-update interpolation for the original absolute-threshold arrival detector
is now implemented alongside the excitation-axis selector. The original confirming
integer update is retained. Consecutive samples bracketing the threshold give
`estimatedArrival = previousUpdate + (threshold-before)/(after-before)`.
Missing brackets remain unavailable; a lobe below threshold is not assigned a crossing.
The directional-spread calculation and per-probe arrival chart/table use the estimated
times. Shell exports retain integer min/max/difference/mean columns and append
`estimated_min_update`, `estimated_max_update`, `estimated_mean_update` and the explicit
`spread_time_basis`. Diagnostic trace exports append both estimated arrival times.
The optional shell arrival-time view and its linear fit still use confirming integer
updates. Cycle completion arrivals retain their separate whole-update qualification.

## Fix for absolute-threshold lobe jumps

The default trace-enabled shell view now plots **half-peak timing range in updates**.
For each probe it locates the rising crossing at 50% of that probe's fitted peak
amplitude, inside the already selected common lobe window. Between consecutive
samples `(t, low)` and `(t+1, high)` the crossing estimate is
`t + (0.5*peakAmplitude - low)/(high-low)`. Exactly one rising crossing is required;
otherwise it is unavailable. Multiplying a lobe by a constant changes its amplitude
and crossing level by the same factor, so amplitude scaling alone cannot cause
this detector to skip to a later lobe. Shape differences still affect its timing.

Raw samples, original thresholds and physics remain unchanged. **Show raw
absolute-threshold report** restores the original shell chart and table. The
matched-peak export adds `half_peak_update` and `half_peak_status`. This change
corrects the comparison of waveform timing; it does not remove physical amplitude
differences or redefine a first detectable wavefront. Common-lobe selection still
uses the earliest original threshold arrival and is not comparable across shells
as a propagation speed unless the same wave feature is established across them.

Reanalysis of `shell-traces-1792849095275.csv` gives these gradient timing ranges:

| Radius | Original threshold range | Half-peak range (updates) |
| --- | --- | --- |
| 104 | 18 | 2.170 |
| 128 | 21 | 1.046 |
| 152 | 21 | 0.756 |
| 192 | 22 | 0.866 |
| 208 | 26 | 1.040 |

An independent production-sampler control uses an exact, unit-amplitude radial
sinusoid at wavelength 32 on both FCC sublattices, evaluated at the same 64 XY
directions and five radii. The interpolated amplitude is the modulus of
`sum(weight * exp(i*2*pi*(sourceRadius-targetRadius)/32))`. Maximum/minimum
amplitude ratios are 1.0114 to 1.0126 (about 1.1-1.3%), much smaller than the
1.29-1.46 ratios of the measured gradient peaks at those radii. This rules out
ordinary interpolation of that smooth scalar wave as a complete explanation.
It does not rule out vector cancellation between sublattices, different actual
spatial spectra, nonlinear mechanics, or fixed-surface effects. No correction
factor is applied to amplitudes and their ultimate source remains unresolved.

Tests additionally cover amplitude scaling by a factor of ten without a lobe
jump, known timing delays, ambiguity, the interpolation control and reanalysis
of the supplied recording. The live GUI has not been exercised by automation.

## Matched-peak diagnostic added

The all-shell report now shows matched peak timing and amplitude after a trace-enabled
recording finishes. **Export matched-peak report CSV** writes per-probe peak updates,
amplitudes, common window bounds and validity status. Existing threshold arrivals,
spread calculations and mechanical updates are unchanged.

For each shell and signal, the analysis averages the recorded detector norms and
selects the first strict local maximum at or after the earliest threshold arrival.
The neighboring minima of that mean waveform define a common lobe window. Each
probe must have exactly one strict interior maximum in that window, higher than
both boundary values. Missing, multiple or boundary peaks remain unavailable.
This conservative rule can reject otherwise useful recordings and makes no claim
to identify a wavefront or a universal mode. Changing the threshold may select
a different reference lobe.

For samples `a, b, c` around a strict maximum at update `n`, a three-point parabolic
fit estimates the peak at `n + delta`, where
`delta = (a-c) / (2*(a-2*b+c))`, and amplitude
`b - (a-c)*delta/4`. This estimates fractional-update peak timing; it is not a
measurement at finer simulation timesteps. The reported timing range is the
largest minus smallest estimated peak update, only when all probes are valid.

Applied to the supplied `shell-traces-1792846440504.csv`, the same production Java
analysis gives:

| Radius (bonds) | Gradient threshold-arrival range | Matched-peak range (updates) |
| --- | --- | --- |
| 104 | 18 | 1.425 |
| 128 | 21 | 0.650 |
| 152 | 21 | 0.637 |
| 192 | 22 | 0.849 |
| 208 | 26 | 1.021 |

The first two gradient shells are flagged ambiguous. At radius 128, matched peak
amplitudes range from 9.10434e-6 to 1.27413e-5 around the original 1e-5 threshold.
The amplitude variation is preserved as a separate result and its origin remains
unresolved. These results diagnose threshold/lobe selection, not isotropy.

Validation: packaged build and 19 tests passed, including synthetic amplitude-only
differences, a known 1.25-update delay, ambiguity and missing samples, existing
arrival/report/layout regressions, and the explicitly requested audit of the real
recording. No new simulation run or live GUI interaction was performed.

## Evidence so far

The 24-shell report `measurements/diamond-shells/shell-report-1792842897161.csv`
has 64 completed directions per shell, radii 64 through 202 in steps of 6,
position threshold 1e-4 and gradient threshold 1e-5. The associated run screenshots
show wavelength 32, initial peak amplitude 50 and a 1000-update XY slab run with
Z excitation. These run settings are screenshot evidence; the summary CSV does
not store them.

Gradient spikes at radii 82, 112, 136, 160, 184 and 202 have arrival ranges of
13, 17, 21, 21, 20 and 23 updates respectively. Whole-update rounding alone cannot
account for those ranges. Several peaks are separated by 24 bonds, but the
sampled spacing does not establish a physical period.

## Detector audit and controlled reproduction

The detector compares the Euclidean norms `|x(n)-x(0)|` and `|g(n)-g(0)|` with
their thresholds. The gradient is the weighted sum of source-node measured
gradient vectors before taking the norm. The first qualifying whole update is
retained. Spatial interpolation, amplitude changes and propagation differences
can all change this crossing time. No fractional crossing-time interpolation is
currently applied.

`ProbeShellArrivalReportTest.smallAmplitudeDifferenceCanSwitchArrivalToALaterLobe`
uses two signals with identical peak times at updates 3 and 20. The first peak
has amplitudes 1.01 and 0.99 against threshold 1; the second peak is above threshold
for both. The resulting recorded arrivals are 3 and 20: a 17-update difference
without a shift in either peak time. This demonstrates a possible measurement
mechanism, not its occurrence in the actual lattice run.

Previously each shell stopped sampling after all arrivals. The new optional
diagnostic captures the exact baseline-relative position and gradient norms
through the end of the run, independently of the shell's completion status.
Arrival results and mechanics retain their existing behavior.

## Next measurement

1. Restart with the rebuilt application. In the all-shell report area enable
   **Record diagnostic traces (next run)**.
2. Repeat the wavelength-32 run with the same 24 shells, amplitude, thresholds,
   dimensions and boundaries. Change no other settings for the first recording.
3. After the run pauses, use **Export diagnostic traces CSV**, and also export
   the summary report. Files are saved under `measurements/diamond-shells`.
4. Compare early- and late-arriving directions at radius 136, then neighboring
   radii 130 and 142. Plot gradient change versus update with the threshold marked.
   Inspect the earlier peak that precedes the late crossing, not only the crossing.
5. Re-evaluate the same exported traces at 0.5x, 1x and 2x threshold. A jump that
   moves or disappears with threshold supports crossing/lobe selection. If whole
   waveforms are shifted, investigate propagation and boundary effects. A later
   controlled change of probe placement/interpolation can separate sampling effects.

CSV includes probe ID, shell, requested radius, update, both detector norms,
thresholds, original arrival updates and a truncation flag. Probe IDs identify the
generated shell and direction. Values use 17 significant digits. The trace is
bounded to 4096 updates per probe and approximately 96 MB of primitive data across
all probes (plus object overhead), including the added signed-Z channels. For 1536 probes the capacity is 1302 updates
per probe, enough for the current 1000-update run. Longer recordings or larger
probe sets can truncate; `trace_truncated=true` explicitly identifies this.
Duplicate calls on the same sample count are not recorded twice. Baseline is the
measurement's existing start state; no synthetic update-zero row is exported.

Norm traces allow threshold reanalysis but do not retain individual source-node
vectors. Attribution specifically to spatial interpolation would require an
additional comparison against source-node traces or a controlled sampler test.
The existing summary files cannot reconstruct discarded waveform samples.

## Validation

`mvn package` with `ProbeShellArrivalReportTest`, `ProbeArrivalMeasurementTest`
and `FrameIsotropyLayoutTest` passed (16 tests). Tests cover lobe switching,
continued recording after arrival, baseline subtraction, duplicate sample calls,
bounded recording, truncation and stopping at finish, plus existing detector and
layout regressions. No actual-lattice trace run has been collected in this change.
