# REM Framework Step 2 / Task 1 — execution status

Status: `INCOMPLETE_RUNTIME_LIMIT`

The frozen compact Extended Identity runner was built with the 297^3 lattice,
D=24, the 15 exact rays, 198 updates, and cycles 6..11 as the measurement
window. R=24 was not attempted because it reaches the detector shell.

The host execution environment terminated the long-running Java process during
the extended-radius measurement before the sweep could be finalized. No
lattice dimension, detector geometry, cycle window, or physics configuration
was reduced. A measured R=1 diagnostic observed before termination was:

- cycleEmax = 8.155862261257493
- cycleMAD = 1.3768390724476045
- cycleStdDev = 2.285692686191823

This partial observation is not treated as a completed Step 2 result. No
radius trend, preferred radius, or X/Y validation claim is made.
