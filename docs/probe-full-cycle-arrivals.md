# Per-probe full-cycle completion arrivals

The all-shell report includes **Show full-cycle completion arrivals**, enabled by
default, and **Export full-cycle arrival report CSV**. A new run is required for
the online report. Trace recording is optional; this detector uses constant memory
per probe and continues after the original threshold measurement completes. The
experiment's existing run limit still applies and can leave cycles incomplete.

Each probe independently measures two absolute signed signals: local Z velocity
and the latest per-update Z bond-distance imbalance. It does not use the cycle-summary
arrow vector, shell means, neighboring probe histories, or retrospective peak matching.

## Definition and qualification

One cycle is negative-to-positive, then positive-to-negative, then the next
negative-to-positive crossing. The **Full-cycle noise deadband (next run)** slider
sets a positive absolute deadband, default 1e-9. The same numeric value applies
separately in each signal's native units. Both signs must exceed this deadband;
subthreshold chatter does not count. Changes to this setting apply to the next
measurement start, not a running detector.

For opposite-sign samples `(tA,sA)` and `(tB,sB)`, the interpolated crossing is
`tA + (tB-tA)*(-sA)/(sB-sA)`. A single exact-zero sample is assigned its sampled
update. A run of several exact zeros is ambiguous and is not given an invented
crossing time. Missing or nonfinite updates invalidate the detector. It does not
skip an ambiguous candidate and silently label a later cycle as the first.

The report records:

- `cycle_start_update`: estimated first rising crossing.
- `cycle_middle_update`: estimated falling crossing.
- `cycle_end_update`: estimated second rising crossing.
- `period_updates`: cycle end minus cycle start.
- `cycle_completion_arrival_update`: the actual whole update when the final rising
  crossing is confirmed above the deadband. This can be later than the interpolated
  cycle end, especially with a slow or weak signal.
- The frozen deadband and qualification status for every probe and signal.

Once complete, these values are frozen. Before completion the GUI shows each
shell's completed/total probe count. A min/max arrival range and mean period are
shown only when every probe in that shell and signal has a completed cycle.
Export preserves partial and invalid results instead of excluding those probes.
The report is written to `measurements/diamond-shells/shell-cycles-<timestamp>.csv`.

## Interpretation

This is the **first qualified complete local cycle**, not first detectable response.
It includes the wait for a suitable starting phase and one measured period. Different
probes can still qualify different physical oscillations if their early amplitudes
or waveform shapes differ. Start times, end times and periods are therefore retained
alongside completion arrival; equality of completion times alone does not establish
equal propagation speeds. The detector does not label the lattice isotropic.

No dynamics, physical gradients, velocities, original arrival thresholds, or cycle
histories are modified. Clearing or restarting a measurement clears all independent
cycle detectors. Finishing a run freezes unfinished detectors as `incomplete`.

## Validation

Tests cover a known three-crossing cycle, confirmation delay beyond the zero
crossing, exact period and a known fractional phase delay, incomplete half cycles,
noise chatter, ambiguous zero plateaus, missing updates, duplicate sample calls,
frozen results, and report export after threshold arrival without trace capture.
An optional audit feeds an existing signed recording through the production detector
and checks that all completed periods are positive and completion times stay inside
the recording. Existing arrival, reversal, peak and layout tests are included in the
packaged build. Native GUI operation has not been exercised by automation.
