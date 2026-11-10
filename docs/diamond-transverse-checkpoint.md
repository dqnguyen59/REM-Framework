# Diamond transverse measurement checkpoint — 2026-11-09

This checkpoint on `optimize-memory` collects the experimental diamond work since
`checkpoint/pre-spherical-excitation-20261025`. It includes explicit excitation
axes, spherical and Identity orbit controls, saved experiment presets, complete
pulse and cycle diagnostics, fractional arrival times, transverse surveys over
26 excitation orientations, distance inspection, connected bonds, and selected
shortest-path visualization with configurable visibility.

The experimental diamond topology is separate from the canonical cubic Node 0.1
foundation. Measurements here do not establish universal physical isotropy.

## Latest measurements

The saved 120³, absorption-off period-6 experiment at radius 42 has all 416
receiver pulses complete. Its arrival spread is 0.858572%, while its peak
amplitude spread is 18.627066%. The arrival uses each receiver's own half-peak
crossing, so agreement of arrival times does not imply agreement of amplitudes.

A synthetic radial signal passed
through the current probe interpolation produces an 8.608966% peak spread. Raw
nodes at one exactly shared radius also differ in peak amplitude by 9.681651%.
These use different populations and are not additive contributions to 18.627066%.
The audit identifies interpolation smoothing and direction-dependent finite-scale
mechanical response as issues to investigate separately. It does not change the
integrator or normalize away the amplitude differences.

## Diamond lattice v0.5.0 arrival-timing milestone — 2026-11-10

The completed local run `transverse-1794261672465` used a 240³ experimental
diamond lattice with absorption disabled, 26 excitation orientations, 16
perpendicular receiver rays per orientation, and 16 equal-radius shells from 40
through 85 bond lengths in steps of three. Every orientation recorded all 256
receiver pulses as complete at update 272, before the conservative graph-based
boundary-safe limit of 319. Pooling the 16 rays over 26 orientations therefore
gives 416 complete measurements per shell.

Arrival is the linearly interpolated first crossing of 50% of each receiver's
own absolute peak. The reported arrival-time spread is
`100 × (maximum arrival - minimum arrival) / mean arrival`; it is not a direct
directional-speed spread. Fifteen of the sixteen sampled shells, radii 43–85,
are below 1%. The radius-40 shell is 1.059680%, so the bounded claim is not that
every tested shell is below 1%.

Across radii 40–85, the relative spread has an overall decreasing linear trend
from 1.059680% to 0.259886% (fit slope -0.015688 percentage points per bond,
R² 0.941157). The absolute arrival range also generally decreases from 0.938161
to 0.508060 simulation updates, although neither series decreases at every
successive shell. Mean half-peak arrival is nearly linear in radius
(`t = -6.703147 + 2.376603 r`, R² 0.999992), corresponding to a
slope-equivalent value of 0.420769 bonds per update for this configuration.

These results demonstrate sub-1% relative half-peak arrival-time spread over
the stated radii and a decreasing distance trend in this controlled run. They
do not prove universal physical isotropy. In particular, the independent
adjacent-shell correlation-speed statistic retains approximately 1.25–2.20%
spread, and pooled peak-amplitude spread remains 8.98–18.40%. The experimental
diamond topology remains separate from the canonical cubic Node 0.1 foundation.

## Reproduction and data preservation

- Named presets remain locally in `../measurements/diamond-shells/presets/`.
- Offline audit helpers and commands: `../scripts/diamond/README.md`.
- Measurements, presets, trace archives, screenshots, root execution logs and
  generated Maven outputs remain local and are excluded from this commit.
- The detailed local amplitude report is
  `../measurements/diamond-shells/amplitude-audit-1794176597851/amplitude-investigation.md`.
  These local data paths require a separate transfer to reproduce the same audit
  on another machine; they are not included in this source checkpoint.

When increasing lattice dimensions on another machine, explicitly recenter the
source indices and recheck the boundary cutoff and pulse completeness.

## Validation

`mvn -q -DreuseForks=false test package` passed: 619 tests, five skipped,
zero failures and zero errors. This uses a fresh JVM for each test class.
The default shared-JVM run had two runtime test failures; both affected tests
passed when run together separately. Shared singleton state between test classes
remains a test-isolation limitation. No mechanics changes were made for this
checkpoint packaging. `git diff --cached --check` passed for the source checkpoint.

The v0.5.0 release candidate was revalidated on 2026-11-10 with the same
isolated-class command: 619 tests, five skipped, zero failures and zero errors,
followed by a successful package build. A default shared-JVM `mvn test` run
reported 12 failures and one error across four stateful test classes; every
affected class passed in its own Maven invocation. This remains a test-isolation
limitation and is reported separately from the arrival-timing result.

The complete committed-file list is in `diamond-transverse-checkpoint-files.txt`.
