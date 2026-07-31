# REM Framework — Current State

Last updated: 2026-07-31

## 1. Purpose

REM stands for Recursive Emergent Mechanics.

The REM Framework investigates whether physical behavior can emerge from
local recursive interactions in a discrete node structure.

## 2. Canonical Terminology

- Use “REM Framework,” not “REM-FIELD.”
- Identity is not assumed to be a particle.
- Pressure is attached to Occupancy.
- Geometry Discovery is independent from REM mechanics.

## 3. Fundamental Node Equation

\[
\mathbf{a}
=
\frac{k^2}{|N|}
\sum_{n \in N}
(\mathbf{n}-\mathbf{p})
\]

## 4. Current REM Architecture

### Node

- Position
- Velocity
- Acceleration
- Direct neighbors

### Identity

- Occupies one node
- Oscillates
- May transport between nodes

### Occupancy

- Relation between Identity and Node
- Stores persistent transport pressure

## 5. Validated Observations

### Wave propagation

- Local excitation propagates through recursive node updates.
- Interference has been observed.

### Cubic-lattice anisotropy

- Exact-radius experiments demonstrate cubic rather than spherical symmetry.
- Directional anisotropy strengthens over the measured radii.

## 6. Geometry Discovery

Geometry Discovery is a separate experimental system.

Accepted workflow:

1. Generate graph configurations.
2. Classify each configuration.
3. Select one configuration.
4. Attempt geometric realization.
5. Derive faces.
6. Validate a closed boundary.
7. Calculate area or volume.
8. Investigate maximum area or volume.

## 7. Current Geometry Discovery Decision

A stable equal-edge realization is not automatically a valid candidate.

Separate:

- topological validity;
- elastic equilibrium;
- geometric realization;
- face derivation;
- boundary validity;
- geometric symmetry;
- area or volume eligibility.

## 8. ConfigurationStatus

Current proposed statuses include:

- GENERATED
- INVALID_PARAMETERS
- INVALID_DEGREE
- DISCONNECTED
- FACES_NOT_DERIVABLE
- AMBIGUOUS_FACE_SET
- OPEN_BOUNDARY
- GEOMETRICALLY_SOLVABLE
- NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS
- DEGENERATE
- SELF_INTERSECTING
- FLEXIBLE
- CLOSED_BOUNDARY
- AREA_AVAILABLE
- VOLUME_AVAILABLE
- VALID_CANDIDATE

## 9. Current Implementation Status

- Pluggable lattice-model loading implemented.
- Built-in cubic fallback implemented.
- Generated cubic-lattice.toml template implemented.
- Geometry Discovery visualizer implemented.
- Dynamic Elastic Discovery implemented for K=2 and K=3.
- Configuration enumeration and classification is the next major phase.

## 10. Open Research Questions

- Can non-isomorphic fixed-degree graphs be enumerated completely within useful bounds?
- Can faces be derived uniquely from topology plus geometry?
- Does maximum enclosed area or volume naturally produce symmetric canonical geometry?
- Which lattice structures reduce propagation anisotropy?
- How should successful discovered geometries later integrate with REM mechanics?

## 11. Current Next Task

Implement the strict two-stage workflow:

1. Configuration Generation and classification.
2. Geometry Test of a user-selected configuration.

Area and volume must not be calculated before a unique valid boundary exists.

## 12. Coding Conventions

- JavaDoc blank lines use `<br><br>`.
- Preserve the Fundamental Node Equation.
- Do not silently modify scientific parameters.
- Every failed experiment must retain a reason.