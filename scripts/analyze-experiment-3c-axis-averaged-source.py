#!/usr/bin/env python3
"""Experiment 3C equal X/Y/Z ensemble-average analysis."""

import csv
import configparser
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

INPUT = Path("measurements/experiment-3b-rotational-consistency")
OUT = Path("measurements/experiment-3c-axis-averaged-source")
CHARTS = OUT / "charts"
AXES = ("X", "Y", "Z")
CYCLES = (3, 4)
EPSILON = np.finfo(float).eps
RMS_METRICS = (
    "accelerationMagnitudeRms", "displacementMagnitudeRms",
    "velocityMagnitudeRms", "gradientMagnitudeRms",
    "radialAccelerationRms", "rawRadialGradientRms")
SIGNED_METRICS = (
    "radialAccelerationSignedMean", "rawRadialGradientSignedMean")
ALL_METRICS = RMS_METRICS + SIGNED_METRICS
CHART_NAMES = (
    "composite-3d-directional-response",
    "composite-acceleration-by-direction",
    "composite-radial-acceleration-by-direction",
    "direction-class-comparison", "opposite-direction-parity",
    "radius-normalization-comparison", "raw-versus-normalized",
    "per-cycle-composite-consistency", "source-contribution-by-detector",
    "cubic-symmetry-groups", "metric-definition-comparison")
PROJECTS = {
    axis: Path("samples") / f"experiment-3b-rotation-{axis.lower()}.qfs"
    for axis in AXES}


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, rows):
    if not rows:
        raise ValueError(f"refusing empty output {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def finite_row(row, fields, context):
    for field in fields:
        try:
            value = float(row[field])
        except (KeyError, ValueError) as exception:
            raise ValueError(f"{context}: invalid {field}") from exception
        if not math.isfinite(value):
            raise ValueError(f"{context}: non-finite {field}")


def validate_projects():
    configurations = {}
    validation = []
    expected_amplitudes = {
        "X": (200.0, 0.0, 0.0), "Y": (0.0, 200.0, 0.0),
        "Z": (0.0, 0.0, 200.0)}
    for axis, path in PROJECTS.items():
        parser = configparser.ConfigParser()
        parser.read(path, encoding="utf-8")
        if not parser.sections():
            raise ValueError(f"could not read project configuration {path}")
        configurations[axis] = parser
        dimensions = tuple(parser.getint("QuantumField", f"dimension.{key}")
                           for key in "xyz")
        source = tuple(parser.getint("Oscillator0", f"nodeindex.{key}")
                       for key in "xyz")
        amplitude = tuple(parser.getfloat("Oscillator0", f"amplitude.{key}")
                          for key in "xyz")
        increment = tuple(parser.getfloat(
            "Oscillator0", f"angleincrement.{key}") for key in "xyz")
        active = tuple(parser.getboolean("Oscillator0", f"active.{key}")
                       for key in "xyz")
        expected_active = tuple(index == "XYZ".index(axis)
                                for index in range(3))
        expected_increment = tuple(20.0 if value else 0.0
                                   for value in expected_active)
        if dimensions != (161, 161, 161) or source != (80, 80, 80) \
                or amplitude != expected_amplitudes[axis] \
                or increment != expected_increment \
                or active != expected_active \
                or parser.getint("FeasibilityPilot", "updates") != 180:
            raise ValueError(f"{axis}: effective project configuration differs")
        validation.extend((
            {"validationItem": "projectLatticeDimensions",
             "sourceAxis": axis, "expectedValue": "161,161,161",
             "observedValue": ",".join(map(str, dimensions)),
             "status": "PASS"},
            {"validationItem": "projectSourcePosition", "sourceAxis": axis,
             "expectedValue": "80,80,80",
             "observedValue": ",".join(map(str, source)), "status": "PASS"},
            {"validationItem": "projectAmplitude", "sourceAxis": axis,
             "expectedValue": str(expected_amplitudes[axis]),
             "observedValue": str(amplitude), "status": "PASS"},
            {"validationItem": "projectPhaseIncrement", "sourceAxis": axis,
             "expectedValue": str(expected_increment),
             "observedValue": str(increment), "status": "PASS"}))
    excluded = {
        "active.x", "active.y", "active.z",
        "angleincrement.x", "angleincrement.y", "angleincrement.z",
        "amplitude.x", "amplitude.y", "amplitude.z"}
    reference = configurations["Z"]
    for section in reference.sections():
        for option, value in reference.items(section):
            if section == "Oscillator0" and option in excluded:
                continue
            for axis in ("X", "Y"):
                if configurations[axis].get(section, option) != value:
                    raise ValueError(
                        f"{axis}: non-axis project setting differs: "
                        f"{section}.{option}")
    validation.append({
        "validationItem": "nonAxisProjectConfiguration",
        "sourceAxis": "ALL", "expectedValue": "identical",
        "observedValue": "identical", "status": "PASS"})
    return validation


def load_and_validate():
    required_configuration = {
        "lattice": "161x161x161", "source": "80,80,80",
        "amplitude": "200", "period": "18", "updates": "180",
        "boundaries": "interfaces 30/130", "safeCycles": "3-4"}
    validation = validate_projects()
    geometry_rows = read_csv(INPUT / "detector-geometry.csv")
    if len(geometry_rows) != 26:
        raise ValueError("shared geometry must contain exactly 26 detectors")
    geometry = {row["detectorName"]: row for row in geometry_rows}
    if len(geometry) != 26:
        raise ValueError("duplicate geometry detector labels")
    class_counts = defaultdict(int)
    for name, row in geometry.items():
        class_counts[row["directionClass"]] += 1
        finite_row(row, ("actualEuclideanRadius", "actualRadialUnitX",
                         "actualRadialUnitY", "actualRadialUnitZ"),
                   f"geometry {name}")
    if dict(class_counts) != {
            "AXIS": 6, "FACE_DIAGONAL": 12, "BODY_DIAGONAL": 8}:
        raise ValueError(f"invalid direction class counts {dict(class_counts)}")
    runs = {}
    for axis in AXES:
        run = INPUT / f"run-{axis.lower()}"
        cycles = read_csv(run / "detector-cycle-summary.csv")
        timeseries = read_csv(run / "detector-timeseries.csv")
        names = {row["detectorName"] for row in cycles}
        if names != set(geometry):
            raise ValueError(f"{axis}: detector labels differ from geometry")
        selected = [row for row in cycles
                    if int(row["cycleNumber"]) in CYCLES]
        common_cycles = {int(row["cycleNumber"]) for row in cycles
                         if row["commonPrimaryCycle"] == "True"}
        if common_cycles != set(CYCLES):
            raise ValueError(
                f"{axis}: common safe cycles {common_cycles} != {CYCLES}")
        keys = [(row["detectorName"], int(row["cycleNumber"]))
                for row in selected]
        if len(keys) != 52 or len(set(keys)) != 52:
            raise ValueError(f"{axis}: selected cycle keys are incomplete")
        for row in selected:
            if row["commonPrimaryCycle"] != "True" or \
                    row["reflectionSafe"] != "True" or \
                    row["postArrival"] != "True":
                raise ValueError(
                    f"{axis}: unsafe selected cycle "
                    f"{row['detectorName']}/{row['cycleNumber']}")
            finite_row(row, tuple(metric for metric in RMS_METRICS
                                  if metric != "velocityMagnitudeRms")
                       + SIGNED_METRICS,
                       f"{axis} cycle {row['detectorName']}")
        ts_keys = [(row["detectorName"], int(row["update"]))
                   for row in timeseries]
        if len(ts_keys) != 4680 or len(set(ts_keys)) != 4680:
            raise ValueError(f"{axis}: time-series integrity failure")
        phase_by_update = {}
        for row in timeseries:
            update = int(row["update"])
            phase_by_update.setdefault(update, row["sourcePhaseDegrees"])
            if phase_by_update[update] != row["sourcePhaseDegrees"]:
                raise ValueError(f"{axis}: inconsistent source phase")
            finite_row(row, ("velocityX", "velocityY", "velocityZ"),
                       f"{axis} time-series")
        first_by_detector = {}
        for row in timeseries:
            first_by_detector.setdefault(row["detectorName"], row)
        for name, row in first_by_detector.items():
            expected = geometry[name]
            observed_index = tuple(int(row[f"node{key}"]) for key in "XYZ")
            expected_index = tuple(int(expected[f"node{key}"])
                                   for key in "XYZ")
            if observed_index != expected_index or not math.isclose(
                    float(row["actualEuclideanRadius"]),
                    float(expected["actualEuclideanRadius"]),
                    rel_tol=0.0, abs_tol=1e-15):
                raise ValueError(f"{axis}: geometry mismatch for {name}")
        runs[axis] = {"cycles": selected, "timeseries": timeseries,
                      "phase": phase_by_update}
        validation.extend({
            "validationItem": item, "sourceAxis": axis,
            "expectedValue": expected, "observedValue": expected,
            "status": "PASS"}
            for item, expected in required_configuration.items())
        validation.extend((
            {"validationItem": "detectorCount", "sourceAxis": axis,
             "expectedValue": 26, "observedValue": len(names),
             "status": "PASS"},
            {"validationItem": "selectedCompleteCycles",
             "sourceAxis": axis, "expectedValue": "3,4",
             "observedValue": "3,4", "status": "PASS"},
            {"validationItem": "selectedDetectorCycleRows",
             "sourceAxis": axis, "expectedValue": 52,
             "observedValue": len(keys), "status": "PASS"}))
    phases = [runs[axis]["phase"] for axis in AXES]
    if not phases[0] == phases[1] == phases[2]:
        raise ValueError("source phase progression differs between runs")
    rotation_map = read_csv(
        INPUT / "combined/source-relative-rotation-map.csv")
    if len(rotation_map) != 78:
        raise ValueError("rotation map must contain 26 rows per run")
    validation.append({
        "validationItem": "sharedPhaseProgression", "sourceAxis": "ALL",
        "expectedValue": "identical", "observedValue": "identical",
        "status": "PASS"})
    return geometry, runs, validation


def velocity_cycle_rms(timeseries):
    values = defaultdict(list)
    for row in timeseries:
        update = int(row["update"])
        cycle = (update - 1) // 18 + 1
        if cycle not in CYCLES:
            continue
        vector = np.array([float(row[f"velocity{component}"])
                           for component in "XYZ"])
        values[(row["detectorName"], cycle)].append(
            float(np.linalg.norm(vector)))
    return {key: float(np.sqrt(np.mean(np.square(samples))))
            for key, samples in values.items()}


def build_cycle_composites(geometry, runs):
    indexed = {}
    velocity = {}
    for axis in AXES:
        indexed[axis] = {
            (row["detectorName"], int(row["cycleNumber"])): row
            for row in runs[axis]["cycles"]}
        velocity[axis] = velocity_cycle_rms(runs[axis]["timeseries"])
    output = []
    for name in sorted(geometry):
        for cycle in CYCLES:
            for metric in ALL_METRICS:
                values = []
                for axis in AXES:
                    value = velocity[axis][(name, cycle)] \
                        if metric == "velocityMagnitudeRms" else float(
                            indexed[axis][(name, cycle)][metric])
                    values.append(value)
                orientation_mean = float(np.mean(values))
                orientation_rms = float(np.sqrt(np.mean(np.square(values))))
                output.append({
                    "detectorLabel": name, "cycleNumber": cycle,
                    "cycleStartUpdate": (cycle - 1) * 18 + 1,
                    "cycleEndUpdate": cycle * 18,
                    "metric": metric, "metricKind":
                        "RMS" if metric in RMS_METRICS else "SIGNED_MEAN",
                    "xSourceValue": values[0], "ySourceValue": values[1],
                    "zSourceValue": values[2],
                    "orientationArithmeticMean": orientation_mean,
                    "orientationRms": orientation_rms,
                    "orientationMeanAbsolute": float(np.mean(
                        np.abs(values))),
                    "orientationStandardDeviation": float(np.std(values)),
                    "orientationCV": float(np.std(values)) /
                        max(abs(orientation_mean), EPSILON),
                    "pooledRms": orientation_rms,
                    "meanOfRms": orientation_mean
                        if metric in RMS_METRICS else "",
                    "compositeValue": orientation_mean,
                })
    return output


def aggregate_detectors(geometry, cycle_rows):
    grouped = defaultdict(list)
    for row in cycle_rows:
        grouped[(row["detectorLabel"], row["metric"])].append(row)
    output = []
    for (name, metric), rows in sorted(grouped.items()):
        source_values = {
            axis: float(np.mean([float(row[f"{axis.lower()}SourceValue"])
                                 for row in rows]))
            for axis in AXES}
        all_orientation_cycle_values = [
            float(row[f"{axis.lower()}SourceValue"])
            for row in rows for axis in AXES]
        mean_of_rms = float(np.mean(all_orientation_cycle_values)) \
            if metric in RMS_METRICS else ""
        pooled = float(np.sqrt(np.mean(
            np.square(all_orientation_cycle_values))))
        composite = float(np.mean(all_orientation_cycle_values))
        geometry_row = geometry[name]
        output.append({
            "detectorLabel": name,
            "directionClass": geometry_row["directionClass"],
            "offsetX": geometry_row["integerOffsetX"],
            "offsetY": geometry_row["integerOffsetY"],
            "offsetZ": geometry_row["integerOffsetZ"],
            "actualRadius": geometry_row["actualEuclideanRadius"],
            "radialUnitX": geometry_row["actualRadialUnitX"],
            "radialUnitY": geometry_row["actualRadialUnitY"],
            "radialUnitZ": geometry_row["actualRadialUnitZ"],
            "metric": metric,
            "metricKind": "RMS" if metric in RMS_METRICS
                else "SIGNED_MEAN",
            "xSourceValue": source_values["X"],
            "ySourceValue": source_values["Y"],
            "zSourceValue": source_values["Z"],
            "orientationMean": float(np.mean(list(source_values.values()))),
            "orientationStandardDeviation": float(np.std(
                list(source_values.values()))),
            "orientationCV": float(np.std(list(source_values.values()))) /
                max(abs(float(np.mean(list(source_values.values())))),
                    EPSILON),
            "pooledRms": pooled,
            "meanOfRms": mean_of_rms,
            "orientationLevelRms": float(np.sqrt(np.mean(np.square(
                list(source_values.values()))))),
            "orientationMeanAbsolute": float(np.mean(np.abs(
                list(source_values.values())))),
            "compositeValue": composite,
            "oppositeDetectorLabel": geometry_row["oppositeDetector"],
        })
    return output


def directional_statistics(detector_rows):
    output = []
    for metric in ALL_METRICS:
        rows = [row for row in detector_rows if row["metric"] == metric]
        values = np.array([float(row["compositeValue"]) for row in rows])
        mean = float(np.mean(values))
        minimum = int(np.argmin(values))
        maximum = int(np.argmax(values))
        output.append({
            "metric": metric, "detectorCount": len(rows),
            "directionalMean": mean,
            "directionalStandardDeviation": float(np.std(values)),
            "directionalCV": float(np.std(values)) /
                max(abs(mean), EPSILON),
            "minimum": float(values[minimum]),
            "minimumDetector": rows[minimum]["detectorLabel"],
            "maximum": float(values[maximum]),
            "maximumDetector": rows[maximum]["detectorLabel"],
            "maximumRelativeSpread": float(
                np.max(values) - np.min(values)) / max(abs(mean), EPSILON),
            "medianAbsoluteDeviation": float(np.median(
                np.abs(values - np.median(values)))),
            "maximumRelativeDeviationFromMean": float(np.max(
                np.abs(values - mean))) / max(abs(mean), EPSILON),
            "descriptiveCategory": category(
                float(np.std(values)) / max(abs(mean), EPSILON)),
        })
    return output


def category(value):
    if value < 1e-10:
        return "numerical equality"
    if value < .001:
        return "strongly uniform"
    if value < .01:
        return "approximately uniform"
    if value < .05:
        return "measurably directional"
    return "strongly directional"


def parity(detector_rows):
    indexed = {(row["detectorLabel"], row["metric"]): row
               for row in detector_rows}
    output, completed = [], set()
    for row in detector_rows:
        name, metric = row["detectorLabel"], row["metric"]
        opposite = row["oppositeDetectorLabel"]
        pair = tuple(sorted((name, opposite)))
        if (pair, metric) in completed:
            continue
        completed.add((pair, metric))
        first = float(row["compositeValue"])
        second = float(indexed[(opposite, metric)]["compositeValue"])
        denominator = max((abs(first) + abs(second)) / 2, EPSILON)
        output.append({
            "detector": name, "oppositeDetector": opposite,
            "metric": metric,
            "appropriateParity": "EVEN" if metric in RMS_METRICS else "ODD",
            "value": first, "oppositeValue": second,
            "evenDifference": abs(first - second) / denominator,
            "oddResidual": abs(first + second) / denominator,
        })
    return output


def class_and_cubic(detector_rows):
    class_rows, cubic_rows = [], []
    for metric in ALL_METRICS:
        metric_rows = [row for row in detector_rows
                       if row["metric"] == metric]
        means = {}
        for kind in ("AXIS", "FACE_DIAGONAL", "BODY_DIAGONAL"):
            rows = [row for row in metric_rows
                    if row["directionClass"] == kind]
            values = np.array([float(row["compositeValue"]) for row in rows])
            radii = np.array([float(row["actualRadius"]) for row in rows])
            mean = float(np.mean(values))
            means[kind] = mean
            class_rows.append({
                "summaryType": "DIRECTION_CLASS", "metric": metric,
                "directionClass": kind, "comparisonClass": "",
                "count": len(rows), "mean": mean,
                "standardDeviation": float(np.std(values)),
                "coefficientOfVariation": float(np.std(values)) /
                    max(abs(mean), EPSILON),
                "minimum": float(np.min(values)),
                "maximum": float(np.max(values)),
                "actualRadiusMean": float(np.mean(radii)),
                "actualRadiusMinimum": float(np.min(radii)),
                "actualRadiusMaximum": float(np.max(radii)),
                "symmetricRelativeDifference": "",
                "radiusMatched": False,
            })
            deviations = np.abs(values - mean)
            cubic_rows.append({
                "metric": metric, "cubicGroup": kind,
                "count": len(rows), "mean": mean,
                "coefficientOfVariation": float(np.std(values)) /
                    max(abs(mean), EPSILON),
                "maximumRelativeDeviation": float(np.max(deviations)) /
                    max(abs(mean), EPSILON),
                "worstDetector": rows[int(np.argmax(deviations))][
                    "detectorLabel"],
            })
        for first, second in (
                ("AXIS", "FACE_DIAGONAL"),
                ("AXIS", "BODY_DIAGONAL"),
                ("FACE_DIAGONAL", "BODY_DIAGONAL")):
            denominator = max(
                (abs(means[first]) + abs(means[second])) / 2, EPSILON)
            class_rows.append({
                "summaryType": "CLASS_COMPARISON", "metric": metric,
                "directionClass": first, "comparisonClass": second,
                "count": "", "mean": "", "standardDeviation": "",
                "coefficientOfVariation": "", "minimum": "", "maximum": "",
                "actualRadiusMean": "", "actualRadiusMinimum": "",
                "actualRadiusMaximum": "",
                "symmetricRelativeDifference":
                    abs(means[first] - means[second]) / denominator,
                "radiusMatched": False,
            })
        radius_groups = defaultdict(list)
        for row in metric_rows:
            radius_groups[round(float(row["actualRadius"]), 9)].append(row)
        for radius, rows in radius_groups.items():
            classes = sorted({row["directionClass"] for row in rows})
            class_rows.append({
                "summaryType": "RADIUS_MATCHED_SUBSET", "metric": metric,
                "directionClass": " ".join(classes),
                "comparisonClass": "none" if len(classes) == 1 else "matched",
                "count": len(rows),
                "mean": float(np.mean([
                    float(row["compositeValue"]) for row in rows])),
                "standardDeviation": float(np.std([
                    float(row["compositeValue"]) for row in rows])),
                "coefficientOfVariation": "", "minimum": "", "maximum": "",
                "actualRadiusMean": radius,
                "actualRadiusMinimum": radius,
                "actualRadiusMaximum": radius,
                "symmetricRelativeDifference": "",
                "radiusMatched": len(classes) > 1,
            })
    return class_rows, cubic_rows


def radius_normalization(detector_rows):
    output = []
    for metric in RMS_METRICS:
        rows = [row for row in detector_rows if row["metric"] == metric]
        for exponent in (0, 1, 2):
            values = np.array([
                float(row["compositeValue"])
                * float(row["actualRadius"]) ** exponent for row in rows])
            mean = float(np.mean(values))
            class_means = {}
            for kind in ("AXIS", "FACE_DIAGONAL", "BODY_DIAGONAL"):
                class_means[kind] = float(np.mean([
                    value for value, row in zip(values, rows)
                    if row["directionClass"] == kind]))
            output.append({
                "metric": metric, "normalizationExponent": exponent,
                "normalizationDefinition": f"Q * actualRadius^{exponent}",
                "directionalCV": float(np.std(values)) /
                    max(abs(mean), EPSILON),
                "directionalSpread": float(np.max(values) - np.min(values))
                    / max(abs(mean), EPSILON),
                "axisMean": class_means["AXIS"],
                "faceDiagonalMean": class_means["FACE_DIAGONAL"],
                "bodyDiagonalMean": class_means["BODY_DIAGONAL"],
                "axisVsFace": relative(
                    class_means["AXIS"], class_means["FACE_DIAGONAL"]),
                "axisVsBody": relative(
                    class_means["AXIS"], class_means["BODY_DIAGONAL"]),
                "faceVsBody": relative(
                    class_means["FACE_DIAGONAL"],
                    class_means["BODY_DIAGONAL"]),
            })
    return output


def relative(first, second):
    return abs(first - second) / max(
        (abs(first) + abs(second)) / 2, EPSILON)


def metric_definitions(detector_rows):
    output = []
    for row in detector_rows:
        if row["metric"] not in RMS_METRICS:
            continue
        mean_rms = float(row["meanOfRms"])
        pooled = float(row["pooledRms"])
        orientation_rms = float(row["orientationLevelRms"])
        output.append({
            "detectorLabel": row["detectorLabel"],
            "metric": row["metric"],
            "meanOfPerRunCycleRms": mean_rms,
            "pooledSampleRms": pooled,
            "rmsAcrossOrientationLevelValues": orientation_rms,
            "pooledMinusMean": pooled - mean_rms,
            "orientationRmsMinusMean": orientation_rms - mean_rms,
            "definitions": "mean(RMS axis/cycle); "
                "sqrt(mean(RMS axis/cycle^2)); "
                "sqrt(mean(meanCycleRMS_axis^2))",
        })
    return output


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


def charts(geometry, detectors, cycles, parity_rows, class_rows,
           normalization, definitions):
    CHARTS.mkdir(parents=True, exist_ok=True)
    primary = [row for row in detectors
               if row["metric"] == "accelerationMagnitudeRms"]
    xyz = np.array([[float(row[f"radialUnit{axis}"]) for axis in "XYZ"]
                    for row in primary])
    values = np.array([float(row["compositeValue"]) for row in primary])
    fig = plt.figure(figsize=(16, 10))
    ax = fig.add_subplot(111, projection="3d")
    sizes = 60 + 300 * values / max(float(np.max(values)), EPSILON)
    ax.scatter(xyz[:, 0], xyz[:, 1], xyz[:, 2], s=sizes, c=values,
               cmap="viridis", edgecolors="black")
    ax.set(xlim=(-1.1, 1.1), ylim=(-1.1, 1.1), zlim=(-1.1, 1.1))
    ax.set_title("Experiment 3C composite acceleration-magnitude response")
    ax.set_xlabel("Radial unit X")
    ax.set_ylabel("Radial unit Y")
    ax.set_zlabel("Radial unit Z")
    save(fig, CHART_NAMES[0])
    labels = [row["detectorLabel"] for row in primary]
    for metric, name, ylabel in (
            ("accelerationMagnitudeRms", CHART_NAMES[1],
             "Composite acceleration magnitude RMS"),
            ("radialAccelerationRms", CHART_NAMES[2],
             "Composite radial acceleration RMS")):
        rows = [row for row in detectors if row["metric"] == metric]
        fig, ax = plt.subplots(figsize=(16, 7))
        ax.bar(range(26), [float(row["compositeValue"]) for row in rows])
        ax.set_xticks(range(26))
        ax.set_xticklabels([row["detectorLabel"] for row in rows],
                           rotation=65, ha="right", fontsize=8)
        style(ax, f"Experiment 3C {ylabel.lower()}", "Detector", ylabel)
        ax.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
        save(fig, name)
    fig, ax = plt.subplots(figsize=(12, 7))
    for index, kind in enumerate(
            ("AXIS", "FACE_DIAGONAL", "BODY_DIAGONAL")):
        rows = [row for row in primary if row["directionClass"] == kind]
        ax.scatter(np.full(len(rows), index),
                   [float(row["compositeValue"]) for row in rows],
                   s=65, label=kind.replace("_", " "))
        ax.hlines(np.mean([float(row["compositeValue"]) for row in rows]),
                  index - .25, index + .25, linewidth=3)
    ax.set_xticks(range(3))
    ax.set_xticklabels(["AXIS", "FACE DIAGONAL", "BODY DIAGONAL"])
    style(ax, "Composite direction-class response", "Class",
          "Acceleration magnitude RMS")
    save(fig, CHART_NAMES[3])
    rows = [row for row in parity_rows
            if row["metric"] == "accelerationMagnitudeRms"]
    fig, ax = plt.subplots(figsize=(12, 7))
    x = np.array([float(row["value"]) for row in rows])
    y = np.array([float(row["oppositeValue"]) for row in rows])
    ax.scatter(x, y, s=65)
    bounds = [min(np.min(x), np.min(y)), max(np.max(x), np.max(y))]
    ax.plot(bounds, bounds, "--", label="y=x even-parity reference")
    style(ax, "Composite opposite-direction parity", "Detector value",
          "Opposite detector value")
    ax.legend()
    save(fig, CHART_NAMES[4])
    fig, ax = plt.subplots(figsize=(12, 7))
    for metric in ("accelerationMagnitudeRms", "radialAccelerationRms",
                   "rawRadialGradientRms"):
        rows = [row for row in normalization if row["metric"] == metric]
        ax.plot([int(row["normalizationExponent"]) for row in rows],
                [float(row["directionalCV"]) for row in rows],
                marker="o", label=f"{metric} CV")
        ax.plot([int(row["normalizationExponent"]) for row in rows],
                [float(row["directionalSpread"]) for row in rows],
                marker="x", linestyle="--", label=f"{metric} spread")
    style(ax, "Exploratory radius-normalization sensitivity",
          "Normalization exponent n", "Directional variation")
    ax.set_xticks([0, 1, 2])
    ax.legend(fontsize=8)
    save(fig, CHART_NAMES[5])
    fig, ax = plt.subplots(figsize=(16, 7))
    for exponent, marker in ((0, "o"), (1, "s"), (2, "^")):
        normalized = [float(row["compositeValue"])
                      * float(row["actualRadius"]) ** exponent
                      for row in primary]
        normalized = np.array(normalized) / np.mean(normalized)
        ax.scatter(range(26), normalized, marker=marker,
                   label=f"n={exponent}")
    ax.set_xticks(range(26))
    ax.set_xticklabels(labels, rotation=65, ha="right", fontsize=8)
    style(ax, "Raw versus radius-normalized angular structure",
          "Detector", "Response / directional mean")
    ax.legend()
    save(fig, CHART_NAMES[6])
    fig, ax = plt.subplots(figsize=(16, 7))
    for cycle in CYCLES:
        rows = [row for row in cycles if row["metric"] ==
                "accelerationMagnitudeRms"
                and int(row["cycleNumber"]) == cycle]
        ax.plot(range(26), [float(row["compositeValue"]) for row in rows],
                marker="o", label=f"cycle {cycle}")
    ax.set_xticks(range(26))
    ax.set_xticklabels(labels, rotation=65, ha="right", fontsize=8)
    style(ax, "Per-cycle composite consistency", "Detector",
          "Acceleration magnitude RMS")
    ax.legend()
    save(fig, CHART_NAMES[7])
    fig, ax = plt.subplots(figsize=(16, 7))
    xloc = np.arange(26)
    for offset, (axis, field) in enumerate(
            (("X", "xSourceValue"), ("Y", "ySourceValue"),
             ("Z", "zSourceValue"))):
        ax.bar(xloc + (offset - 1.5) * .2,
               [float(row[field]) for row in primary], .2, label=axis)
    ax.plot(xloc, values, color="black", marker=".", label="composite mean")
    ax.set_xticks(xloc)
    ax.set_xticklabels(labels, rotation=65, ha="right", fontsize=8)
    style(ax, "Source contributions to the composite", "Detector",
          "Acceleration magnitude RMS")
    ax.legend()
    save(fig, CHART_NAMES[8])
    fig, ax = plt.subplots(figsize=(12, 7))
    for kind, marker in (("AXIS", "o"), ("FACE_DIAGONAL", "s"),
                         ("BODY_DIAGONAL", "^")):
        rows = [row for row in primary if row["directionClass"] == kind]
        ax.scatter(range(len(rows)),
                   [float(row["compositeValue"]) for row in rows],
                   marker=marker, s=70, label=kind.replace("_", " "))
    style(ax, "Within-group cubic symmetry", "Index within cubic group",
          "Acceleration magnitude RMS")
    ax.legend()
    save(fig, CHART_NAMES[9])
    rows = [row for row in definitions
            if row["metric"] == "accelerationMagnitudeRms"]
    fig, ax = plt.subplots(figsize=(16, 7))
    for field, label, marker in (
            ("meanOfPerRunCycleRms", "mean of RMS", "o"),
            ("pooledSampleRms", "pooled RMS", "s"),
            ("rmsAcrossOrientationLevelValues",
             "orientation-level RMS", "^")):
        ax.scatter(range(26), [float(row[field]) for row in rows],
                   marker=marker, label=label)
    ax.set_xticks(range(26))
    ax.set_xticklabels([row["detectorLabel"] for row in rows],
                       rotation=65, ha="right", fontsize=8)
    style(ax, "Composite metric-definition comparison", "Detector",
          "Acceleration magnitude response")
    ax.legend()
    save(fig, CHART_NAMES[10])


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
        copied = Path("/tmp") / f"experiment-3c-{name}.png"
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


def report(detectors, isotropy, parity_rows, class_rows, normalization,
           definitions):
    iso = {row["metric"]: row for row in isotropy}
    primary = iso["accelerationMagnitudeRms"]
    primary_classes = {
        row["directionClass"]: row for row in class_rows
        if row["summaryType"] == "DIRECTION_CLASS"
        and row["metric"] == "accelerationMagnitudeRms"}
    norms = {(row["metric"], int(row["normalizationExponent"])): row
             for row in normalization}
    parity_primary = [row for row in parity_rows
                      if row["metric"] == "accelerationMagnitudeRms"]
    definition_differences = [abs(float(row["pooledMinusMean"]))
                              for row in definitions
                              if row["metric"] ==
                              "accelerationMagnitudeRms"]
    text = f"""# Experiment 3C — Composite Axis-Averaged Source Symmetry

## 1. Experiment title

Experiment 3C analyzes an equal statistical combination of the existing
X-, Y-, and Z-polarized source responses.

## 2. Objective

The objective is to determine whether equal orientation averaging reduces
source-polarization dependence across the 26 sampled directions.

## 3. Composite versus physical source

This is an analysis-layer ensemble average of three independent simulations.
It is not an in-phase XYZ oscillator, breathing source, or newly implemented
physical source mechanism.

## 4. Input Experiment 3B validation

All three 161³ datasets passed configuration, detector geometry, source phase,
row uniqueness, finite-value, and cycle-safety validation. Each contributes
the same 26 detector labels and cycles 3–4.

## 5. Composite metric definitions

The primary non-negative definition is the arithmetic mean of per-run,
per-detector cycle RMS values. Pooled RMS is
`sqrt(mean(RMS_axis,cycle²))`; orientation-level RMS is calculated from the
three per-axis aggregated values. Signed means remain signed and also receive
separate RMS-across-orientations and mean-absolute summaries. The largest
pooled-minus-mean difference for acceleration magnitude is
{max(definition_differences):.6g}.

## 6. Common safe-cycle range

Only complete reflection-safe cycles 3–4, updates 37–72, are used. Results are
reported by cycle and aggregated across both cycles.

## 7. Composite directional response

Raw composite acceleration-magnitude RMS has directional CV
{float(primary['directionalCV']):.6g} and spread
{float(primary['maximumRelativeSpread']):.6g}, classified as
**{primary['descriptiveCategory']}**. The minimum is
`{primary['minimumDetector']}` and maximum is
`{primary['maximumDetector']}`.

## 8. Opposite-direction symmetry

Acceleration-magnitude even-parity residuals have median
{np.median([float(row['evenDifference']) for row in parity_primary]):.6g} and
maximum {max(float(row['evenDifference']) for row in parity_primary):.6g}.
Signed metrics use odd residuals rather than automatic absolute values.

## 9. Cubic symmetry

Within-class cubic groups and their worst detectors are reported in
`composite-cubic-groups.csv`. Opposite symmetry, cyclic rotation consistency,
cubic class symmetry, and sampled-direction spherical isotropy remain
distinct claims.

## 10. Direction-class comparison

- AXIS mean: {float(primary_classes['AXIS']['mean']):.9g}
- FACE_DIAGONAL mean:
  {float(primary_classes['FACE_DIAGONAL']['mean']):.9g}
- BODY_DIAGONAL mean:
  {float(primary_classes['BODY_DIAGONAL']['mean']):.9g}

These raw classes occur at different actual radii and cannot alone establish
cubic anisotropy.

## 11. Actual-radius confounding

Strict `1e-9` radius grouping yields three separate radius groups: axis,
face-diagonal, and body-diagonal. No cross-class radius-matched subset exists,
so a direct radius-controlled class comparison is unavailable on this shell.

## 12. Radius-normalization sensitivity

For acceleration-magnitude RMS:

- n=1: CV {float(norms[('accelerationMagnitudeRms', 1)]['directionalCV']):.6g},
  spread {float(norms[('accelerationMagnitudeRms', 1)]['directionalSpread']):.6g}
- n=2: CV {float(norms[('accelerationMagnitudeRms', 2)]['directionalCV']):.6g},
  spread {float(norms[('accelerationMagnitudeRms', 2)]['directionalSpread']):.6g}

These are exploratory shell equalizations, not radial-law fits. No exponent is
treated as physically preferred.

## 13. Per-cycle consistency

Cycle 3 and cycle 4 composites are retained separately in
`composite-cycle-summary.csv` and plotted by detector.

## 14. Limitations

Only 26 high-symmetry directions, one requested shell, three discrete source
orientations, and two safe cycles are available. Actual radii differ by class,
and polarization averaging is statistical rather than physical.

## 15. Conclusions

The equal X/Y/Z ensemble average remains **{primary['descriptiveCategory']}**
across the sampled directions using raw acceleration-magnitude RMS. Its
direction-class structure must be interpreted together with the radius
normalization sensitivity and the lack of cross-class radius-matched samples.
This result does not show that one Identity emits isotropically and does not
establish continuous rotational invariance, inverse-square behavior,
gravitational equivalence, or a monopole field.

## 16. Recommended next experiment

Use detector coordinates designed to match actual radii across direction
classes, or repeat multiple nearby shells so angular structure and radial
attenuation can be separated without assuming a decay exponent.
"""
    (OUT / "report.md").write_text(text, encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    geometry, runs, validation = load_and_validate()
    cycles = build_cycle_composites(geometry, runs)
    detectors = aggregate_detectors(geometry, cycles)
    isotropy = directional_statistics(detectors)
    parity_rows = parity(detectors)
    class_rows, cubic_rows = class_and_cubic(detectors)
    normalization = radius_normalization(detectors)
    definitions = metric_definitions(detectors)
    write_csv(OUT / "input-validation.csv", validation)
    write_csv(OUT / "composite-detector-summary.csv", detectors)
    write_csv(OUT / "composite-cycle-summary.csv", cycles)
    write_csv(OUT / "composite-opposite-parity.csv", parity_rows)
    write_csv(OUT / "composite-direction-class-summary.csv", class_rows)
    write_csv(OUT / "composite-cubic-groups.csv", cubic_rows)
    write_csv(OUT / "radius-normalization-summary.csv", normalization)
    write_csv(OUT / "directional-isotropy-summary.csv", isotropy)
    write_csv(OUT / "metric-definition-comparison.csv", definitions)
    charts(geometry, detectors, cycles, parity_rows, class_rows,
           normalization, definitions)
    validate_charts()
    report(detectors, isotropy, parity_rows, class_rows, normalization,
           definitions)
    primary = next(row for row in isotropy
                   if row["metric"] == "accelerationMagnitudeRms")
    print("PASS", "detectors=26", "cycles=3,4",
          "accelerationCV=" + str(primary["directionalCV"]),
          "spread=" + str(primary["maximumRelativeSpread"]))


if __name__ == "__main__":
    main()
