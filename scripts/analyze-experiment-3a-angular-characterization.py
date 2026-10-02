#!/usr/bin/env python3
"""Independent angular analysis and chart generation for Experiment 3A."""

import csv
import math
import os
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

LOCAL_DEPS = Path(__file__).resolve().parents[1] / ".python-test2d-deps"
if LOCAL_DEPS.is_dir():
    sys.path.insert(0, str(LOCAL_DEPS))
os.environ.setdefault("MPLCONFIGDIR", "/tmp/rem-framework-matplotlib")

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image

TEST_ID = "experiment-3a-angular-characterization"
OUT = Path("measurements") / TEST_ID
RAW = OUT / "raw-detectors-run-001.csv"
CHARTS = OUT / "charts"
SOURCE = np.array([80.0, 80.0, 80.0])
PERIOD = 18
UPDATES = 180
RADIUS = 20
INTERFACES = (("x=30", 0, 30.0), ("x=130", 0, 130.0),
              ("y=30", 1, 30.0), ("y=130", 1, 130.0),
              ("z=30", 2, 30.0), ("z=130", 2, 130.0))
SCALARS = ("radialDisplacement", "radialVelocity", "radialAcceleration",
           "rawRadialGradient", "displacementMagnitude",
           "accelerationMagnitude", "gradientMagnitude",
           "sourceAxisAcceleration", "transverseAccelerationMagnitude")
PRIMARY = ("radialAccelerationRms", "accelerationMagnitudeRms",
           "rawRadialGradientRms", "displacementMagnitudeRms")
CHART_NAMES = (
    "angular-radial-acceleration", "angular-acceleration-magnitude",
    "angular-raw-radial-gradient", "angular-displacement-magnitude",
    "azimuthal-response", "opposite-even-parity", "opposite-odd-parity",
    "direction-class-comparison", "representative-radial-acceleration",
    "representative-acceleration-magnitude", "directional-response-3d")


def label(v):
    return "".join(("P" if c > 0 else "N") + a
                   for c, a in zip(v, "XYZ") if c)


def directions():
    result = {}
    for x in (-1, 0, 1):
        for y in (-1, 0, 1):
            for z in (-1, 0, 1):
                if not (x or y or z):
                    continue
                vector = np.array([x, y, z], dtype=float)
                count = int(np.count_nonzero(vector))
                kind = ("AXIS", "FACE_DIAGONAL", "BODY_DIAGONAL")[count - 1]
                result[label((x, y, z))] = (vector, kind,
                                             label((-x, -y, -z)))
    return result


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, rows):
    if not rows:
        raise ValueError(f"refusing empty output {path}")
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def vec(row, prefix):
    return np.array([float(row[prefix + axis]) for axis in "XYZ"])


def reflected_path(coordinate):
    options = []
    for boundary, axis, interface in INTERFACES:
        mirror = coordinate.copy()
        mirror[axis] = 2 * interface - coordinate[axis]
        options.append((float(np.linalg.norm(mirror - SOURCE)), boundary))
    return min(options)


def geometry_and_timeseries(raw):
    specs = directions()
    if len(raw) != 26 * UPDATES:
        raise ValueError(f"expected 4680 raw rows, found {len(raw)}")
    grouped = defaultdict(list)
    enriched = []
    geometry = {}
    seen = set()
    for row in raw:
        name, update = row["detectorName"], int(row["update"])
        if (name, update) in seen:
            raise ValueError(f"duplicate row {(name, update)}")
        seen.add((name, update))
        grouped[name].append(row)
        base = name.removesuffix("_R020")
        if base not in specs:
            raise ValueError(f"unexpected detector {name}")
        requested, kind, opposite = specs[base]
        coordinate = np.array([float(row[f"node{a}"]) for a in "XYZ"])
        offset = coordinate - SOURCE
        actual_radius = float(np.linalg.norm(offset))
        radial = offset / actual_radius
        requested_unit = requested / np.linalg.norm(requested)
        theta = math.degrees(math.acos(np.clip(radial[2], -1, 1)))
        phi = math.degrees(math.atan2(radial[1], radial[0]))
        angle_error = math.degrees(math.acos(np.clip(
            float(np.dot(radial, requested_unit)), -1, 1)))
        ideal = requested_unit * RADIUS
        displacement = vec(row, "positionDeviation")
        velocity = vec(row, "velocity")
        acceleration = vec(row, "acceleration")
        gradient = vec(row, "rawGradient")
        cycle_gradient = vec(row, "cycleDerivedGradient")
        measured = {}
        for prefix, value in (("displacement", displacement),
                              ("velocity", velocity),
                              ("acceleration", acceleration),
                              ("gradient", gradient),
                              ("cycleGradient", cycle_gradient)):
            measured[prefix + "Magnitude"] = float(np.linalg.norm(value))
            measured[prefix + "Radial"] = float(np.dot(value, radial))
        perpendicular_accel = math.sqrt(max(
            0.0, measured["accelerationMagnitude"] ** 2 -
            acceleration[2] ** 2))
        output = dict(row)
        output.update({
            "testIdentifier": TEST_ID,
            "directionClass": kind,
            "oppositeDetector": opposite + "_R020",
            "actualThetaDegrees": theta,
            "actualPhiDegrees": phi,
            "angularErrorDegrees": angle_error,
            "radialUnitX": radial[0], "radialUnitY": radial[1],
            "radialUnitZ": radial[2],
            "radialDisplacement": measured["displacementRadial"],
            "radialVelocity": measured["velocityRadial"],
            "radialAcceleration": measured["accelerationRadial"],
            "rawRadialGradient": measured["gradientRadial"],
            "cycleDerivedRadialGradient": measured["cycleGradientRadial"],
            "displacementMagnitude": measured["displacementMagnitude"],
            "accelerationMagnitude": measured["accelerationMagnitude"],
            "gradientMagnitude": measured["gradientMagnitude"],
            "sourceAxisAcceleration": acceleration[2],
            "transverseAccelerationMagnitude": perpendicular_accel,
        })
        for key, value in output.items():
            if key in {"detectorName", "direction", "directionClass",
                       "oppositeDetector", "cycleCompleted",
                       "testIdentifier"} or value == "":
                continue
            if not math.isfinite(float(value)):
                raise ValueError(f"non-finite {key} at {(name, update)}")
        enriched.append(output)
        if name not in geometry:
            reflected, boundary = reflected_path(coordinate)
            geometry[name] = {
                "detectorName": name, "detectorLabel": base,
                "directionClass": kind, "oppositeDetector": opposite + "_R020",
                "requestedDirectionX": int(requested[0]),
                "requestedDirectionY": int(requested[1]),
                "requestedDirectionZ": int(requested[2]),
                "requestedUnitX": requested_unit[0],
                "requestedUnitY": requested_unit[1],
                "requestedUnitZ": requested_unit[2],
                "requestedRadius": RADIUS,
                "idealOffsetX": ideal[0], "idealOffsetY": ideal[1],
                "idealOffsetZ": ideal[2],
                "integerOffsetX": int(offset[0]),
                "integerOffsetY": int(offset[1]),
                "integerOffsetZ": int(offset[2]),
                "nodeX": int(coordinate[0]), "nodeY": int(coordinate[1]),
                "nodeZ": int(coordinate[2]),
                "actualEuclideanRadius": actual_radius,
                "actualRadialUnitX": radial[0],
                "actualRadialUnitY": radial[1],
                "actualRadialUnitZ": radial[2],
                "thetaDegrees": theta, "phiDegrees": phi,
                "angularErrorDegrees": angle_error,
                "duplicateOffset": False, "placementAdjustment": "none",
                "nearestReflectionInterface": boundary,
                "reflectionEstimateUpdate": math.floor(reflected),
            }
    if len(grouped) != 26:
        raise ValueError(f"expected 26 detectors, found {len(grouped)}")
    offsets = {(g["integerOffsetX"], g["integerOffsetY"],
                g["integerOffsetZ"]) for g in geometry.values()}
    if len(offsets) != 26:
        raise ValueError("duplicate integer detector offsets")
    for name, rows in grouped.items():
        if [int(r["update"]) for r in rows] != list(range(1, UPDATES + 1)):
            raise ValueError(f"incomplete sequence {name}")
    return enriched, geometry


def cycle_analysis(enriched, geometry):
    grouped = defaultdict(list)
    for row in enriched:
        grouped[row["detectorName"]].append(row)
    cycles = []
    arrivals = {}
    safe_sets = {}
    for name, rows in grouped.items():
        completed = [row for row in rows if row["cycleCompleted"] == "true"]
        arrival_row = next((row for row in completed
                            if math.isfinite(float(
                                row["cycleDerivedRadialGradient"]))
                            and float(row["cycleDerivedRadialGradient"]) != 0),
                           None)
        if arrival_row is None:
            raise ValueError(f"no meaningful cycle-locked arrival for {name}")
        arrival_cycle = int(arrival_row["completedCycleCount"])
        arrivals[name] = (int(arrival_row["update"]), arrival_cycle)
        reflection = geometry[name]["reflectionEstimateUpdate"]
        safe_sets[name] = {cycle for cycle in range(arrival_cycle + 1,
                                                    UPDATES // PERIOD + 1)
                           if cycle * PERIOD < reflection}
        for cycle in range(1, UPDATES // PERIOD + 1):
            sample = rows[(cycle - 1) * PERIOD:cycle * PERIOD]
            if len(sample) != PERIOD:
                continue
            output = {
                "detectorName": name, "cycleNumber": cycle,
                "cycleStartUpdate": (cycle - 1) * PERIOD + 1,
                "cycleEndUpdate": cycle * PERIOD,
                "arrivalCycleNumber": arrival_cycle,
                "postArrival": cycle > arrival_cycle,
                "reflectionSafe": cycle in safe_sets[name],
            }
            for scalar in SCALARS:
                values = np.array([float(row[scalar]) for row in sample])
                peak = int(np.argmax(np.abs(values)))
                stem = scalar[0].upper() + scalar[1:]
                output[scalar + "SignedMean"] = float(np.mean(values))
                output[scalar + "Rms"] = float(np.sqrt(np.mean(values ** 2)))
                output[scalar + "Minimum"] = float(np.min(values))
                output[scalar + "Maximum"] = float(np.max(values))
                output[scalar + "PeakAbsolute"] = float(abs(values[peak]))
                output[scalar + "PeakUpdate"] = int(
                    sample[peak]["update"])
                output[scalar + "PeakPhaseDegrees"] = float(
                    sample[peak]["sourcePhaseDegrees"])
            cycles.append(output)
    common = set.intersection(*safe_sets.values())
    if not common:
        raise ValueError("no common post-arrival reflection-safe cycle")
    for row in cycles:
        row["commonPrimaryCycle"] = row["cycleNumber"] in common
    return cycles, arrivals, safe_sets, sorted(common)


def detector_primary(cycles, geometry, common):
    values = defaultdict(lambda: defaultdict(list))
    for row in cycles:
        if row["cycleNumber"] not in common:
            continue
        for metric in PRIMARY:
            values[row["detectorName"]][metric].append(float(row[metric]))
        for metric in ("radialAccelerationSignedMean",
                       "rawRadialGradientSignedMean",
                       "sourceAxisAccelerationSignedMean"):
            values[row["detectorName"]][metric].append(float(row[metric]))
    result = {}
    for name, metrics in values.items():
        result[name] = {key: float(np.mean(value))
                        for key, value in metrics.items()}
        result[name].update(geometry[name])
    return result


def summaries(primary, geometry, arrivals, safe_sets, common):
    parity = []
    done = set()
    even_metrics = PRIMARY
    odd_metrics = ("radialAccelerationSignedMean",
                   "rawRadialGradientSignedMean",
                   "sourceAxisAccelerationSignedMean")
    for name, data in primary.items():
        opposite = data["oppositeDetector"]
        pair = tuple(sorted((name, opposite)))
        if pair in done:
            continue
        done.add(pair)
        for metric in even_metrics + odd_metrics:
            first, second = data[metric], primary[opposite][metric]
            denominator = max((abs(first) + abs(second)) / 2,
                              np.finfo(float).eps)
            parity.append({
                "detector": name, "oppositeDetector": opposite,
                "metric": metric,
                "appropriateParity": "EVEN" if metric in even_metrics
                else "ODD",
                "value": first, "oppositeValue": second,
                "evenRelativeDifference": abs(first - second) / denominator,
                "oddRelativeResidual": abs(first + second) / denominator,
                "arrivalUpdateDifference":
                    arrivals[name][0] - arrivals[opposite][0],
                "reflectionMarginDifference":
                    (geometry[name]["reflectionEstimateUpdate"] -
                     max(common) * PERIOD) -
                    (geometry[opposite]["reflectionEstimateUpdate"] -
                     max(common) * PERIOD),
            })
    azimuth = []
    theta_groups = defaultdict(list)
    for data in primary.values():
        theta_groups[round(data["thetaDegrees"], 6)].append(data)
    for theta, group in sorted(theta_groups.items()):
        for metric in PRIMARY:
            vals = np.array([row[metric] for row in group])
            mean = float(np.mean(vals))
            azimuth.append({
                "thetaDegrees": theta, "detectorCount": len(group),
                "metric": metric, "groupMean": mean,
                "standardDeviation": float(np.std(vals)),
                "coefficientOfVariation":
                    float(np.std(vals) / abs(mean)) if mean else "",
                "maximumRelativeDeviation":
                    float(np.max(np.abs(vals - mean)) / abs(mean))
                    if mean else "",
                "detectors": " ".join(row["detectorName"] for row in group),
            })
    classes = []
    for kind in ("AXIS", "FACE_DIAGONAL", "BODY_DIAGONAL"):
        group = [data for data in primary.values()
                 if data["directionClass"] == kind]
        for metric in PRIMARY:
            vals = np.array([row[metric] for row in group])
            radii = np.array([row["actualEuclideanRadius"] for row in group])
            classes.append({
                "summaryType": "RAW_CLASS", "directionClass": kind,
                "thetaDegrees": "mixed", "metric": metric,
                "detectorCount": len(group), "mean": float(np.mean(vals)),
                "standardDeviation": float(np.std(vals)),
                "coefficientOfVariation":
                    float(np.std(vals) / abs(np.mean(vals)))
                    if np.mean(vals) else "",
                "minimumActualRadius": float(np.min(radii)),
                "maximumActualRadius": float(np.max(radii)),
                "meanActualRadius": float(np.mean(radii)),
            })
    for theta, group in theta_groups.items():
        by_class = defaultdict(list)
        for row in group:
            by_class[row["directionClass"]].append(row)
        for kind, members in by_class.items():
            for metric in PRIMARY:
                vals = np.array([row[metric] for row in members])
                classes.append({
                    "summaryType": "THETA_AWARE",
                    "directionClass": kind, "thetaDegrees": theta,
                    "metric": metric, "detectorCount": len(members),
                    "mean": float(np.mean(vals)),
                    "standardDeviation": float(np.std(vals)),
                    "coefficientOfVariation":
                        float(np.std(vals) / abs(np.mean(vals)))
                        if np.mean(vals) else "",
                    "minimumActualRadius": min(
                        row["actualEuclideanRadius"] for row in members),
                    "maximumActualRadius": max(
                        row["actualEuclideanRadius"] for row in members),
                    "meanActualRadius": float(np.mean([
                        row["actualEuclideanRadius"] for row in members])),
                })
    return parity, azimuth, classes


def style(ax, title, ylabel, xlabel="Actual polar angle θ (degrees)"):
    ax.set_title(title)
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, which="major", alpha=.35)


def save(fig, name):
    fig.tight_layout()
    for extension in ("png", "svg"):
        fig.savefig(CHARTS / f"{name}.{extension}", format=extension,
                    dpi=180, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def make_charts(primary, enriched, parity, classes, common):
    CHARTS.mkdir(parents=True, exist_ok=True)
    classes_markers = {"AXIS": "o", "FACE_DIAGONAL": "s",
                       "BODY_DIAGONAL": "^"}
    chart_metrics = (
        ("radialAccelerationRms", "angular-radial-acceleration",
         "Radial acceleration RMS"),
        ("accelerationMagnitudeRms", "angular-acceleration-magnitude",
         "Acceleration magnitude RMS"),
        ("rawRadialGradientRms", "angular-raw-radial-gradient",
         "Raw radial gradient RMS"),
        ("displacementMagnitudeRms", "angular-displacement-magnitude",
         "Displacement magnitude RMS"))
    for metric, name, ylabel in chart_metrics:
        fig, ax = plt.subplots(figsize=(12, 7))
        for kind, marker in classes_markers.items():
            group = [row for row in primary.values()
                     if row["directionClass"] == kind]
            ax.scatter([row["thetaDegrees"] for row in group],
                       [row[metric] for row in group], marker=marker,
                       s=80, label=kind.replace("_", " "))
        style(ax, f"Experiment 3A radius-20 angular {ylabel.lower()}",
              ylabel)
        ax.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
        ax.legend()
        save(fig, name)
    fig, ax = plt.subplots(figsize=(12, 7))
    theta_groups = defaultdict(list)
    for row in primary.values():
        theta_groups[round(row["thetaDegrees"], 6)].append(row)
    selected = sorted((theta, rows) for theta, rows in theta_groups.items()
                      if len(rows) > 1 and (abs(theta - 90) < .01
                                           or theta not in (0, 180)))
    for theta, rows in selected:
        ax.scatter([row["phiDegrees"] for row in rows],
                   [row["accelerationMagnitudeRms"] for row in rows],
                   s=60, label=f"θ={theta:.2f}°")
    style(ax, "Experiment 3A azimuthal response at equal polar angles",
          "Acceleration magnitude RMS", "Actual azimuth φ (degrees)")
    ax.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    ax.legend(ncol=2)
    save(fig, "azimuthal-response")
    for parity_type, name, metric, reference in (
            ("even", "opposite-even-parity", "accelerationMagnitudeRms", 1),
            ("odd", "opposite-odd-parity",
             "radialAccelerationSignedMean", -1)):
        rows = [row for row in parity if row["metric"] == metric]
        fig, ax = plt.subplots(figsize=(12, 7))
        x = np.array([row["value"] for row in rows])
        y = np.array([row["oppositeValue"] for row in rows])
        ax.scatter(x, y, s=70)
        span = max(np.max(np.abs(x)), np.max(np.abs(y)), 1e-300)
        line = np.array([-span, span])
        ax.plot(line, reference * line, "--", label=(
            "y=x even reference" if parity_type == "even"
            else "y=-x odd reference"))
        style(ax, f"Experiment 3A opposite-direction {parity_type} parity",
              "Opposite detector value", "Detector value")
        ax.ticklabel_format(axis="both", style="sci", scilimits=(-3, 3))
        ax.legend()
        save(fig, name)
    fig, ax = plt.subplots(figsize=(12, 7))
    raw_classes = [row for row in classes
                   if row["summaryType"] == "RAW_CLASS"
                   and row["metric"] == "accelerationMagnitudeRms"]
    ax.bar([row["directionClass"].replace("_", "\n") for row in raw_classes],
           [row["mean"] for row in raw_classes])
    style(ax, "Experiment 3A raw direction-class comparison "
              "(theta distributions differ)",
          "Mean acceleration magnitude RMS", "Direction class")
    ax.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    save(fig, "direction-class-comparison")
    representative = {"PZ_R020", "NZ_R020", "PX_R020",
                      "PXPZ_R020", "PXPYPZ_R020"}
    for field, name, ylabel in (
            ("radialAcceleration", "representative-radial-acceleration",
             "Radial acceleration"),
            ("accelerationMagnitude",
             "representative-acceleration-magnitude",
             "Acceleration magnitude")):
        fig, ax = plt.subplots(figsize=(12, 7))
        for detector in sorted(representative):
            rows = [row for row in enriched
                    if row["detectorName"] == detector]
            ax.plot([int(row["update"]) for row in rows],
                    [float(row[field]) for row in rows],
                    linewidth=1, label=detector)
        for cycle in common:
            ax.axvspan((cycle - 1) * PERIOD + 1, cycle * PERIOD,
                       color="green", alpha=.04)
        style(ax, f"Experiment 3A representative radius-20 {ylabel.lower()}",
              ylabel, "Update")
        ax.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
        ax.legend(ncol=2)
        save(fig, name)
    # A 3D axes preserves a square plotting box; use extra figure width so
    # bbox_inches="tight" still leaves at least 1600 output pixels.
    fig = plt.figure(figsize=(16, 10))
    ax = fig.add_subplot(111, projection="3d")
    values = np.array([row["accelerationMagnitudeRms"]
                       for row in primary.values()])
    scale = 60 + 300 * values / max(float(np.max(values)), 1e-300)
    xyz = np.array([[row["actualRadialUnitX"],
                     row["actualRadialUnitY"],
                     row["actualRadialUnitZ"]]
                    for row in primary.values()])
    ax.scatter(xyz[:, 0], xyz[:, 1], xyz[:, 2], s=scale,
               c=values, cmap="viridis", edgecolors="black")
    for row in primary.values():
        ax.text(row["actualRadialUnitX"], row["actualRadialUnitY"],
                row["actualRadialUnitZ"], row["detectorLabel"], fontsize=6)
    ax.set_title("Experiment 3A radius-20 directional response\n"
                 "marker size and color: acceleration magnitude RMS")
    ax.set_xlabel("Radial unit X")
    ax.set_ylabel("Radial unit Y")
    ax.set_zlabel("Radial unit Z")
    ax.grid(True)
    save(fig, "directional-response-3d")


def validate_charts():
    lines = []
    for name in CHART_NAMES:
        png, svg = CHARTS / f"{name}.png", CHARTS / f"{name}.svg"
        with Image.open(png) as image:
            image.verify()
        with Image.open(png) as image:
            image.load()
            width, height = image.size
            extrema = image.getextrema()
            mode, fmt = image.mode, image.format
        signature = png.read_bytes()[:8]
        file_result = subprocess.run(["file", str(png)],
                                     capture_output=True, text=True,
                                     check=True).stdout.strip()
        valid = (png.stat().st_size > 20_000 and width >= 1600
                 and height >= 900 and fmt == "PNG"
                 and mode in ("RGB", "RGBA")
                 and signature == b"\x89PNG\r\n\x1a\n"
                 and svg.stat().st_size > 2_000
                 and "<svg" in svg.read_text(encoding="utf-8"))
        copied = Path("/tmp") / f"experiment-3a-{name}.png"
        shutil.copy2(png, copied)
        with Image.open(copied) as image:
            image.load()
        if not valid:
            raise ValueError(f"chart validation failed: {name}")
        lines.append(
            f"{name}.png | {png.resolve()} | {png.stat().st_size} bytes | "
            f"{width}x{height} | {mode} {fmt} | signature={signature.hex()} | "
            f"extrema={extrema} | file={file_result} | SVG={svg.stat().st_size} "
            "| Pillow verify/load/tmp reopen PASS | PASS")
    (CHARTS / "chart-validation.txt").write_text(
        "\n".join(lines) + "\n", encoding="utf-8")


def report(geometry, primary, parity, azimuth, classes, arrivals,
           safe_sets, common):
    radii = [row["actualEuclideanRadius"] for row in geometry.values()]
    errors = [row["angularErrorDegrees"] for row in geometry.values()]
    even = [row["evenRelativeDifference"] for row in parity
            if row["metric"] == "accelerationMagnitudeRms"]
    odd = [row["oddRelativeResidual"] for row in parity
           if row["metric"] == "radialAccelerationSignedMean"]
    az = [float(row["coefficientOfVariation"]) for row in azimuth
          if row["metric"] == "accelerationMagnitudeRms"
          and row["coefficientOfVariation"] != ""
          and row["detectorCount"] > 1]
    first_cycle, last_cycle = min(common), max(common)
    arrival_updates = [value[0] for value in arrivals.values()]
    safe_counts = [len(value) for value in safe_sets.values()]
    class_values = {
        row["directionClass"]: row["mean"] for row in classes
        if row["summaryType"] == "RAW_CLASS"
        and row["metric"] == "accelerationMagnitudeRms"}
    text = f"""# Experiment 3A — Angular Characterization of a Single-Axis Oscillating Identity

## 1. Experiment title

Experiment 3A characterizes the angular structure of the passive node-level
response generated by one Z-axis oscillating Identity in the REM Framework.

## 2. Objective

The experiment tests directional dependence, approximate axial symmetry,
opposite-direction parity, high-symmetry direction classes, and possible
cubic-lattice anisotropy. It is not a radial power-law or inverse-square test.

## 3. Configuration

- Lattice: 161 × 161 × 161; source node: (80, 80, 80).
- Updates: {UPDATES}; source period: {PERIOD} updates.
- Fundamental Node Equation, update order, absorption interfaces (30 and 130
  on each axis), and fixed boundaries are unchanged from Gravity Test 2E.
- Primary shell: requested radius 20; 26 passive detectors; 4,680 raw rows.

## 4. Source definition

One Identity oscillates only on Z with amplitude 200 and 20 degrees per update.
No receiver Identity or Occupancy is used. Detector measurements are passive
node reads and do not represent Identity acceleration, Occupancy pressure, or
transport momentum.

## 5. Detector geometry

The shell contains 6 axis, 12 face-diagonal, and 8 body-diagonal directions.
All 26 integer offsets and nodes are unique; no placement adjustment occurred.
Each direction has exactly one explicit opposite.

## 6. Actual-radius and angular-error summary

Actual radii span {min(radii):.9g} to {max(radii):.9g} nodes because diagonal
ideal offsets were rounded independently. Angular error spans
{min(errors):.6g}° to {max(errors):.6g}°. Every radial projection uses the
normalized actual integer offset, not detector-label sign rules.

## 7. Reflection-safe data range

Cycle-locked arrivals occur from update {min(arrival_updates)} through
{max(arrival_updates)}. Detector-specific reflection paths use the nearest
first non-unity interface and a one-edge-per-update causal bound. Individual
detectors have {min(safe_counts)} to {max(safe_counts)} safe post-arrival
cycles. The common primary comparison uses {len(common)} complete cycles:
cycles {first_cycle}–{last_cycle} (updates
{(first_cycle - 1) * PERIOD + 1}–{last_cycle * PERIOD}). No partial cycle is
included.

## 8. Angular response findings

Acceleration-magnitude RMS varies with actual polar angle and direction. The
raw class means are: {', '.join(f'{key}={value:.6e}' for key, value in class_values.items())}.
These class means are descriptive only because their theta distributions differ.

## 9. Opposite-direction parity findings

For acceleration-magnitude RMS (even parity), the maximum paired relative
difference is {max(even):.6g}. For signed radial-acceleration cycle means (odd
parity), the maximum odd residual is {max(odd):.6g}. RMS and magnitude metrics
use even-parity comparison; signed radial/Cartesian means use odd-parity
comparison when opposite signs are the relevant hypothesis.

## 10. Azimuthal-symmetry findings

Within equal-actual-theta groups, acceleration-magnitude RMS coefficients of
variation range from {min(az):.6g} to {max(az):.6g}. Equal-theta azimuthal
agreement is evidence about axial symmetry around Z, not spherical isotropy.

## 11. Direction-class findings

Raw AXIS, FACE_DIAGONAL, and BODY_DIAGONAL summaries are reported separately
from theta-aware groups in `direction-class-summary.csv`. Differences across
raw classes cannot be attributed solely to cubic anisotropy because theta and
the small actual-radius differences are confounders.

## 12. Representative time-series findings

PZ, NZ, PX, one XZ face diagonal, and one XYZ body diagonal are plotted for
radial acceleration and acceleration magnitude. Green shading marks the common
complete reflection-safe cycles.

## 13. Limitations

This is one requested shell with integer-rounded diagonal placements and only
{len(common)} common safe cycles. Source polarization and cubic geometry are
not experimentally separable here. Cycle-locked arrival is quantized to
{PERIOD}-update boundaries. The analysis does not measure attraction, orbital
motion, or a gravitational field law.

## 14. Conclusions

Experiment 3A measures a directionally structured passive lattice response
from a Z-axis oscillator. The quantitative parity and equal-theta azimuthal
tables determine the degree of opposite-direction and axial symmetry present
in this run; the class tables expose high-symmetry differences while retaining
their theta and radius confounders. These observations characterize angular
geometry only and do not establish spherical isotropy, inverse-square behavior,
gravitational equivalence, or a monopole field.
"""
    (OUT / "report.md").write_text(text, encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    raw = read_csv(RAW)
    enriched, geometry = geometry_and_timeseries(raw)
    cycles, arrivals, safe_sets, common = cycle_analysis(
        enriched, geometry)
    primary = detector_primary(cycles, geometry, common)
    parity, azimuth, classes = summaries(
        primary, geometry, arrivals, safe_sets, common)
    write_csv(OUT / "detector-timeseries.csv", enriched)
    write_csv(OUT / "detector-geometry.csv",
              [geometry[name] for name in sorted(geometry)])
    write_csv(OUT / "detector-cycle-summary.csv", cycles)
    write_csv(OUT / "opposite-pair-parity.csv", parity)
    write_csv(OUT / "azimuthal-symmetry.csv", azimuth)
    write_csv(OUT / "direction-class-summary.csv", classes)
    make_charts(primary, enriched, parity, classes, common)
    validate_charts()
    report(geometry, primary, parity, azimuth, classes, arrivals,
           safe_sets, common)
    print(f"PASS: 26 detectors, {len(enriched)} rows, "
          f"{len(common)} common safe cycles {common}")


if __name__ == "__main__":
    main()
