# Compact Receiver Velocity / Cycle Audit

The small equivalence audit passed with maximum receiver-velocity difference below 8.4e-16.

For R01, R08, and R09, the persisted 600-update compact traces show the measured receiver Z velocity, sign state, source phase/excitation, and existing transport state. The cycle decision remains the existing positive-to-negative then negative-to-nonnegative Z-velocity transition.

Results for all three directions are identical in sign behavior: `receiverVelocityZ` never became negative, never returned from a negative state, and `cycleCompletedThisUpdate` remained false. Therefore `Occupancy.isCycleCompleted()` never reached its negative-to-nonnegative completion branch; `signIsPositiveZ` remained true while `cycleSampleCount` advanced.

The signed final Z velocities were positive: R01 `0.17490179566237168`, R08 `0.678606311510032`, and R09 `1.1638192957346238`. Pressure continued accumulating during the incomplete receiver cycle in all three directions. The complete signed values are in `receiver-velocity-trace.csv` and `receiver-cycle-state-summary.csv`.

The audit records the mechanical result only: velocity never crossed the existing negative threshold, so no cycle could complete. Reflection labels retain the previously established safe limits (116, 176, 196); no post-reflection data is claimed clean.
