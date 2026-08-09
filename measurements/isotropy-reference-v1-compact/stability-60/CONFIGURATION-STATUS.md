# REM Framework Step 1 / Task 2 — 60-cycle stability characterization

Status: `NOT_RUN_COMPUTATIONALLY_IMPRACTICAL`

The frozen reflection-safety derivation requires `1080` complete updates and outer detector offset `96`. Therefore the required symmetric half-size is `(1080 + 96) / 2 + 1 = 589`, and the required lattice is `1179^3`.

That is `1638858339` nodes and `117997800408` persistent payload bytes (`9 * double`, 72 bytes/node), approximately 109.894 GiB before JVM/object/runtime overhead. The experiment was stopped before backend allocation. No 297^3 fallback, cycle shortening, or measured stability claim was made.
