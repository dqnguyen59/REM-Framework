# Passive probe arrow quantities

In Isotropy's probe inspection tab, **Probe arrows > Arrow quantity** selects:

- **Bond-distance imbalance (current)**: the latest completed measurement update.
- **Velocity (current)**: the source node velocity, or weighted source velocities.
- **Bond-distance imbalance (cycle summary)**: the existing arrow behavior, retained
  as the default to preserve the previous display.

The selected probe also shows its arrow vector's local X, Y, Z components and
magnitude. The scale slider and display-only maximum length apply to every mode.
No physical velocities, gradients, arrival thresholds or probe samples are altered.
The selector is session-local.

Current interpolated arrows use `v = sum(w_i * v_i)` or `g = sum(w_i * g_i)`.
The current imbalance is read from the same per-update Occupancy gradient samples
used by the arrival detector. Velocity is read from node velocity fields. The two
streams are not guaranteed to represent the same substep during concurrent rendering.
Cycle summaries have different time semantics and must not be used as instantaneous
velocity or instantaneous imbalance.

The existing Occupancy cycle summary is the componentwise midpoint of positive
and negative extrema over the completed history cycle:
`summary = (componentwiseMax + componentwiseMin) / 2`. It is not a time-average
vector and can change direction differently from the latest measurement.

## Rotation and the arrival spikes

Scene sets arrow orientation directly from each input vector. There is no
frame-to-frame interpolation of Euler angles or gradual-turn animation. A test
checks the transformed mesh endpoint through 722 orientations, including opposite
rotation senses and vectors near a reversal. Each endpoint follows the input.

Pure rotation of a vector leaves its Euclidean norm unchanged. Therefore clockwise
versus counterclockwise turning alone cannot cause an absolute-norm threshold miss.
If source vectors differ in orientation, their weighted sum can partially cancel:
`|sum(w_i*g_i)| <= sum(w_i*|g_i|)` for the nonnegative probe weights. That can change
the measured magnitude and is a possible contributor to the amplitude differences.
The new signed-Z trace columns can test up/down reversal timing, but do not retain
individual source-node vectors needed to confirm that cancellation mechanism.
The cause of the large arrival jumps already established from the earlier norm-only
traces remains threshold/lobe switching.

## Measuring flip synchrony

Enable **Record diagnostic traces (next run)** before a fresh packet run. After
completion, enable **Show signed Z reversal timing**, then use **Export signed Z
reversal report CSV**. Also export the diagnostic traces. Old recordings cannot
recover the signs discarded by their norm-only format.

Trace CSV retains the original columns and appends `velocity_z`, `imbalance_z`
and `cycle_imbalance_z`. These are signed absolute local-Z values, not norms or
changes relative to the start. Current imbalance and velocity are sampled in the
probe measurement stage after completed mechanics updates, independently of
render FPS. Cycle-summary values retain their existing update schedule.

For each shell and signal, a common noise deadband is 1% of the largest absolute
recorded probe signal. A reversal requires opposite signs on either side of this
deadband; tiny sign chatter alone is ignored. The current-signal zero crossing
between `(tA,sA)` and `(tB,sB)` is estimated by
`tA + (tB-tA)*(-sA)/(sB-sA)`. A single exact-zero sample has its recorded update;
multiple consecutive zeros leave the interpolated reversal ambiguous. Cycle
summary reversals are reported at the whole update when the opposite qualified
sign is sampled, without interpolating across its discrete jumps.

Reversals of the shell-mean signed signal define common event windows, whose
edges are halfway between consecutive reference reversals. Each probe must have
exactly one crossing of the same direction within the window. Otherwise it is
flagged missing/ambiguous or multiple. A shell range is reported only when every
probe matches. A cancelling shell mean or signals too weak for the deadband can
make the comparison unavailable. This is conservative event matching, not a claim
that every waveform cycle has been tracked continuously.

The GUI shows the largest within-shell range among fully matched events, the
reference update of that event, and matched/total event counts. The CSV includes
every probe's reversal time, direction, reference time, window, deadband and status,
plus each event's shell minimum/maximum/range. This distinguishes exact sample-level
synchrony from small estimated delays. Fractional-update estimates do not establish
precision finer than the simulation and waveform sampling support. Absolute event
times are retained so different radii can also be compared, without assuming their
event ordinals correspond to the same propagating wave feature.

Recording remains bounded: up to 4096 updates per probe and approximately 96 MB of
primitive trace data across all probes, plus analysis/object overhead. The existing
1280-probe, 1000-update experiment fits. Truncation is explicit in the trace CSV;
the reversal summary refuses truncated recordings rather than claiming full-run
synchrony from partial data. The physical update rule and samples are unchanged.

Validation: raw/current/cycle selection, weighted velocity and imbalance, immutable
source values, arrow endpoint orientation and existing inspector-layout/arrival
tests. Native GUI selection has not been exercised by automation.
