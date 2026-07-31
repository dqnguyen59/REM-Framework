# Geometry Discovery Phase 2

Geometry Discovery now has two independent stages. Configuration Generation
enumerates coordinate-free connected simple `K`-regular graphs. Geometry Test
operates on one explicitly selected configuration and never changes its edges.

## Enumeration and identity

Generation uses deterministic degree-constrained backtracking. Exact exhaustive
enumeration and canonical labeling are intentionally bounded to `N <= 8`.
Larger requests return `ENUMERATION_LIMIT_REACHED`; no random sample is presented
as complete. Labeled candidates are compared by an exact adjacency-preserving
permutation search, rather than by sorted edge lists. Retained representatives
receive the lexicographically minimal adjacency encoding over all permutations.
No third-party graph dependency was added.

Automorphism group size is the count of adjacency-preserving permutations.
Size one is `ASYMMETRIC`; size greater than one is `SYMMETRIC`; a graph is
descriptively `HIGHLY_SYMMETRIC` when the computed automorphisms act transitively
on its vertices. This is graph symmetry only.

## Geometry gates

The existing Dynamic Elastic engine is reused with canonical angle constraints
disabled. It seeks equal connected-edge lengths while holding topology fixed.
Numerical exhaustion is reported as `NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS`,
not as proof of non-embeddability.

Faces are canonicalized simple cycles, filtered for chords and geometric
planarity. A 2D boundary must be one non-self-intersecting cycle. A 3D boundary
requires every graph edge to occur in exactly two planar faces. `V-E+F` is
reported only as a diagnostic. Area uses the 2D shoelace equation or deterministic
face-fan triangle areas. Volume sums signed tetrahedral contributions
`dot(a, cross(b,c))/6` after outward orientation. Both calculators are gated by
a validated unique closed boundary. Optimization remains a separate later stage.

Configuration sets are stored under `lattice/geometry-discovery/configuration-sets`
with topology-only per-configuration TOML. Geometry-test results live in the
registry separately and remain attached by configuration ID, including failures.
