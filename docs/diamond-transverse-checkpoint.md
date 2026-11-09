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

The complete committed-file list is in `diamond-transverse-checkpoint-files.txt`.
