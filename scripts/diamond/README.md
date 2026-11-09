# Diamond measurement analysis

Run these offline helpers from the repository root after building `target/rem.jar`.
Saved presets are included in Git. Measurement data is excluded; transfer the input
data directories separately before running these historical audit commands.
They investigate the experimental diamond model; they do not change the canonical
Node 0.1 mechanics. Java helpers use the application's existing dependencies.
Python analysis requires NumPy; the tail plot requires ReportLab. The JavaScript
comparison uses Node.js built-ins.

The amplitude audit is specific to the saved period-6 run, radii 40–42, 26
excitation orientations and 16 perpendicular receiver rays per orientation:

```powershell
New-Item -ItemType Directory -Force target/analysis | Out-Null
javac -cp target/rem.jar -d target/analysis scripts/diamond/AmplitudeMechanicsAudit.java
java -Xmx768m -cp 'target/analysis;target/rem.jar' AmplitudeMechanicsAudit measurements/diamond-shells/transverse-1794176597851 measurements/diamond-shells/amplitude-audit-1794176597851
python scripts/diamond/analyze-amplitude.py
```

The Python script uses these historical paths and 154 recorded samples explicitly.
It regenerates analysis outputs in that audit directory. Preserve a copy before
changing its assumptions for another run.

```powershell
node scripts/diamond/compare-pulse-periods.mjs measurements/diamond-shells/transverse-1794174331487 measurements/diamond-shells/transverse-1794176597851 measurements/diamond-shells/transverse-1794177902901
python scripts/diamond/plot-period8-tail.py
```

`MaximumRadiusAudit.java` and `PulseWindowAudit.java` document the geometric
receiver-fit and boundary-cutoff checks used for the radius experiments. Named
experiment presets are in `measurements/diamond-shells/presets/`. When moving to
a larger lattice, explicitly recenter the configured source indices.
