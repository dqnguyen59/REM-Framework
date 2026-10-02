# REM Framework Step 1 / Task 1 — revised protocol status

`RESOURCE_LIMIT_BEFORE_MEASUREMENT`

The exact geometry is valid and requires a symmetric 297^3 lattice. The
existing cubic-lattice implementation could not construct that lattice with
the tested 16 GiB, 64 GiB, or 96 GiB JVM heaps. The 96 GiB retry failed during
`lattice.buildHeadless`; its last telemetry sample reached approximately 89.2
GiB used heap and 96.7 GiB committed heap, with only 16.7 GiB available system
memory. No oscillator update or detector measurement ran.
No stable-cycle or isotropy values are reported from this incomplete run.

The 96 GiB retry telemetry recorded 134,965,014,528 bytes physical memory,
110,132,977,664 bytes available before construction, and expected node count
26,198,073. Available memory had fallen to 16,741,552,128 bytes at the last
construction sample. The process terminated before a completed construction
sample could be written. A safe minimum heap cannot be established from this
partial failure; trying a larger heap is not safe on this machine.

Required measurement window: cycles 6..11, updates 1..198.
Earliest calculated reflection return at 297^3 for the critical offset 96:
update 200. No detector is silently removed and no measurement window is
shortened.
