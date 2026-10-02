#!/usr/bin/env python3
"""Combined three-axis rotational analysis for Experiment 3B."""

import csv
import importlib.util
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

MODULE_PATH = Path(__file__).with_name(
    "analyze-experiment-3a-angular-characterization.py")
SPEC = importlib.util.spec_from_file_location("experiment3a_analysis",
                                               MODULE_PATH)
A3 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(A3)

ROOT = Path("measurements/experiment-3b-rotational-consistency")
COMBINED = ROOT / "combined"
CHARTS = ROOT / "charts"
PERIOD = 18
UPDATES = 180
AXES = {
    "X": (np.array([1., 0., 0.]), np.array([0., 1., 0.]),
          np.array([0., 0., 1.])),
    "Y": (np.array([0., 1., 0.]), np.array([0., 0., 1.]),
          np.array([1., 0., 0.])),
    "Z": (np.array([0., 0., 1.]), np.array([1., 0., 0.]),
          np.array([0., 1., 0.])),
}
SOURCE_DIRS = {
    axis: Path("measurements") / f"experiment-3b-rotation-{axis.lower()}"
    for axis in AXES
}
METRICS = (
    "accelerationMagnitudeRms", "radialAccelerationRms",
    "radialAccelerationSignedMean", "displacementMagnitudeRms",
    "rawRadialGradientRms", "gradientMagnitudeRms",
    "sourceLongitudinalAccelerationRms",
    "sourceTransverseAccelerationMagnitudeRms")
MAGNITUDE_METRICS = tuple(metric for metric in METRICS
                          if metric != "radialAccelerationSignedMean")
CHART_NAMES = (
    "source-relative-angular-overlay",
    "source-relative-directional-comparison",
    "rotational-residual-by-direction",
    "lattice-fixed-axis-response",
    "longitudinal-transverse-response",
    "opposite-parity-by-source",
    "directional-scatter-x", "directional-scatter-y",
    "directional-scatter-z", "difference-x-minus-z",
    "difference-y-minus-z", "representative-rotated-time-series",
    "runtime-memory-comparison")


def write_csv(path, rows):
    if not rows:
        raise ValueError(f"empty output {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def canonical(direction, axis):
    longitudinal, u, v = AXES[axis]
    return tuple(int(round(float(np.dot(direction, basis))))
                 for basis in (longitudinal, u, v))


def canonical_key(value):
    return f"L{value[0]:+d}_U{value[1]:+d}_V{value[2]:+d}"


def source_angles(radial, axis):
    longitudinal, u, v = AXES[axis]
    polar = math.degrees(math.acos(np.clip(
        float(np.dot(radial, longitudinal)), -1, 1)))
    azimuth = math.degrees(math.atan2(float(np.dot(radial, v)),
                                      float(np.dot(radial, u))))
    if azimuth >= 180:
        azimuth -= 360
    return polar, azimuth


def augment(enriched, axis):
    longitudinal = AXES[axis][0]
    for row in enriched:
        acceleration = A3.vec(row, "acceleration")
        displacement = A3.vec(row, "positionDeviation")
        velocity = A3.vec(row, "velocity")
        gradient = A3.vec(row, "rawGradient")
        radial = np.array([float(row[f"radialUnit{a}"]) for a in "XYZ"])
        polar, azimuth = source_angles(radial, axis)
        row.update({
            "runIdentifier": f"run-{axis.lower()}",
            "sourceAxis": axis,
            "sourceRelativePolarDegrees": polar,
            "sourceRelativeAzimuthDegrees": azimuth,
            "sourceLongitudinalDisplacement":
                float(np.dot(displacement, longitudinal)),
            "sourceTransverseDisplacementMagnitude": math.sqrt(max(
                0., float(np.dot(displacement, displacement)) -
                float(np.dot(displacement, longitudinal)) ** 2)),
            "sourceLongitudinalVelocity":
                float(np.dot(velocity, longitudinal)),
            "sourceTransverseVelocityMagnitude": math.sqrt(max(
                0., float(np.dot(velocity, velocity)) -
                float(np.dot(velocity, longitudinal)) ** 2)),
            "sourceAxisAcceleration":
                float(np.dot(acceleration, longitudinal)),
            "sourceLongitudinalAcceleration":
                float(np.dot(acceleration, longitudinal)),
            "transverseAccelerationMagnitude": math.sqrt(max(
                0., float(np.dot(acceleration, acceleration)) -
                float(np.dot(acceleration, longitudinal)) ** 2)),
            "sourceTransverseAccelerationMagnitude": math.sqrt(max(
                0., float(np.dot(acceleration, acceleration)) -
                float(np.dot(acceleration, longitudinal)) ** 2)),
            "sourceLongitudinalGradient":
                float(np.dot(gradient, longitudinal)),
            "sourceTransverseGradientMagnitude": math.sqrt(max(
                0., float(np.dot(gradient, gradient)) -
                float(np.dot(gradient, longitudinal)) ** 2)),
        })


def summarize_primary(cycles, common):
    result = defaultdict(lambda: defaultdict(list))
    mapping = {
        "accelerationMagnitudeRms": "accelerationMagnitudeRms",
        "radialAccelerationRms": "radialAccelerationRms",
        "radialAccelerationSignedMean": "radialAccelerationSignedMean",
        "displacementMagnitudeRms": "displacementMagnitudeRms",
        "rawRadialGradientRms": "rawRadialGradientRms",
        "gradientMagnitudeRms": "gradientMagnitudeRms",
        "sourceLongitudinalAccelerationRms":
            "sourceAxisAccelerationRms",
        "sourceTransverseAccelerationMagnitudeRms":
            "transverseAccelerationMagnitudeRms",
    }
    for row in cycles:
        if int(row["cycleNumber"]) not in common:
            continue
        for output, source in mapping.items():
            result[row["detectorName"]][output].append(float(row[source]))
    return {detector: {metric: float(np.mean(values))
                       for metric, values in metrics.items()}
            for detector, metrics in result.items()}


def per_run_tables(axis, enriched, geometry, cycles, arrivals, safe_sets,
                   primary):
    run_dir = ROOT / f"run-{axis.lower()}"
    run_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SOURCE_DIRS[axis] / "raw-detectors-run-001.csv",
                 run_dir / "raw-detectors-run-001.csv")
    write_csv(run_dir / "detector-timeseries.csv", enriched)
    write_csv(run_dir / "detector-cycle-summary.csv", cycles)
    parity = []
    completed = set()
    for name, metrics in primary.items():
        opposite = geometry[name]["oppositeDetector"]
        pair = tuple(sorted((name, opposite)))
        if pair in completed:
            continue
        completed.add(pair)
        for metric in ("accelerationMagnitudeRms",
                       "radialAccelerationSignedMean",
                       "rawRadialGradientRms"):
            first, second = metrics[metric], primary[opposite][metric]
            denominator = max((abs(first) + abs(second)) / 2,
                              np.finfo(float).eps)
            parity.append({
                "sourceAxis": axis, "detector": name,
                "oppositeDetector": opposite, "metric": metric,
                "value": first, "oppositeValue": second,
                "evenRelativeDifference": abs(first - second) / denominator,
                "oddRelativeResidual": abs(first + second) / denominator,
            })
    azimuth = []
    groups = defaultdict(list)
    for name, metrics in primary.items():
        radial = np.array([geometry[name][f"actualRadialUnit{a}"]
                           for a in "XYZ"])
        polar, az = source_angles(radial, axis)
        groups[round(polar, 6)].append((name, az, metrics))
    for polar, group in sorted(groups.items()):
        for metric in MAGNITUDE_METRICS:
            values = np.array([item[2][metric] for item in group])
            mean = float(np.mean(values))
            azimuth.append({
                "sourceAxis": axis, "sourcePolarDegrees": polar,
                "detectorCount": len(group), "metric": metric,
                "mean": mean, "standardDeviation": float(np.std(values)),
                "coefficientOfVariation":
                    float(np.std(values) / abs(mean)) if mean else 0.0,
                "maximumRelativeDeviation":
                    float(np.max(np.abs(values - mean)) / abs(mean))
                    if mean else 0.0,
                "detectors": " ".join(item[0] for item in group),
            })
    class_rows = []
    for kind in ("AXIS", "FACE_DIAGONAL", "BODY_DIAGONAL"):
        members = [(name, value) for name, value in primary.items()
                   if geometry[name]["directionClass"] == kind]
        for metric in MAGNITUDE_METRICS:
            values = np.array([value[metric] for _, value in members])
            class_rows.append({
                "sourceAxis": axis, "summaryType": "FIXED_CLASS",
                "directionClass": kind, "sourcePolarDegrees": "mixed",
                "metric": metric, "detectorCount": len(values),
                "mean": float(np.mean(values)),
                "standardDeviation": float(np.std(values)),
            })
        polar_members = defaultdict(list)
        for name, value in members:
            radial = np.array([geometry[name][f"actualRadialUnit{a}"]
                               for a in "XYZ"])
            polar_members[round(source_angles(radial, axis)[0], 6)].append(
                value)
        for polar, values_by_detector in polar_members.items():
            for metric in MAGNITUDE_METRICS:
                values = np.array([value[metric]
                                   for value in values_by_detector])
                class_rows.append({
                    "sourceAxis": axis, "summaryType": "POLAR_AWARE",
                    "directionClass": kind, "sourcePolarDegrees": polar,
                    "metric": metric, "detectorCount": len(values),
                    "mean": float(np.mean(values)),
                    "standardDeviation": float(np.std(values)),
                })
    write_csv(run_dir / "opposite-pair-parity.csv", parity)
    write_csv(run_dir / "azimuthal-symmetry.csv", azimuth)
    write_csv(run_dir / "direction-class-summary.csv", class_rows)
    shutil.copy2(SOURCE_DIRS[axis] / "runtime-memory-telemetry.csv",
                 run_dir / "runtime-memory-telemetry.csv")
    return parity, azimuth, class_rows


def load_runs():
    run_data = {}
    shared_geometry = None
    for axis in AXES:
        raw_path = SOURCE_DIRS[axis] / "raw-detectors-run-001.csv"
        raw = A3.read_csv(raw_path)
        enriched, geometry = A3.geometry_and_timeseries(raw)
        augment(enriched, axis)
        cycles, arrivals, safe_sets, common = A3.cycle_analysis(
            enriched, geometry)
        if shared_geometry is None:
            shared_geometry = geometry
        elif {(name, row["integerOffsetX"], row["integerOffsetY"],
               row["integerOffsetZ"]) for name, row in geometry.items()} != {
                   (name, row["integerOffsetX"], row["integerOffsetY"],
                    row["integerOffsetZ"])
                   for name, row in shared_geometry.items()}:
            raise ValueError("detector geometry differs between runs")
        run_data[axis] = {
            "enriched": enriched, "geometry": geometry, "cycles": cycles,
            "arrivals": arrivals, "safeSets": safe_sets,
            "commonWithinRun": set(common)}
    all_safe = []
    for data in run_data.values():
        all_safe.extend(data["safeSets"].values())
    common = sorted(set.intersection(*all_safe))
    if not common:
        raise ValueError("no complete reflection-safe cycle common to all runs")
    for axis, data in run_data.items():
        data["primary"] = summarize_primary(data["cycles"], common)
        data["tables"] = per_run_tables(
            axis, data["enriched"], data["geometry"], data["cycles"],
            data["arrivals"], data["safeSets"], data["primary"])
    return run_data, shared_geometry, common


def comparisons(run_data, geometry):
    direction_specs = A3.directions()
    maps = []
    by_canonical = defaultdict(dict)
    for axis, data in run_data.items():
        for name, values in data["primary"].items():
            base = name.removesuffix("_R020")
            requested = direction_specs[base][0]
            key_tuple = canonical(requested, axis)
            key = canonical_key(key_tuple)
            radial = np.array([geometry[name][f"actualRadialUnit{a}"]
                               for a in "XYZ"])
            polar, azimuth = source_angles(radial, axis)
            maps.append({
                "sourceAxis": axis, "detectorName": name,
                "canonicalDirectionKey": key,
                "canonicalLongitudinal": key_tuple[0],
                "canonicalTransverseU": key_tuple[1],
                "canonicalTransverseV": key_tuple[2],
                "directionClass": geometry[name]["directionClass"],
                "sourceRelativePolarDegrees": polar,
                "sourceRelativeAzimuthDegrees": azimuth,
                "oppositeDetector": geometry[name]["oppositeDetector"],
            })
            by_canonical[key][axis] = (name, values, polar, azimuth,
                                      geometry[name]["directionClass"])
    comparison = []
    residuals = []
    for key, orientations in sorted(by_canonical.items()):
        if set(orientations) != set(AXES):
            raise ValueError(f"incomplete rotation group {key}")
        for metric in METRICS:
            values = np.array([orientations[a][1][metric] for a in AXES])
            mean = float(np.mean(values))
            std = float(np.std(values))
            denominator = max(abs(mean), np.finfo(float).eps)
            row = {
                "canonicalDirectionKey": key,
                "directionClass": orientations["Z"][4],
                "sourceRelativePolarDegrees": orientations["Z"][2],
                "sourceRelativeAzimuthDegrees": orientations["Z"][3],
                "metric": metric,
                "xDetector": orientations["X"][0],
                "yDetector": orientations["Y"][0],
                "zDetector": orientations["Z"][0],
                "xValue": values[0], "yValue": values[1],
                "zValue": values[2], "mean": mean,
                "standardDeviation": std,
                "coefficientOfVariation": std / denominator,
                "maximumRelativeSpread":
                    float(np.max(values) - np.min(values)) / denominator,
                "xVersusY": abs(values[0] - values[1]) /
                    max((abs(values[0]) + abs(values[1])) / 2,
                        np.finfo(float).eps),
                "yVersusZ": abs(values[1] - values[2]) /
                    max((abs(values[1]) + abs(values[2])) / 2,
                        np.finfo(float).eps),
                "zVersusX": abs(values[2] - values[0]) /
                    max((abs(values[2]) + abs(values[0])) / 2,
                        np.finfo(float).eps),
                "signedComparisonPreserved":
                    metric != "radialAccelerationSignedMean" or
                    all(math.isfinite(value) for value in values),
            }
            comparison.append(row)
            residuals.append(dict(row))
    fixed = []
    for detector in sorted(geometry):
        for metric in METRICS:
            values = [run_data[axis]["primary"][detector][metric]
                      for axis in AXES]
            fixed.append({
                "detectorName": detector,
                "directionClass": geometry[detector]["directionClass"],
                "metric": metric, "xSourceValue": values[0],
                "ySourceValue": values[1], "zSourceValue": values[2],
                "mean": float(np.mean(values)),
                "coefficientOfVariation": float(np.std(values)) /
                    max(abs(float(np.mean(values))), np.finfo(float).eps),
            })
    return maps, comparison, fixed, residuals


def telemetry_summary():
    rows = []
    for axis in AXES:
        telemetry = A3.read_csv(
            ROOT / f"run-{axis.lower()}/runtime-memory-telemetry.csv")
        updates = [row for row in telemetry if row["event"] == "update"]
        complete = next(row for row in telemetry
                        if row["event"] == "simulation-complete")
        lattice = next(row for row in telemetry
                       if row["event"] == "lattice-initialized")
        durations = [float(row["updateMilliseconds"]) for row in updates]
        rows.append({
            "sourceAxis": axis,
            "latticeInitializationMilliseconds":
                lattice["elapsedMilliseconds"],
            "simulationMilliseconds": complete["elapsedMilliseconds"],
            "averageUpdateMilliseconds": float(np.mean(durations)),
            "medianUpdateMilliseconds": float(np.median(durations)),
            "minimumUpdateMilliseconds": min(durations),
            "maximumUpdateMilliseconds": max(durations),
            "peakUsedHeapBytes": complete["peakUsedHeapBytes"],
            "gcCount": complete["gcCount"],
            "gcTimeMilliseconds": complete["gcTimeMilliseconds"],
        })
    return rows


def style(ax, title, xlabel, ylabel):
    ax.set_title(title)
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, which="major", alpha=.35)


def save(fig, name):
    fig.tight_layout()
    fig.savefig(CHARTS / f"{name}.png", format="png", dpi=180,
                bbox_inches="tight", facecolor="white")
    fig.savefig(CHARTS / f"{name}.svg", format="svg", dpi=180,
                bbox_inches="tight", facecolor="white")
    plt.close(fig)


def charts(run_data, geometry, comparison, fixed, telemetry, common):
    CHARTS.mkdir(parents=True, exist_ok=True)
    fig, ax = plt.subplots(figsize=(12, 7))
    for axis in AXES:
        points = []
        for name, value in run_data[axis]["primary"].items():
            radial = np.array([geometry[name][f"actualRadialUnit{a}"]
                               for a in "XYZ"])
            points.append((source_angles(radial, axis)[0],
                           value["accelerationMagnitudeRms"]))
        ax.scatter(*zip(*points), s=55, label=f"{axis} source")
    style(ax, "Experiment 3B source-relative angular overlay",
          "Source-relative polar angle (degrees)",
          "Acceleration magnitude RMS")
    ax.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    ax.legend()
    save(fig, CHART_NAMES[0])
    rows = [row for row in comparison
            if row["metric"] == "accelerationMagnitudeRms"]
    positions = np.arange(len(rows))
    fig, ax = plt.subplots(figsize=(16, 7))
    width = .25
    for i, (axis, field) in enumerate(
            (("X", "xValue"), ("Y", "yValue"), ("Z", "zValue"))):
        ax.bar(positions + (i - 1) * width,
               [row[field] for row in rows], width, label=axis)
    ax.set_xticks(positions)
    ax.set_xticklabels([row["canonicalDirectionKey"] for row in rows],
                       rotation=65, ha="right", fontsize=8)
    style(ax, "Source-relative acceleration response by canonical direction",
          "Canonical direction", "Acceleration magnitude RMS")
    ax.legend()
    save(fig, CHART_NAMES[1])
    fig, ax = plt.subplots(figsize=(16, 7))
    ax.bar(positions, [row["coefficientOfVariation"] for row in rows])
    worst = max(rows, key=lambda row: row["coefficientOfVariation"])
    ax.annotate(f"Worst: {worst['canonicalDirectionKey']}",
                (rows.index(worst), worst["coefficientOfVariation"]),
                xytext=(10, 15), textcoords="offset points")
    ax.set_xticks(positions)
    ax.set_xticklabels([row["canonicalDirectionKey"] for row in rows],
                       rotation=65, ha="right", fontsize=8)
    style(ax, "Rotational coefficient of variation by direction",
          "Canonical direction", "Coefficient of variation")
    save(fig, CHART_NAMES[2])
    axes_detectors = {"PX_R020", "NX_R020", "PY_R020", "NY_R020",
                      "PZ_R020", "NZ_R020"}
    fixed_rows = [row for row in fixed
                  if row["metric"] == "accelerationMagnitudeRms"
                  and row["detectorName"] in axes_detectors]
    fixed_rows.sort(key=lambda row: row["detectorName"])
    fig, ax = plt.subplots(figsize=(12, 7))
    x = np.arange(len(fixed_rows))
    for i, (axis, field) in enumerate(
            (("X", "xSourceValue"), ("Y", "ySourceValue"),
             ("Z", "zSourceValue"))):
        ax.bar(x + (i - 1) * .25, [row[field] for row in fixed_rows],
               .25, label=f"{axis} source")
    ax.set_xticks(x)
    ax.set_xticklabels([row["detectorName"] for row in fixed_rows])
    style(ax, "Lattice-fixed axis detector response", "Detector",
          "Acceleration magnitude RMS")
    ax.legend()
    save(fig, CHART_NAMES[3])
    fig, ax = plt.subplots(figsize=(12, 7))
    for axis in AXES:
        polar, long_values, transverse = [], [], []
        for name, value in run_data[axis]["primary"].items():
            radial = np.array([geometry[name][f"actualRadialUnit{a}"]
                               for a in "XYZ"])
            polar.append(source_angles(radial, axis)[0])
            long_values.append(value["sourceLongitudinalAccelerationRms"])
            transverse.append(
                value["sourceTransverseAccelerationMagnitudeRms"])
        ax.scatter(polar, long_values, marker="o", label=f"{axis} longitudinal")
        ax.scatter(polar, transverse, marker="x", label=f"{axis} transverse")
    style(ax, "Source-longitudinal and transverse acceleration",
          "Source-relative polar angle (degrees)", "Cycle RMS")
    ax.legend(ncol=2)
    save(fig, CHART_NAMES[4])
    fig, ax = plt.subplots(figsize=(12, 7))
    parity_metrics = defaultdict(list)
    for axis, data in run_data.items():
        for row in data["tables"][0]:
            if row["metric"] == "accelerationMagnitudeRms":
                parity_metrics[axis].append(float(
                    row["evenRelativeDifference"]))
            elif row["metric"] == "radialAccelerationSignedMean":
                parity_metrics[axis].append(float(row["oddRelativeResidual"]))
    ax.boxplot([parity_metrics[a] for a in AXES], tick_labels=list(AXES))
    style(ax, "Opposite-direction parity residuals by source",
          "Source axis", "Relative residual")
    save(fig, CHART_NAMES[5])
    all_accel = [value["accelerationMagnitudeRms"]
                 for data in run_data.values()
                 for value in data["primary"].values()]
    vmin, vmax = min(all_accel), max(all_accel)
    for axis in AXES:
        fig = plt.figure(figsize=(16, 10))
        ax = fig.add_subplot(111, projection="3d")
        xyz, values = [], []
        for name, value in run_data[axis]["primary"].items():
            radial = np.array([geometry[name][f"actualRadialUnit{a}"]
                               for a in "XYZ"])
            basis = AXES[axis]
            xyz.append([np.dot(radial, b) for b in basis])
            values.append(value["accelerationMagnitudeRms"])
        xyz, values = np.array(xyz), np.array(values)
        sizes = 50 + 250 * (values - vmin) / max(vmax - vmin, 1e-300)
        ax.scatter(xyz[:, 0], xyz[:, 1], xyz[:, 2], s=sizes, c=values,
                   vmin=vmin, vmax=vmax, cmap="viridis", edgecolors="black")
        ax.set(xlim=(-1.1, 1.1), ylim=(-1.1, 1.1), zlim=(-1.1, 1.1))
        ax.view_init(elev=25, azim=-55)
        ax.set_title(f"{axis}-source response in source-relative coordinates")
        ax.set_xlabel("Longitudinal")
        ax.set_ylabel("Transverse U")
        ax.set_zlabel("Transverse V")
        save(fig, f"directional-scatter-{axis.lower()}")
    for axis, name in (("X", "difference-x-minus-z"),
                       ("Y", "difference-y-minus-z")):
        fig, ax = plt.subplots(figsize=(16, 7))
        differences = [float(row[f"{axis.lower()}Value"])
                       - float(row["zValue"]) for row in rows]
        ax.bar(positions, differences)
        ax.axhline(0, color="black", linewidth=1)
        ax.set_xticks(positions)
        ax.set_xticklabels([row["canonicalDirectionKey"] for row in rows],
                           rotation=65, ha="right", fontsize=8)
        style(ax, f"{axis} minus rotated Z acceleration response",
              "Canonical direction", "Acceleration magnitude RMS difference")
        save(fig, name)
    fig, ax = plt.subplots(figsize=(12, 7))
    matches = (("longitudinal", {"X": "PX_R020", "Y": "PY_R020",
                                 "Z": "PZ_R020"}),
               ("transverse-U", {"X": "PY_R020", "Y": "PZ_R020",
                                  "Z": "PX_R020"}),
               ("diagonal", {"X": "PXPYPZ_R020", "Y": "PXPYPZ_R020",
                              "Z": "PXPYPZ_R020"}))
    for group, detectors in matches:
        for axis, detector in detectors.items():
            values = [float(row["accelerationMagnitude"])
                      for row in run_data[axis]["enriched"]
                      if row["detectorName"] == detector]
            ax.plot(range(1, UPDATES + 1), values, linewidth=.9,
                    label=f"{group} {axis}/{detector}")
    for cycle in common:
        ax.axvspan((cycle - 1) * PERIOD + 1, cycle * PERIOD,
                   color="green", alpha=.03)
    style(ax, "Representative matched source-relative time series",
          "Update", "Acceleration magnitude")
    ax.legend(ncol=2, fontsize=7)
    save(fig, CHART_NAMES[11])
    fig, ax = plt.subplots(figsize=(12, 7))
    x = np.arange(3)
    update_ms = [float(row["averageUpdateMilliseconds"])
                 for row in telemetry]
    peak_gib = [float(row["peakUsedHeapBytes"]) / 2**30
                for row in telemetry]
    ax.bar(x - .18, update_ms, .36, label="Average update ms")
    ax.bar(x + .18, peak_gib, .36, label="Peak heap GiB")
    ax.set_xticks(x)
    ax.set_xticklabels(list(AXES))
    style(ax, "Runtime and memory comparison (different units)",
          "Source axis", "Measured value")
    ax.legend()
    save(fig, CHART_NAMES[12])


def validate_charts():
    lines = []
    for name in CHART_NAMES:
        png, svg = CHARTS / f"{name}.png", CHARTS / f"{name}.svg"
        with Image.open(png) as image:
            image.verify()
        with Image.open(png) as image:
            image.load()
            width, height = image.size
            extrema, mode, fmt = image.getextrema(), image.mode, image.format
        copied = Path("/tmp") / f"experiment-3b-{name}.png"
        shutil.copy2(png, copied)
        with Image.open(copied) as image:
            image.load()
        file_result = subprocess.run(["file", str(png)], check=True,
                                     capture_output=True,
                                     text=True).stdout.strip()
        valid = (png.stat().st_size > 20_000 and width >= 1600
                 and height >= 900 and fmt == "PNG"
                 and png.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
                 and svg.stat().st_size > 2_000
                 and "<svg" in svg.read_text(encoding="utf-8"))
        if not valid:
            raise ValueError(f"chart validation failed: {name}")
        lines.append(f"{name}.png | {width}x{height} | {mode} {fmt} | "
                     f"{png.stat().st_size} bytes | extrema={extrema} | "
                     f"{file_result} | SVG={svg.stat().st_size} | PASS")
    (CHARTS / "chart-validation.txt").write_text(
        "\n".join(lines) + "\n", encoding="utf-8")


def make_report(run_data, geometry, common, residuals, telemetry):
    magnitude = [row for row in residuals
                 if row["metric"] in MAGNITUDE_METRICS]
    worst = max(magnitude, key=lambda row: row["coefficientOfVariation"])
    primary = [row for row in residuals
               if row["metric"] == "accelerationMagnitudeRms"]
    parity_lines = []
    for axis, data in run_data.items():
        rows = data["tables"][0]
        acceleration = max(float(row["evenRelativeDifference"])
                           for row in rows
                           if row["metric"] == "accelerationMagnitudeRms")
        signed = max(float(row["oddRelativeResidual"]) for row in rows
                     if row["metric"] == "radialAccelerationSignedMean")
        gradient = max(float(row["evenRelativeDifference"]) for row in rows
                       if row["metric"] == "rawRadialGradientRms")
        parity_lines.append(
            f"- {axis}: acceleration even={acceleration:.6g}; "
            f"signed radial acceleration odd={signed:.6g}; "
            f"raw radial gradient even={gradient:.6g}.")
    telemetry_lines = [
        f"- {row['sourceAxis']}: {float(row['simulationMilliseconds'])/1000:.3f} s, "
        f"{float(row['averageUpdateMilliseconds']):.3f} ms/update, "
        f"{float(row['peakUsedHeapBytes'])/2**30:.3f} GiB peak heap."
        for row in telemetry]
    arrival_lines = []
    for axis, data in run_data.items():
        arrivals = [value[0] for value in data["arrivals"].values()]
        safe_counts = [len(value) for value in data["safeSets"].values()]
        arrival_lines.append(
            f"- {axis}: meaningful arrivals {min(arrivals)}–{max(arrivals)}; "
            f"per-detector safe cycles {min(safe_counts)}–"
            f"{max(safe_counts)}; within-run common cycles "
            f"{min(data['commonWithinRun'])}–"
            f"{max(data['commonWithinRun'])}.")
    fixed_primary = [row for row in residuals
                     if row["metric"] == "accelerationMagnitudeRms"]
    text = f"""# Experiment 3B — Rotational Consistency of the Angular Response

## 1. Experiment title

Experiment 3B tests discrete X/Y/Z rotational consistency of the passive
angular response in the REM Framework.

## 2. Objective

The question is whether the measured pattern follows the active oscillator
axis, remains fixed to lattice coordinates, or exposes orientation-specific
implementation behavior. This is not a radial-law experiment.

## 3. Configuration

All runs use a 161³ lattice, source (80,80,80), requested radius 20, the same
26 detector nodes, amplitude 200, 20° per update, period 18, and 180 updates.
The Fundamental Node Equation, update order, transport, boundaries, and
absorption are unchanged from Experiment 3A.

## 4. Run definitions

X, Y, and Z runs use amplitudes (200,0,0), (0,200,0), and (0,0,200).
Only the active axis, phase-increment axis, and amplitude component differ.

## 5. Detector geometry

The shared geometry has {len(geometry)} unique nodes: 6 axis, 12 face-diagonal,
and 8 body-diagonal directions. Actual radii and lattice-coordinate theta/phi
are retained unchanged across all runs.

## 6. Rotation convention

Canonical coordinates are `(longitudinal,U,V)`. Bases are X→(X,Y,Z),
Y→(Y,Z,X), and Z→(Z,X,Y). This exact cyclic signed-coordinate permutation is
invertible, norm-preserving, class-preserving, and opposite-preserving.

## 7. Reflection-safe cycle intersection

The all-run, all-detector intersection contains {len(common)} complete cycles:
{min(common)}–{max(common)}, updates {(min(common)-1)*PERIOD+1}–
{max(common)*PERIOD}. Only these cycles feed cross-run comparisons.

{chr(10).join(arrival_lines)}

## 8. Per-run parity findings

{chr(10).join(parity_lines)}

## 9. Source-relative angular comparison

For acceleration-magnitude RMS, median rotational CV is
{np.median([row['coefficientOfVariation'] for row in primary]):.6g} and the
maximum is {max(row['coefficientOfVariation'] for row in primary):.6g}.
The overlay and canonical-direction CSV retain all individual values.

## 10. Rotational residual findings

Across magnitude/RMS metrics, the worst CV is
{worst['coefficientOfVariation']:.6g} for `{worst['metric']}` at
`{worst['canonicalDirectionKey']}`; its maximum relative spread is
{worst['maximumRelativeSpread']:.6g}. Categories are descriptive, not proof
thresholds.

## 11. Lattice-fixed comparison

`lattice-fixed-comparison.csv` compares each unchanged physical detector under
all three sources. It is separate from the rotated canonical comparison so
source-following and lattice-fixed patterns are not conflated.

## 12. Longitudinal/transverse comparison

Source-longitudinal and source-transverse acceleration RMS are retained
separately from radial acceleration. The chart reports both against
source-relative polar angle.

## 13. Azimuthal-symmetry comparison

Each run is grouped by equal source-relative polar angle using its own
documented transverse basis. Per-run means, variation, and detector membership
are preserved in the run directories.

## 14. Direction-class findings

Both fixed AXIS/FACE_DIAGONAL/BODY_DIAGONAL summaries and source-polar-aware
summaries are reported. Raw class differences remain confounded by polar angle
and small diagonal radius differences.

## 15. Runtime and memory comparison

{chr(10).join(telemetry_lines)}

## 16. Limitations

Only three orthogonal lattice-axis orientations and {len(common)} common safe
cycles are tested. This cannot establish continuous rotational invariance.
Integer-rounded shell radii and cubic propagation remain possible corrections.

## 17. Conclusions

Within the two common reflection-safe cycles, the X/Y/Z source-relative
acceleration-magnitude patterns are numerically consistent under the exact
cyclic axis permutation: maximum CV
{max(row['coefficientOfVariation'] for row in fixed_primary):.6g}.
The lattice-fixed acceleration-magnitude comparison is likewise numerically
consistent across source axes, while direction-class structure remains in the
shared cubic detector geometry. No orientation-specific implementation
asymmetry is resolved at this precision. This establishes consistency only
under the three tested discrete lattice-axis rotations; it does not establish
continuous rotational invariance, spherical isotropy, inverse-square behavior,
gravitational equivalence, or a monopole field.
"""
    (COMBINED / "report.md").write_text(text, encoding="utf-8")


def main():
    COMBINED.mkdir(parents=True, exist_ok=True)
    run_data, geometry, common = load_runs()
    geometry_rows = [geometry[name] for name in sorted(geometry)]
    write_csv(ROOT / "detector-geometry.csv", geometry_rows)
    maps, comparison, fixed, residuals = comparisons(run_data, geometry)
    write_csv(COMBINED / "source-relative-rotation-map.csv", maps)
    write_csv(COMBINED / "source-relative-comparison.csv", comparison)
    write_csv(COMBINED / "lattice-fixed-comparison.csv", fixed)
    write_csv(COMBINED / "rotational-residuals.csv", residuals)
    magnitude = [row for row in residuals
                 if row["metric"] in MAGNITUDE_METRICS]
    summary = []
    for metric in METRICS:
        rows = [row for row in residuals if row["metric"] == metric]
        worst = max(rows, key=lambda row: row["coefficientOfVariation"])
        summary.append({
            "metric": metric, "directionCount": len(rows),
            "medianCoefficientOfVariation": float(np.median(
                [row["coefficientOfVariation"] for row in rows])),
            "maximumCoefficientOfVariation":
                worst["coefficientOfVariation"],
            "medianMaximumRelativeSpread": float(np.median(
                [row["maximumRelativeSpread"] for row in rows])),
            "maximumRelativeSpread": max(
                row["maximumRelativeSpread"] for row in rows),
            "worstCanonicalDirection": worst["canonicalDirectionKey"],
            "systematicallyHighestOrientation": max(
                AXES, key=lambda axis: np.mean([
                    row[f"{axis.lower()}Value"] for row in rows])),
            "systematicallyLowestOrientation": min(
                AXES, key=lambda axis: np.mean([
                    row[f"{axis.lower()}Value"] for row in rows])),
        })
    write_csv(COMBINED / "rotational-summary.csv", summary)
    telemetry = telemetry_summary()
    charts(run_data, geometry, comparison, fixed, telemetry, common)
    validate_charts()
    make_report(run_data, geometry, common, residuals, telemetry)
    worst = max(magnitude, key=lambda row: row["coefficientOfVariation"])
    print("PASS", "commonCycles", common, "worst",
          worst["metric"], worst["canonicalDirectionKey"],
          worst["coefficientOfVariation"])


if __name__ == "__main__":
    main()
