# REM Framework

**REM Framework — Recursive Emergent Mechanics**

REM Framework is an experimental computational research framework for investigating
whether complex physical behavior can emerge from a small set of local mechanical
rules.

The project originated as the **Quantum Field Simulator**, initially developed to
experiment with wave propagation and the double-slit experiment. It has since
developed into a broader research framework investigating emergent geometry,
wave propagation, transport, interaction, and other physical phenomena.

The central research question is:

> What is the simplest possible universe capable of producing the physical
> behavior we observe?

REM Framework does not assume that its current mechanics describe physical
reality. It is a computational environment for constructing hypotheses,
running experiments, measuring emergent behavior, and identifying where those
hypotheses succeed or fail.

## Core Idea

The current REM model starts with a discrete lattice of connected nodes.

Each node has a position, velocity, and acceleration. A node responds only to
the relative positions of its local neighbors.

The fundamental node acceleration is:

\[
\mathbf{a}
=
\frac{k^2}{|N|}
\sum_{\mathbf{n}\in N}
(\mathbf{n}-\mathbf{p})
\]

where:

- \(\mathbf{p}\) is the current node position;
- \(N\) is the node's local neighborhood;
- \(\mathbf{n}\) is the position of a neighboring node;
- \(k\) controls the response rate.

For the current cubic REM lattice, each interior node has six direct neighbors.

No global wave equation is imposed on the lattice. Disturbances propagate
because displaced nodes affect their neighbors, which affect their neighbors,
and so on.

This recursive local interaction is the foundation from which the framework
investigates emergent behavior.

## Current Research Areas

### Wave Propagation

Oscillating Identities can disturb the lattice and produce propagating waves.

Experiments have demonstrated:

- recursive wave propagation;
- reflection;
- absorption;
- constructive interference;
- destructive interference;
- double-slit-like interference patterns.

These behaviors emerge from local node interactions rather than from a
separately imposed global wave equation.

### Identity

An **Identity** is a persistent entity that occupies and interacts with the
lattice.

The original implementation occupies a single lattice node and can oscillate
along selected spatial axes.

Current development is extending this concept to an **Extended Identity**,
where one Identity occupies a finite region containing multiple lattice nodes.

One objective is to investigate whether a spatially extended, approximately
spherical excitation improves propagation isotropy compared with a single-node
source.

### Transport and Pressure

REM Framework includes experimental transport mechanics in which local
geometric imbalance produces an accumulated pressure associated with an
occupied location.

This provides a research mechanism for investigating whether translational
motion and interaction can emerge from local lattice deformation.

Transport and pressure remain experimental components of the framework.

### Electric Charge

Electric charge is an active research topic.

The current working direction is to investigate whether charge-like interaction
can emerge from deformation of the surrounding lattice rather than by directly
programming attraction and repulsion between distant objects.

This work is experimental and should not be interpreted as an established
physical model.

## Geometry Discovery

REM Framework also contains an independent research system called
**Geometry Discovery**.

Geometry Discovery investigates whether canonical geometry can emerge from
topological and geometric constraints instead of being predefined by
coordinates.

It is intentionally separated from the REM runtime mechanics.

Its purpose is ultimately to investigate whether alternative spatial
connectivity could provide better isotropy than the current cubic lattice.

### Stage 1 — Topology Discovery

Stage 1 operates without geometric coordinates.

It supports research into:

- regular graph generation;
- fixed neighbor counts;
- combinatorial face discovery;
- canonical graph identification;
- graph isomorphism;
- automorphism and symmetry analysis;
- manifold validation;
- uniform face structures;
- dual-directed topology generation;
- deterministic configuration persistence.

Known regular topologies including tetrahedral, cubic, octahedral,
dodecahedral, and icosahedral cases can be identified by the research system.

Reference recognition is used only after discovery for comparison and does not
supply coordinates to the realization algorithm.

### Stage 2 — Geometric Realization

Stage 2 attempts to realize a selected coordinate-free topology geometrically.

The research environment includes constraints and measurements such as:

- Edge Length;
- Face Planarity;
- Face Angle;
- Face Area;
- Face Orientation;
- Face Distance;
- Vertex Distance;
- Face-Center Distance;
- Edge-Center Distance;
- Dihedral Angle;
- Normal Alignment;
- Convexity;
- Self-Intersection Avoidance.

The system supports:

- random spherical initialization;
- topology-spectral initialization;
- simultaneous relaxation;
- sequential continuation;
- guided relaxation;
- manual relaxation;
- independent constraint activation;
- runtime constraint-strength factors;
- geometry snapshots;
- experiment recording and replay;
- detailed scientific diagnostics.

Failed realizations are retained as research results and classified using
`ConfigurationStatus`.

Geometry Discovery has demonstrated that the same topology and constraint set
can settle into different stable geometric realizations depending on
initialization and relaxation history.

This makes local minima, constraint interaction, and realization trajectory
important open research questions.

### Geometry Discovery Status

Geometry Discovery is currently a **research system**, not a validated
replacement for the REM Framework lattice.

Current open questions include:

- robust realization from random coordinates;
- global convexity;
- self-intersection avoidance;
- escape from stable noncanonical local minima;
- constraint activation order;
- relative constraint strengths;
- mixed-face topology;
- higher-genus topology;
- systematic multi-seed reproducibility;
- quantitative isotropy comparison between candidate geometries.

The current Geometry Discovery work is being preserved as a research checkpoint
while development returns to Extended Identity and electric-charge experiments.

## Scientific Status

REM Framework is experimental research software.

It is important to distinguish between:

1. behavior observed in simulation;
2. mechanisms currently implemented by design;
3. hypotheses being investigated;
4. experimentally validated physical theory.

A simulation reproducing a familiar phenomenon does not by itself demonstrate
that the underlying mechanism describes nature.

The framework is intended to make these questions testable rather than assume
their answers.

## Historical Background

The project began as **Quantum Field Simulator**, primarily for experimenting
with wave propagation and double-slit interference.

Older project files, configuration names, packages, documentation, and videos
may therefore still contain terminology associated with Quantum Field Simulator
or QFS.

These should be considered historical names.

The current project name is:

**REM Framework — Recursive Emergent Mechanics**

## Building and Running

REM Framework is written in Java and built with Maven.

### Requirements

- JDK 21 or newer, with `java` and `javac` available on your PATH
- Maven, with `mvn` available on your PATH
- An OpenGL-capable graphics environment and a graphical desktop session

Run the commands below from the repository root. Build on the operating system
where you will run the application: Maven selects its native libraries for that
platform. The Linux build currently targets x86-64.

### Windows (PowerShell)

```powershell
git switch optimize-memory
git pull --ff-only
mvn clean package -DskipTests
java --enable-native-access=ALL-UNNAMED -Xmx4g -jar target/rem.jar
```

### Linux (x86-64)

```bash
git switch optimize-memory
git pull --ff-only
mvn clean package -DskipTests
java --enable-native-access=ALL-UNNAMED -Xmx4g -jar target/rem.jar
```

The build produces `target/rem.jar`. Keep the repository root as the working
directory when launching so the application can find its local resources.
`-Xmx4g` allows up to 4 GB of Java heap; increase it if your machine has enough
RAM for larger lattices.

The launch build above skips tests. To build and run the test suite with a fresh
JVM per test class, use:

```bash
mvn clean package -DreuseForks=false
```

### Experiment Presets

Small saved configurations are included in
[`measurements/diamond-shells/presets/`](measurements/diamond-shells/presets/).
Measurement CSVs, trace archives and screenshots remain excluded from Git.

In the Isotropy window, **Load saved experiment** restores
`measurements/diamond-shells/presets/last-experiment.ini`. Other saved `.ini`
configurations can be copied to that filename before loading. Loading a preset
restores its configuration, not an evolved simulation state or measurement results.

The transverse survey separately reads `transverse-identity.properties` from the
same directory. Named period-4, period-6, period-8 and two-cycle configurations are
included; copy the desired `.properties` file to `transverse-identity.properties`
before starting the survey. These configure the survey and do not replace the
full application `.ini` preset.

When changing lattice dimensions, explicitly recenter the source indices and
check pulse completeness and the boundary cutoff again.

The default 120³ transverse setup uses radii 40–42 with **edge absorption off**.
The saved `last-experiment.ini` matches this setting. If the survey reports that a
receiver overlaps edge absorption, uncheck **Edge absorption for next survey**
before clicking **Run survey**, or use smaller radii / a thinner absorber. Loading
an older absorber-enabled preset restores its original absorption setting.
