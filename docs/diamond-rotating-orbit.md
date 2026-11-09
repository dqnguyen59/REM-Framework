# Continuous rotating orbit preview

The application now offers **Continuous rotating orbit (preview only)** under Tools → Isotropy → Probe measurements. Start/reset orbit begins continuous excitation; Pause/resume orbit controls playback. No probes are required. This preview includes boundary reflections and does not compute arrivals or establish equal directional excitation.

## Orbit equation

The unit direction is `u(t) = Rz(c) Ry(b) Rx(a) (0,1,0)`, with `a=2πt/P`, `b=2πt/(Pr)`, and `c=2πt/(Pr²)`. Source displacement is the existing spatial source weight times orbit radius times `u(t)`. Its direction has constant unit length. The trajectory repeats after `Pr²` updates. Three magnified projections show recent XY, XZ, and YZ paths with a yellow current-position marker. A finite nested orbit does not guarantee uniform spherical coverage.

## Saved preview configuration

Load `measurements/diamond-shells/presets/rotating-orbit-120cube-q095.ini` using the existing experiment preset workflow, then start the orbit. The latest saved experiment also contains these values.

| Setting | Value |
|---|---:|
| Lattice dimensions | 120 × 120 × 120 |
| X orbit period P | 64 updates |
| Slower-axis multiplier r | 4 |
| X / Y / Z periods | 64 / 256 / 1024 updates |
| Source width | 4 bonds |
| Orbit radius | 1 bond |
| Constant Frequency k | 0.9746794344808963 |
| Electric Field γ | 1 |
| Effective coupling q = γk² | 0.95 |

The Electric Field label represents the existing mechanical factor in this experimental diamond model. The two coupling controls enter this update through their product `q=γk²`; they are not independent frequency and field controls here. The existing acceleration increment is `(q/4) Σ(neighbor position − node position)`. No damping or new propagation equation was added.

## Limited propagation comparison

`measurements/diamond-shells/orbit-coupling-comparison.csv` records six settings on a 64³ lattice over 96 updates, with source radius 0.01, width 4, and periods 64/256/1024. Each receiver contributes its maximum displacement over that interval; results average those maxima within shell bands. Boundary contamination was not explicitly excluded.

At radius 24 bonds, q=0.95 produced mean peak displacement 8.93611803e-7, versus 6.88606033e-7 for k=0.5, γ=1: about 30% higher. It was only about 0.56% above q=0.81. The settings k=0.9, γ=1 and k=0.45, γ=4 produced identical results, as expected from their equal q. Nearer-shell responses do not all improve with q. These results select the strongest farthest-shell response among the tested cases; they do not establish a universal optimum or maximum propagation distance.

## Validation and files changed for this addition

`mvn package` passed with the selected regression tests; the separate propagation audit also passed. Total: 21 tests, zero failures or errors. The GUI was launched and observed running through update 450 with changing projection trails.

Source and test files changed for this addition (other pre-existing working-tree changes were retained):

- `src/main/java/remframework/geometry/discovery/research/diamond/DiamondRotatingOrbit.java` — new trajectory.
- `src/main/java/remframework/geometry/discovery/research/diamond/DiamondRuntimeDisturbance.java` — continuous source driver.
- `src/main/java/remframework/gui/view/frames/FrameIsotropy.java` — controls and projection preview.
- `src/main/java/remframework/settings/AppSettings.java` — persisted orbit configuration.
- `src/test/java/remframework/geometry/discovery/research/diamond/DiamondRotatingOrbitTest.java` — trajectory checks.
- `src/test/java/remframework/lattice/DiamondSphericalRuntimeTest.java` — continuous runtime coverage.
- `src/test/java/remframework/lattice/DiamondOrbitPropagationAuditTest.java` — parameter comparison.
- `src/test/java/remframework/settings/AppSettingsStartupTest.java` — configuration round trip.

Artifacts: this document, the comparison CSV, the named preset, updated `presets/last-experiment.ini` and its timestamped save, and `docs/guided-spherical-test/09-rotating-orbit.png`. Normal application autosave also records the orbit controls in local settings.

![Running orbit preview](guided-spherical-test/09-rotating-orbit.png)
