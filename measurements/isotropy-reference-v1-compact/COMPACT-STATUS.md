# Compact Step 1 execution status

Status: `VALID`

The compact backend passed all three canonical-object equivalence regressions
before the 297^3 run. Each regression covered 180 updates and used tolerance
1e-12; maximum absolute, mean absolute, and maximum relative differences were
all 0.0.

The frozen Step 1 experiment then completed X, Y, and Z independently for 198
updates each. Cycles 1..5 were discarded and cycles 6..11 produced 630 stable
rows per axis (105 detectors × 6 cycles). The backend was compact and used 16
deterministic CPU chunks.

The previous canonical object-lattice failure remains recorded separately in
`measurements/isotropy-reference-v1/CONFIGURATION-STATUS.md` as
`RESOURCE_LIMIT_BEFORE_MEASUREMENT`.

Persistent compact lattice state is 9 double arrays = 72 payload bytes/node.
For 297^3 this is 1,886,261,256 bytes. No per-node boundary, detector, or
temporary update arrays are used; source state and detector cycle state are
constant-size overheads.
