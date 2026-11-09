# Experimental diamond edge absorber

The optional **Experimental edge absorber** checkbox in Preferences enables a boundary-only sponge for the rendered diamond Node v0.1 runtime. It is off for old presets and never enabled for the canonical cubic runtime. Existing fixed outer nodes remain fixed. Pressure, transport and Identity excitation rules are unchanged.

The supplied `measurements/diamond-shells/presets/identity-spread-120cube-absorber30.ini` uses 120 x 120 x 120 nodes and 30 index layers per side, leaving a 60 x 60 x 60 index interior. It retains the existing orbit (32/512/8192-update periods), amplitude 20, four XYZ shells at 16/20/24/28 bonds, one warm-up cycle and one measured cycle. The source index is moved to (60,60,60). Arrival thresholds remain 0.01 for both signals. Index layers and physical bond distances are different units in diamond geometry.

Load this preset through the existing experiment preset workflow, regenerate/apply its four probe shells, and restart the Identity spread measurement. The long 120-cube experiment has not been run as part of this change. The original preset and existing results are preserved.

## Equation

Let d be the nearest edge distance in index layers and L the configured width. The existing profile is a = (1 - cos(pi (d+1)/(L+1)))/2 for d < L, otherwise a = 1. An axis with 2L >= its dimension is skipped by the existing profile logic. Identity spread measurements now reject enabled absorbers with any such skipped axis or zero width.

The experimental retention factor is r = exp(-0.2 (1-a)^2). In the absorbing region only, let b denote the normal acceleration increment (the neighbor/force sum times gamma times waveStep), and v the old velocity. The update becomes:

- position increment = r (v + b + b/2)
- new velocity = r (v + b)
- stored acceleration = b/2, preserving the existing storage convention

When a = 1 or the feature is disabled, the original code path is used without numerical changes. This is an explicitly artificial experimental absorber, not a reflection-free boundary or a modification to the canonical node equation.

## Measurement safeguards and interpretation

Identity spread recording rejects sources and positive-weight probe stencil nodes inside the absorbing region. Changing enabled state or layer width during recording invalidates the run when sampling resumes. Summary CSVs include `edge_absorber_enabled` and `edge_absorber_layers`, captured at measurement start. `reflections_included` remains true: residual reflections have not been eliminated or subtracted.

This does not correct the previously identified source-orbit component imbalance. Higher arrival thresholds are not used to mask reflections; they do not change the whole-cycle RMS response statistic.

## Validation

A headless 32-cube diamond pulse comparison uses eight boundary layers, k=0.8, gamma=1, a small Gaussian initial Z displacement, and 500 updates. The integrated squared displacement inside radius 4 over updates 151–500 is 0.003487633138537164 with fixed boundaries and 0.00008454827717528586 with the absorber (ratio 0.0242423, a 97.58% reduction in this diagnostic). The first five interior samples are unchanged. This is a returning-wave diagnostic for that setup, not a universal reflection coefficient or proof of physical isotropy.

Tests also check legacy/off and interior update equivalence, opt-in edge damping, runtime profile changes, and the existing half-dimension skip rule. The relevant Identity, measurement, settings and layout regression suite passed. A GUI test and full 120-cube run are not claimed.

## Files changed for this feature

- `src/main/java/remframework/opengl/model/entity/Node.java`
- `src/main/java/remframework/opengl/model/Lattice.java`
- `src/main/java/remframework/settings/Project.java`
- `src/main/java/remframework/gui/view/frames/FramePreferences.java`
- `src/main/java/remframework/opengl/model/IdentityOrbitSpreadRun.java`
- `src/test/java/remframework/lattice/DiamondAbsorberTest.java`
- `measurements/diamond-shells/presets/identity-spread-120cube-absorber30.ini`
- `docs/diamond-edge-absorber.md`
