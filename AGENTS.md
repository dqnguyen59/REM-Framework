# REM Framework Development Instructions

## Framework terminology

- Use the name "REM Framework".
- REM stands for "Recursive Emergent Mechanics".
- Do not use the former name "REM-FIELD".

## Canonical model

- Node version 0.1 is the canonical mechanical foundation.
- Experimental electromagnetic versions are not part of the canonical foundation.
- The Fundamental Node Equation determines node acceleration from relative
  positions of directly connected neighboring nodes.
- The lattice is a finite three-dimensional cubic lattice.
- Each node has six direct neighbors where dimensions permit.

## Occupancy and pressure

- Use occupancy-local pressure.
- Pressure belongs to the Occupancy relation between an Identity and a Node.
- Pressure does not belong directly to the Identity or Node.
- Pressure persists between updates.
- Pressure resets or transfers according to the transport rule when occupancy
  moves to another node.
- Preserve residual pressure after a transfer unless a task explicitly changes
  this rule.

## Transport

- Identities respond only to local mechanical differences.
- Identities do not choose destinations globally.
- Transport direction is based on local neighbor-distance imbalance.
- Avoid artificial damping, saturation, or velocity caps unless explicitly
  requested for a controlled experiment.

## Java conventions

- Use JOML vector types where consistent with the existing codebase.
- Follow the existing package structure.
- Do not introduce a new framework or dependency without explaining why.
- In JavaDoc, use `<br><br>` for paragraph spacing instead of blank `*` lines.
- Make focused changes and preserve existing behavior outside the requested task.

## Validation

- Build the project after changes.
- Run relevant tests.
- Report exactly which files were changed.
- Explain any equations implemented in the code.
- Never claim that an emergent physical behavior has been proven solely from
  visual simulation output.