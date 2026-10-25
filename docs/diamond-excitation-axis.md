# Controlled packet excitation axis

The Isotropy controlled-packet panel has an **Excitation axis** selector: X, Y or Z.
The explicit choice applies to both thin XY slabs and full 3D domains.
Choose Z to preserve Z displacement when changing from a slab to a 120-cube.
The choice is saved as `[DiamondProbe] excitationAxis` in application settings.
The default is Z. Missing, invalid or older AUTO settings become Z.

Changing dimensions never selects a different axis. Legacy research request overloads
retain their previous behavior, independently of the explicit GUI setting. The GUI freezes the
selected axis into each pending request, so changing the dropdown does not alter
a running packet. Status messages report the resolved axis.

This is **polarization**, the axis of the initial node displacement. The initial
position is `equilibrium + offset * selectedUnitAxis`. The radial packet envelope,
source support, fixed nodes, time integration and reflection cutoffs are unchanged.
The XY slab uses its existing XY-radius envelope and sampled-peak normalization;
3D uses its existing spherical envelope and amplitude convention. The built-in
3D research receivers project displacement on the selected axis instead of always
reading X. Probe position/gradient norm detectors remain vector-valued.

Signed reversal and full-cycle diagnostics still explicitly measure Z. For direct
comparison of those diagnostics select Z excitation; X or Y excitation does not
automatically change the measurement axis.

This change also implements the previously requested fractional threshold-arrival
estimates. See `diamond-spike-investigation.md` for the equation and export fields.
Whole confirming updates remain available separately. Fractional estimates improve
temporal resolution but do not recover missed subthreshold lobes.

## Validation and changed files

Validation includes explicit X/Y/Z and legacy research behavior in XY slabs across wavelengths 2/4/8 and
8/16/32/64 directions, X/Y/Z in a 48-cube, preservation of fixed nodes and unselected
components, affine receiver projection on every selected axis, saved settings,
bracketed fractional crossings, existing arrival/shell statistics and layout tests.
No live GUI run is claimed.

Files changed for this implementation:

- `src/main/java/remframework/geometry/discovery/research/diamond/DiamondExcitationAxis.java`
- `src/main/java/remframework/geometry/discovery/research/diamond/DiamondRuntimeDisturbance.java`
- `src/main/java/remframework/geometry/discovery/research/diamond/DiamondRuntimeReceivers.java`
- `src/main/java/remframework/settings/AppSettings.java`
- `src/main/java/remframework/gui/view/frames/FrameIsotropy.java`
- `src/main/java/remframework/opengl/model/ProbeArrivalMeasurement.java`
- `src/main/java/remframework/opengl/model/ProbeShellArrivalReport.java`
- `src/test/java/remframework/lattice/DiamondXYExcitationTest.java`
- `src/test/java/remframework/lattice/DiamondBulkExcitationAxisTest.java`
- `src/test/java/remframework/settings/AppSettingsStartupTest.java`
- `src/test/java/remframework/opengl/model/ProbeArrivalMeasurementTest.java`
- `docs/diamond-excitation-axis.md`
- `docs/diamond-spike-investigation.md`
