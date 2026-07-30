#!/usr/bin/env python3
"""Analyze Experiment 3D's directly measured exact-radius shell."""

import csv
import itertools
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

ROOT = Path("measurements/experiment-3d-exact-radius-angular-structure")
COMBINED = ROOT / "combined"
CHARTS = ROOT / "charts"
SOURCE = np.array([90., 90., 90.])
AXES = {
    "X": (np.array([1., 0., 0.]), np.array([0., 1., 0.]),
          np.array([0., 0., 1.])),
    "Y": (np.array([0., 1., 0.]), np.array([0., 0., 1.]),
          np.array([1., 0., 0.])),
    "Z": (np.array([0., 0., 1.]), np.array([1., 0., 0.]),
          np.array([0., 1., 0.])),
}
INPUTS = {a: Path("measurements") /
          f"experiment-3d-exact-radius-{a.lower()}" for a in AXES}
ORBITS = (
    ("ORBIT_25_0_0", (25, 0, 0), 6),
    ("ORBIT_24_7_0", (24, 7, 0), 24),
    ("ORBIT_20_15_0", (20, 15, 0), 24),
    ("ORBIT_20_12_9", (20, 12, 9), 48),
    ("ORBIT_16_15_12", (16, 15, 12), 48))
PERIOD, UPDATES = 18, 180
INTERFACES = (("x=30", 0, 30.), ("x=150", 0, 150.),
              ("y=30", 1, 30.), ("y=150", 1, 150.),
              ("z=30", 2, 30.), ("z=150", 2, 150.))
RMS_METRICS = (
    "accelerationMagnitudeRms", "displacementMagnitudeRms",
    "velocityMagnitudeRms", "gradientMagnitudeRms",
    "radialAccelerationRms", "rawRadialGradientRms",
    "sourceLongitudinalAccelerationRms",
    "sourceTransverseAccelerationRms")
SIGNED_METRICS = (
    "radialAccelerationSignedMean", "rawRadialGradientSignedMean")
ALL_METRICS = RMS_METRICS + SIGNED_METRICS
EPS = np.finfo(float).eps
CHART_NAMES = (
    "exact-shell-detector-geometry", "composite-3d-response",
    "response-by-detector", "response-by-cubic-orbit",
    "opposite-pair-parity", "cubic-invariant-response", "angular-map",
    "per-cycle-directional-variation", "per-cycle-orbit-means",
    "source-contributions", "rotational-consistency-residuals",
    "metric-definition-comparison")


def component(axis, value):
    sign = "P" if value > 0 else "N" if value < 0 else "Z"
    return f"{axis}{sign}{abs(value):02d}"


def label(offset):
    return "_".join(component(axis, value)
                    for axis, value in zip("XYZ", offset))


def signed_permutations(primitive):
    values = set()
    for permutation in set(itertools.permutations(primitive)):
        signs = [(-1, 1) if value else (1,) for value in permutation]
        for sign in itertools.product(*signs):
            values.add(tuple(a * b for a, b in zip(permutation, sign)))
    return sorted(values)


def geometry():
    rows, by_offset = [], {}
    for orbit_id, primitive, expected in ORBITS:
        offsets = signed_permutations(primitive)
        if len(offsets) != expected:
            raise ValueError(f"{orbit_id}: {len(offsets)} != {expected}")
        for offset in offsets:
            if offset in by_offset:
                raise ValueError(f"duplicate offset {offset}")
            radius2 = sum(value * value for value in offset)
            if radius2 != 625:
                raise ValueError(f"invalid squared radius {offset}")
            unit = np.array(offset, dtype=float) / 25.
            theta = math.degrees(math.acos(np.clip(unit[2], -1, 1)))
            phi = math.degrees(math.atan2(unit[1], unit[0]))
            row = {
                "detectorLabel": label(offset), "orbitId": orbit_id,
                "primitiveX": primitive[0], "primitiveY": primitive[1],
                "primitiveZ": primitive[2], "offsetX": offset[0],
                "offsetY": offset[1], "offsetZ": offset[2],
                "squaredRadius": radius2, "actualRadius": 25.0,
                "unitX": unit[0], "unitY": unit[1], "unitZ": unit[2],
                "theta": theta, "phi": phi,
                "cubicInvariantI4": float(np.sum(unit ** 4)),
                "oppositeDetectorLabel": label(tuple(-v for v in offset)),
            }
            rows.append(row)
            by_offset[offset] = row
    if len(rows) != 150:
        raise ValueError(f"detector count {len(rows)} != 150")
    for row in rows:
        opposite = (-int(row["offsetX"]), -int(row["offsetY"]),
                    -int(row["offsetZ"]))
        if opposite not in by_offset or \
                by_offset[opposite]["orbitId"] != row["orbitId"]:
            raise ValueError(f"missing orbit-preserving opposite {row}")
    return rows, by_offset


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, rows):
    if not rows:
        raise ValueError(f"empty output {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def vector(row, prefix):
    return np.array([float(row[prefix + axis]) for axis in "XYZ"])


def reflection(coordinate):
    candidates = []
    for boundary, axis, interface in INTERFACES:
        mirror = coordinate.copy()
        mirror[axis] = 2 * interface - coordinate[axis]
        candidates.append((float(np.linalg.norm(mirror - SOURCE)), boundary))
    return min(candidates)


def sample_values(row, geom, axis):
    radial = np.array([float(geom[f"unit{key}"]) for key in "XYZ"])
    displacement = vector(row, "positionDeviation")
    velocity = vector(row, "velocity")
    acceleration = vector(row, "acceleration")
    gradient = vector(row, "rawGradient")
    longitudinal = AXES[axis][0]
    return {
        "accelerationMagnitudeRms": float(np.linalg.norm(acceleration)),
        "displacementMagnitudeRms": float(np.linalg.norm(displacement)),
        "velocityMagnitudeRms": float(np.linalg.norm(velocity)),
        "gradientMagnitudeRms": float(np.linalg.norm(gradient)),
        "radialAccelerationRms": float(np.dot(acceleration, radial)),
        "rawRadialGradientRms": float(np.dot(gradient, radial)),
        "sourceLongitudinalAccelerationRms":
            float(np.dot(acceleration, longitudinal)),
        "sourceTransverseAccelerationRms": math.sqrt(max(
            0., float(np.dot(acceleration, acceleration))
            - float(np.dot(acceleration, longitudinal)) ** 2)),
        "radialAccelerationSignedMean": float(np.dot(acceleration, radial)),
        "rawRadialGradientSignedMean": float(np.dot(gradient, radial)),
    }


def load_runs(geometry_rows):
    geom = {row["detectorLabel"]: row for row in geometry_rows}
    runs, validation, safety = {}, [], []
    for axis in AXES:
        raw = read_csv(INPUTS[axis] / "raw-detectors-run-001.csv")
        if len(raw) != 150 * UPDATES:
            raise ValueError(f"{axis}: raw rows {len(raw)} != 27000")
        grouped, seen = defaultdict(list), set()
        for row in raw:
            key = (row["detectorName"], int(row["update"]))
            if key in seen:
                raise ValueError(f"{axis}: duplicate {key}")
            seen.add(key)
            grouped[row["detectorName"]].append(row)
            for field in ("positionDeviationX", "velocityX",
                          "accelerationX", "rawGradientX"):
                if not math.isfinite(float(row[field])):
                    raise ValueError(f"{axis}: non-finite {field}")
        if set(grouped) != set(geom):
            raise ValueError(f"{axis}: detector labels mismatch")
        arrivals, safe_sets = {}, {}
        for name, rows in grouped.items():
            if [int(row["update"]) for row in rows] != list(
                    range(1, UPDATES + 1)):
                raise ValueError(f"{axis}: incomplete updates {name}")
            g = geom[name]
            expected_node = tuple(90 + int(g[f"offset{k}"]) for k in "XYZ")
            actual_node = tuple(int(rows[0][f"node{k}"]) for k in "XYZ")
            if expected_node != actual_node or \
                    float(rows[0]["actualEuclideanRadius"]) != 25.0:
                raise ValueError(f"{axis}: geometry mismatch {name}")
            completed = [row for row in rows
                         if row["cycleCompleted"] == "true"]
            arrival = next((row for row in completed
                            if np.linalg.norm(vector(
                                row, "cycleDerivedGradient")) > 0), None)
            if arrival is None:
                raise ValueError(f"{axis}: no arrival {name}")
            arrival_cycle = int(arrival["completedCycleCount"])
            arrivals[name] = (int(arrival["update"]), arrival_cycle)
            coordinate = np.array(actual_node, dtype=float)
            estimate, boundary = reflection(coordinate)
            reflection_update = math.floor(estimate)
            safe = {cycle for cycle in range(
                arrival_cycle + 1, UPDATES // PERIOD + 1)
                if cycle * PERIOD < reflection_update}
            safe_sets[name] = safe
            safety.append({
                "sourceAxis": axis, "detectorLabel": name,
                "arrivalUpdate": int(arrival["update"]),
                "arrivalCycle": arrival_cycle,
                "reflectionInterface": boundary,
                "reflectionEstimateUpdate": reflection_update,
                "safeCycles": " ".join(map(str, sorted(safe))),
                "safeCycleCount": len(safe),
            })
        runs[axis] = {"raw": raw, "grouped": grouped,
                      "arrivals": arrivals, "safe": safe_sets}
        validation.extend((
            {"validationItem": "rawRows", "sourceAxis": axis,
             "expected": 27000, "observed": len(raw), "status": "PASS"},
            {"validationItem": "detectors", "sourceAxis": axis,
             "expected": 150, "observed": len(grouped), "status": "PASS"},
            {"validationItem": "actualRadius", "sourceAxis": axis,
             "expected": 25.0, "observed": 25.0, "status": "PASS"}))
    common = sorted(set.intersection(*[
        cycles for run in runs.values() for cycles in run["safe"].values()]))
    if len(common) < 2:
        raise ValueError(f"underpowered: common safe cycles {common}")
    validation.append({
        "validationItem": "allRunCommonSafeCycles", "sourceAxis": "ALL",
        "expected": "at least two", "observed": " ".join(map(str, common)),
        "status": "PASS"})
    return runs, validation, safety, common


def aggregate_cycles(runs, geom, common):
    cycle_rows, run_metrics = [], {}
    for axis, run in runs.items():
        metric_samples = defaultdict(list)
        for name, rows in run["grouped"].items():
            for cycle in common:
                selected = rows[(cycle - 1) * PERIOD:cycle * PERIOD]
                values = defaultdict(list)
                for row in selected:
                    measured = sample_values(row, geom[name], axis)
                    for metric, value in measured.items():
                        values[metric].append(value)
                for metric in ALL_METRICS:
                    samples = np.array(values[metric])
                    value = float(np.sqrt(np.mean(samples ** 2))) \
                        if metric in RMS_METRICS else float(np.mean(samples))
                    cycle_rows.append({
                        "sourceAxis": axis, "detectorLabel": name,
                        "cycleNumber": cycle,
                        "cycleStartUpdate": (cycle - 1) * PERIOD + 1,
                        "cycleEndUpdate": cycle * PERIOD,
                        "metric": metric, "value": value,
                    })
                    metric_samples[(name, metric)].extend(samples)
        run_metrics[axis] = {}
        for key, samples in metric_samples.items():
            data = np.array(samples)
            run_metrics[axis][key] = float(np.sqrt(np.mean(data ** 2))) \
                if key[1] in RMS_METRICS else float(np.mean(data))
        run_dir = ROOT / f"run-{axis.lower()}"
        run_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(INPUTS[axis] / "raw-detectors-run-001.csv",
                     run_dir / "raw-detectors-run-001.csv")
        shutil.copy2(INPUTS[axis] / "runtime-memory-telemetry.csv",
                     run_dir / "runtime-memory-telemetry.csv")
        write_csv(run_dir / "cycle-metrics.csv",
                  [row for row in cycle_rows if row["sourceAxis"] == axis])
    return cycle_rows, run_metrics


def composites(geometry_rows, runs, run_metrics, cycle_rows, common):
    geom = {row["detectorLabel"]: row for row in geometry_rows}
    detector_rows, definition_rows = [], []
    for name in sorted(geom):
        for metric in ALL_METRICS:
            values = np.array([run_metrics[axis][(name, metric)]
                               for axis in AXES])
            mean = float(np.mean(values))
            pooled = float(np.sqrt(np.mean(values ** 2)))
            composite = pooled if metric in RMS_METRICS else mean
            detector_rows.append({
                "detectorLabel": name, "orbitId": geom[name]["orbitId"],
                "offsetX": geom[name]["offsetX"],
                "offsetY": geom[name]["offsetY"],
                "offsetZ": geom[name]["offsetZ"],
                "actualRadius": 25.0,
                "cubicInvariantI4": geom[name]["cubicInvariantI4"],
                "metric": metric,
                "xSourceValue": values[0], "ySourceValue": values[1],
                "zSourceValue": values[2], "meanOfRms": mean
                    if metric in RMS_METRICS else "",
                "pooledRms": pooled,
                "truePooledSampleRms": pooled,
                "signedArithmeticMean": mean
                    if metric in SIGNED_METRICS else "",
                "signedMeanAbsolute": float(np.mean(np.abs(values)))
                    if metric in SIGNED_METRICS else "",
                "signedOrientationRms": pooled
                    if metric in SIGNED_METRICS else "",
                "compositeValue": composite,
                "oppositeDetectorLabel":
                    geom[name]["oppositeDetectorLabel"],
            })
            if metric in RMS_METRICS:
                definition_rows.append({
                    "detectorLabel": name, "metric": metric,
                    "meanOfRms": mean, "pooledRms": pooled,
                    "truePooledSampleRms": pooled,
                    "difference": pooled - mean,
                    "relativeDifference": (pooled - mean)
                        / max(abs(mean), EPS),
                })
    indexed = {(row["sourceAxis"], row["detectorLabel"],
                int(row["cycleNumber"]), row["metric"]): float(row["value"])
               for row in cycle_rows}
    composite_cycles = []
    for name in sorted(geom):
        for cycle in common:
            for metric in ALL_METRICS:
                values = np.array([indexed[(axis, name, cycle, metric)]
                                   for axis in AXES])
                mean, pooled = float(np.mean(values)), float(np.sqrt(
                    np.mean(values ** 2)))
                composite_cycles.append({
                    "detectorLabel": name, "orbitId": geom[name]["orbitId"],
                    "cycleNumber": cycle, "metric": metric,
                    "xSourceValue": values[0], "ySourceValue": values[1],
                    "zSourceValue": values[2], "meanOfRms": mean
                        if metric in RMS_METRICS else "",
                    "pooledRms": pooled, "compositeValue":
                        pooled if metric in RMS_METRICS else mean,
                })
    return detector_rows, composite_cycles, definition_rows


def rotational(geometry_rows, run_metrics):
    geom_by_offset = {
        (int(row["offsetX"]), int(row["offsetY"]), int(row["offsetZ"])): row
        for row in geometry_rows}
    mapping, comparison = [], []
    groups = defaultdict(dict)
    for axis in AXES:
        basis = AXES[axis]
        for offset, geom in geom_by_offset.items():
            canonical = tuple(int(np.dot(offset, vector))
                              for vector in basis)
            if canonical not in geom_by_offset:
                raise ValueError(f"unmatched canonical direction {canonical}")
            key = label(canonical)
            mapping.append({
                "sourceAxis": axis, "detectorLabel": geom["detectorLabel"],
                "canonicalDirection": key,
                "canonicalX": canonical[0], "canonicalY": canonical[1],
                "canonicalZ": canonical[2], "matched": True})
            groups[key][axis] = geom["detectorLabel"]
    for key, matched in sorted(groups.items()):
        if set(matched) != set(AXES):
            raise ValueError(f"incomplete canonical group {key}")
        for metric in RMS_METRICS:
            values = np.array([run_metrics[axis][(matched[axis], metric)]
                               for axis in AXES])
            mean = float(np.mean(values))
            comparison.append({
                "canonicalDirection": key, "metric": metric,
                "xDetector": matched["X"], "yDetector": matched["Y"],
                "zDetector": matched["Z"], "xValue": values[0],
                "yValue": values[1], "zValue": values[2],
                "mean": mean, "standardDeviation": float(np.std(values)),
                "rotationalCV": float(np.std(values))
                    / max(abs(mean), EPS),
                "maximumRelativeSpread":
                    float(np.max(values) - np.min(values))
                    / max(abs(mean), EPS),
            })
    return mapping, comparison


def statistics(detectors, cycles, common):
    isotropy, parity_rows, orbit_rows, pairwise = [], [], [], []
    opposites = {row["detectorLabel"]: row["oppositeDetectorLabel"]
                 for row in detectors}
    for metric in ALL_METRICS:
        rows = [row for row in detectors if row["metric"] == metric]
        values = np.array([float(row["compositeValue"]) for row in rows])
        mean, mean_abs = float(np.mean(values)), float(np.mean(np.abs(values)))
        std = float(np.std(values))
        imin, imax = int(np.argmin(values)), int(np.argmax(values))
        isotropy.append({
            "summaryType": "AGGREGATE", "cycleNumber": "ALL",
            "metric": metric, "directionCount": len(rows),
            "mean": mean, "standardDeviation": std,
            "directionalCV": std / max(abs(mean), EPS)
                if metric in RMS_METRICS else "",
            "normalizedStandardDeviation": std / max(mean_abs, EPS)
                if metric in SIGNED_METRICS else "",
            "minimum": float(np.min(values)),
            "minimumDetector": rows[imin]["detectorLabel"],
            "maximum": float(np.max(values)),
            "maximumDetector": rows[imax]["detectorLabel"],
            "median": float(np.median(values)),
            "medianAbsoluteDeviation": float(np.median(
                np.abs(values - np.median(values)))),
            "directionalSpread": float(np.max(values) - np.min(values))
                / max(abs(mean), EPS) if metric in RMS_METRICS else "",
            "signedNormalizedSpread":
                float(np.max(values) - np.min(values)) / max(mean_abs, EPS)
                if metric in SIGNED_METRICS else "",
            "maximumRelativeDeviation": float(np.max(
                np.abs(values - mean))) / max(
                    abs(mean) if metric in RMS_METRICS else mean_abs, EPS),
        })
        completed = set()
        indexed = {row["detectorLabel"]: row for row in rows}
        for row in rows:
            pair = tuple(sorted((row["detectorLabel"],
                                 row["oppositeDetectorLabel"])))
            if pair in completed:
                continue
            completed.add(pair)
            first, second = float(row["compositeValue"]), float(
                indexed[row["oppositeDetectorLabel"]]["compositeValue"])
            denominator = max((abs(first) + abs(second)) / 2, EPS)
            parity_rows.append({
                "summaryType": "AGGREGATE", "cycleNumber": "ALL",
                "metric": metric, "detector": pair[0],
                "oppositeDetector": pair[1],
                "parityType": "EVEN" if metric in RMS_METRICS else "ODD",
                "value": first, "oppositeValue": second,
                "residual": abs(first - second) / denominator
                    if metric in RMS_METRICS
                    else abs(first + second) / denominator,
            })
        means = {}
        for orbit_id, _, expected in ORBITS:
            members = [row for row in rows if row["orbitId"] == orbit_id]
            orbit_values = np.array([
                float(row["compositeValue"]) for row in members])
            orbit_mean = float(np.mean(orbit_values))
            means[orbit_id] = orbit_mean
            deviations = np.abs(orbit_values - orbit_mean)
            orbit_rows.append({
                "summaryType": "AGGREGATE", "cycleNumber": "ALL",
                "metric": metric, "orbitId": orbit_id,
                "detectorCount": len(members), "expectedCount": expected,
                "mean": orbit_mean,
                "standardDeviation": float(np.std(orbit_values)),
                "coefficientOfVariation": float(np.std(orbit_values))
                    / max(abs(orbit_mean), EPS)
                    if metric in RMS_METRICS else "",
                "minimum": float(np.min(orbit_values)),
                "maximum": float(np.max(orbit_values)),
                "median": float(np.median(orbit_values)),
                "maximumRelativeDeviation": float(np.max(deviations))
                    / max(abs(orbit_mean), EPS),
                "worstDetector":
                    members[int(np.argmax(deviations))]["detectorLabel"],
            })
        for (first, second) in itertools.combinations(
                [orbit[0] for orbit in ORBITS], 2):
            pairwise.append({
                "summaryType": "AGGREGATE", "cycleNumber": "ALL",
                "metric": metric, "firstOrbit": first,
                "secondOrbit": second, "firstMean": means[first],
                "secondMean": means[second],
                "symmetricRelativeDifference":
                    abs(means[first] - means[second])
                    / max((abs(means[first]) + abs(means[second])) / 2, EPS),
            })
    for cycle in common:
        for metric in RMS_METRICS:
            rows = [row for row in cycles if row["metric"] == metric
                    and int(row["cycleNumber"]) == cycle]
            values = np.array([float(row["compositeValue"]) for row in rows])
            mean = float(np.mean(values))
            orbit_means = {}
            for orbit_id, _, _ in ORBITS:
                members = [row for row in rows if row["orbitId"] == orbit_id]
                member_values = np.array([
                    float(row["compositeValue"]) for row in members])
                orbit_mean = float(np.mean(member_values))
                orbit_means[orbit_id] = orbit_mean
                deviations = np.abs(member_values - orbit_mean)
                orbit_rows.append({
                    "summaryType": "PER_CYCLE", "cycleNumber": cycle,
                    "metric": metric, "orbitId": orbit_id,
                    "detectorCount": len(members),
                    "expectedCount": next(orbit[2] for orbit in ORBITS
                                          if orbit[0] == orbit_id),
                    "mean": orbit_mean,
                    "standardDeviation": float(np.std(member_values)),
                    "coefficientOfVariation": float(np.std(member_values))
                        / max(abs(orbit_mean), EPS),
                    "minimum": float(np.min(member_values)),
                    "maximum": float(np.max(member_values)),
                    "median": float(np.median(member_values)),
                    "maximumRelativeDeviation": float(np.max(deviations))
                        / max(abs(orbit_mean), EPS),
                    "worstDetector":
                        members[int(np.argmax(deviations))]["detectorLabel"],
                })
            for first, second in itertools.combinations(
                    [orbit[0] for orbit in ORBITS], 2):
                pairwise.append({
                    "summaryType": "PER_CYCLE", "cycleNumber": cycle,
                    "metric": metric, "firstOrbit": first,
                    "secondOrbit": second,
                    "firstMean": orbit_means[first],
                    "secondMean": orbit_means[second],
                    "symmetricRelativeDifference": relative(
                        orbit_means[first], orbit_means[second]),
                })
            indexed_cycle = {row["detectorLabel"]: row for row in rows}
            completed_pairs = set()
            for row in rows:
                opposite = opposites[row["detectorLabel"]]
                pair = tuple(sorted((row["detectorLabel"], opposite)))
                if pair in completed_pairs:
                    continue
                completed_pairs.add(pair)
                first = float(row["compositeValue"])
                second = float(indexed_cycle[opposite]["compositeValue"])
                parity_rows.append({
                    "summaryType": "PER_CYCLE", "cycleNumber": cycle,
                    "metric": metric, "detector": pair[0],
                    "oppositeDetector": pair[1], "parityType": "EVEN",
                    "value": first, "oppositeValue": second,
                    "residual": abs(first - second) / max(
                        (abs(first) + abs(second)) / 2, EPS),
                })
            largest = max(relative(a, b) for a, b in itertools.combinations(
                orbit_means.values(), 2))
            isotropy.append({
                "summaryType": "PER_CYCLE", "cycleNumber": cycle,
                "metric": metric, "directionCount": len(rows),
                "mean": mean, "standardDeviation": float(np.std(values)),
                "directionalCV": float(np.std(values)) / max(abs(mean), EPS),
                "normalizedStandardDeviation": "",
                "minimum": float(np.min(values)), "minimumDetector": "",
                "maximum": float(np.max(values)), "maximumDetector": "",
                "median": float(np.median(values)),
                "medianAbsoluteDeviation": float(np.median(
                    np.abs(values - np.median(values)))),
                "directionalSpread": float(np.max(values) - np.min(values))
                    / max(abs(mean), EPS),
                "signedNormalizedSpread": "",
                "maximumRelativeDeviation": largest,
            })
    return isotropy, parity_rows, orbit_rows, pairwise


def relative(first, second):
    return abs(first - second) / max((abs(first) + abs(second)) / 2, EPS)


def rank(values):
    order = np.argsort(values)
    result = np.empty(len(values), dtype=float)
    index = 0
    while index < len(values):
        end = index + 1
        while end < len(values) and values[order[end]] == values[order[index]]:
            end += 1
        result[order[index:end]] = (index + end - 1) / 2
        index = end
    return result


def invariant_analysis(detectors):
    output = []
    for metric in RMS_METRICS:
        rows = [row for row in detectors if row["metric"] == metric]
        x = np.array([float(row["cubicInvariantI4"]) for row in rows])
        y = np.array([float(row["compositeValue"]) for row in rows])
        design = np.column_stack((np.ones(len(x)), x))
        coefficients = np.linalg.lstsq(design, y, rcond=None)[0]
        fitted = design @ coefficients
        residual = y - fitted
        constant = np.full(len(y), np.mean(y))
        ss_total = float(np.sum((y - np.mean(y)) ** 2))
        pearson = float(np.corrcoef(x, y)[0, 1])
        spearman = float(np.corrcoef(rank(x), rank(y))[0, 1])
        for row, fit, error in zip(rows, fitted, residual):
            output.append({
                "summaryType": "DETECTOR_RESIDUAL", "metric": metric,
                "detectorLabel": row["detectorLabel"],
                "cubicInvariantI4": row["cubicInvariantI4"],
                "response": row["compositeValue"],
                "constantFit": float(np.mean(y)), "cubicFit": float(fit),
                "residual": float(error), "a0": coefficients[0],
                "a4": coefficients[1], "pearsonCorrelation": pearson,
                "spearmanCorrelation": spearman,
                "constantRmse": float(np.sqrt(np.mean(
                    (y - constant) ** 2))),
                "cubicRmse": float(np.sqrt(np.mean(residual ** 2))),
                "normalizedRmse": float(np.sqrt(np.mean(residual ** 2)))
                    / max(abs(float(np.mean(y))), EPS),
                "rSquared": 1 - float(np.sum(residual ** 2))
                    / max(ss_total, EPS),
                "residualVarianceReduction": 1 - float(np.sum(
                    residual ** 2)) / max(float(np.sum(
                        (y - constant) ** 2)), EPS),
            })
    return output


def style(ax, title, xlabel, ylabel):
    ax.set_title(title)
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, alpha=.35)


def save(fig, name):
    fig.tight_layout()
    fig.savefig(CHARTS / f"{name}.png", format="png", dpi=180,
                bbox_inches="tight", facecolor="white")
    fig.savefig(CHARTS / f"{name}.svg", format="svg", dpi=180,
                bbox_inches="tight", facecolor="white")
    plt.close(fig)


def charts(geometry_rows, detectors, cycles, parity_rows, orbit_rows,
           isotropy, invariant, rotational):
    CHARTS.mkdir(parents=True, exist_ok=True)
    colors = {orbit[0]: index for index, orbit in enumerate(ORBITS)}
    xyz = np.array([[float(row[f"unit{k}"]) for k in "XYZ"]
                    for row in geometry_rows])
    fig = plt.figure(figsize=(16, 10))
    ax = fig.add_subplot(111, projection="3d")
    ax.scatter(xyz[:, 0], xyz[:, 1], xyz[:, 2],
               c=[colors[row["orbitId"]] for row in geometry_rows],
               cmap="tab10", s=35)
    ax.set(xlim=(-1.1, 1.1), ylim=(-1.1, 1.1), zlim=(-1.1, 1.1))
    ax.set_title("Experiment 3D exact radius-25 detector shell")
    ax.set_xlabel("X"); ax.set_ylabel("Y"); ax.set_zlabel("Z")
    save(fig, CHART_NAMES[0])
    primary = [row for row in detectors
               if row["metric"] == "accelerationMagnitudeRms"]
    values = np.array([float(row["compositeValue"]) for row in primary])
    fig = plt.figure(figsize=(16, 10))
    ax = fig.add_subplot(111, projection="3d")
    ax.scatter(xyz[:, 0], xyz[:, 1], xyz[:, 2],
               s=35 + 250 * values / np.max(values), c=values,
               cmap="viridis", edgecolors="black")
    ax.set(xlim=(-1.1, 1.1), ylim=(-1.1, 1.1), zlim=(-1.1, 1.1))
    ax.set_title("Pooled acceleration RMS on the exact shell")
    ax.set_xlabel("X"); ax.set_ylabel("Y"); ax.set_zlabel("Z")
    save(fig, CHART_NAMES[1])
    fig, ax = plt.subplots(figsize=(18, 7))
    x = np.arange(150)
    ax.plot(x, values, label="pooled RMS")
    ax.plot(x, [float(row["meanOfRms"]) for row in primary],
            label="mean of RMS", alpha=.8)
    style(ax, "Exact-shell response by stable detector order",
          "Detector index", "Acceleration magnitude RMS")
    ax.legend()
    save(fig, CHART_NAMES[2])
    fig, ax = plt.subplots(figsize=(12, 7))
    for index, (orbit_id, _, _) in enumerate(ORBITS):
        members = [row for row in primary if row["orbitId"] == orbit_id]
        vals = [float(row["compositeValue"]) for row in members]
        ax.scatter(np.full(len(vals), index), vals, s=25)
        ax.errorbar(index, np.mean(vals), yerr=np.std(vals), fmt="s",
                    color="black")
    ax.set_xticks(range(5))
    ax.set_xticklabels([orbit[0].replace("ORBIT_", "") for orbit in ORBITS],
                       rotation=25)
    style(ax, "Response within and between cubic orbits", "Cubic orbit",
          "Pooled acceleration RMS")
    save(fig, CHART_NAMES[3])
    pairs = [row for row in parity_rows
             if row["metric"] == "accelerationMagnitudeRms"
             and row["summaryType"] == "AGGREGATE"]
    fig, ax = plt.subplots(figsize=(12, 7))
    px = [float(row["value"]) for row in pairs]
    py = [float(row["oppositeValue"]) for row in pairs]
    ax.scatter(px, py, s=25)
    bounds = [min(px + py), max(px + py)]
    ax.plot(bounds, bounds, "--", label="y=x")
    style(ax, "Composite opposite-pair parity", "Q(d)", "Q(-d)")
    ax.legend()
    save(fig, CHART_NAMES[4])
    inv = [row for row in invariant if row["metric"] ==
           "accelerationMagnitudeRms"]
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.scatter([float(row["cubicInvariantI4"]) for row in inv],
               [float(row["response"]) for row in inv], s=25,
               label="detectors")
    ordered = sorted(inv, key=lambda row: float(row["cubicInvariantI4"]))
    ax.plot([float(row["cubicInvariantI4"]) for row in ordered],
            [float(row["cubicFit"]) for row in ordered],
            label="a0 + a4 I4")
    ax.axhline(float(inv[0]["constantFit"]), linestyle="--",
               label="constant")
    style(ax, "Descriptive cubic-invariant response", "I4",
          "Pooled acceleration RMS")
    ax.legend()
    save(fig, CHART_NAMES[5])
    fig, ax = plt.subplots(figsize=(12, 7))
    scatter = ax.scatter(
        [float(row["phi"]) for row in geometry_rows],
        [float(row["theta"]) for row in geometry_rows], c=values,
        s=45, cmap="viridis")
    fig.colorbar(scatter, ax=ax, label="Pooled acceleration RMS")
    style(ax, "Non-interpolated exact-shell angular map",
          "Azimuth phi (degrees)", "Polar angle theta (degrees)")
    save(fig, CHART_NAMES[6])
    cycle_stats = [row for row in isotropy
                   if row["summaryType"] == "PER_CYCLE"
                   and row["metric"] == "accelerationMagnitudeRms"]
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot([int(row["cycleNumber"]) for row in cycle_stats],
            [float(row["directionalCV"]) for row in cycle_stats],
            marker="o", label="CV")
    ax.plot([int(row["cycleNumber"]) for row in cycle_stats],
            [float(row["directionalSpread"]) for row in cycle_stats],
            marker="s", label="spread")
    style(ax, "Per-cycle directional variation", "Cycle", "Variation")
    ax.legend()
    save(fig, CHART_NAMES[7])
    fig, ax = plt.subplots(figsize=(12, 7))
    for orbit_id, _, _ in ORBITS:
        series = []
        for cycle in sorted({int(row["cycleNumber"]) for row in cycles}):
            members = [float(row["compositeValue"]) for row in cycles
                       if int(row["cycleNumber"]) == cycle
                       and row["metric"] == "accelerationMagnitudeRms"
                       and row["orbitId"] == orbit_id]
            series.append(np.mean(members))
        ax.plot(sorted({int(row["cycleNumber"]) for row in cycles}),
                series, marker="o", label=orbit_id.replace("ORBIT_", ""))
    style(ax, "Per-cycle cubic-orbit means", "Cycle",
          "Pooled acceleration RMS")
    ax.legend()
    save(fig, CHART_NAMES[8])
    fig, ax = plt.subplots(figsize=(18, 7))
    for field, label_text in (("xSourceValue", "X"),
                              ("ySourceValue", "Y"),
                              ("zSourceValue", "Z")):
        ax.plot(x, [float(row[field]) for row in primary],
                linewidth=.8, label=label_text)
    ax.plot(x, values, color="black", label="pooled composite")
    style(ax, "Source contributions by detector", "Detector index",
          "Acceleration magnitude RMS")
    ax.legend()
    save(fig, CHART_NAMES[9])
    rot = [row for row in rotational if row["metric"] ==
           "accelerationMagnitudeRms"]
    fig, ax = plt.subplots(figsize=(18, 7))
    ax.plot(range(len(rot)), [float(row["rotationalCV"]) for row in rot])
    style(ax, "Source-relative rotational residuals",
          "Canonical direction index", "Rotational CV")
    save(fig, CHART_NAMES[10])
    fig, ax = plt.subplots(figsize=(12, 7))
    mean_rms = [float(row["meanOfRms"]) for row in primary]
    ax.scatter(mean_rms, values, s=25)
    bounds = [min(mean_rms + list(values)), max(mean_rms + list(values))]
    ax.plot(bounds, bounds, "--", label="equality")
    style(ax, "Metric-definition comparison", "Mean of source RMS",
          "Pooled source RMS")
    ax.legend()
    save(fig, CHART_NAMES[11])


def validate_charts():
    lines = []
    for name in CHART_NAMES:
        png, svg = CHARTS / f"{name}.png", CHARTS / f"{name}.svg"
        with Image.open(png) as image:
            image.verify()
        with Image.open(png) as image:
            image.load()
            width, height, extrema = *image.size, image.getextrema()
            mode, fmt = image.mode, image.format
        copied = Path("/tmp") / f"experiment-3d-{name}.png"
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


def report(common, runs, detectors, isotropy, parity_rows, orbit_rows,
           pairwise, rotational, invariant):
    primary = next(row for row in isotropy if row["summaryType"] ==
                   "AGGREGATE" and row["metric"] ==
                   "accelerationMagnitudeRms")
    mean_values = np.array([float(row["meanOfRms"]) for row in detectors
                            if row["metric"] ==
                            "accelerationMagnitudeRms"])
    mean_cv = float(np.std(mean_values) / np.mean(mean_values))
    primary_orbits = [row for row in orbit_rows if row["metric"] ==
                      "accelerationMagnitudeRms"
                      and row["summaryType"] == "AGGREGATE"]
    largest = max((row for row in pairwise if row["metric"] ==
                   "accelerationMagnitudeRms"
                   and row["summaryType"] == "AGGREGATE"),
                  key=lambda row: float(row["symmetricRelativeDifference"]))
    parity = [float(row["residual"]) for row in parity_rows
              if row["metric"] == "accelerationMagnitudeRms"
              and row["summaryType"] == "AGGREGATE"]
    rot = [float(row["rotationalCV"]) for row in rotational
           if row["metric"] == "accelerationMagnitudeRms"]
    inv = next(row for row in invariant if row["metric"] ==
               "accelerationMagnitudeRms")
    arrival = [value[0] for run in runs.values()
               for value in run["arrivals"].values()]
    orbit_text = "\n".join(
        f"- {row['orbitId']}: mean {float(row['mean']):.9g}, "
        f"within-orbit CV {float(row['coefficientOfVariation']):.6g}."
        for row in primary_orbits)
    text = f"""# Experiment 3D — Exact-Radius Angular Structure

## 1. Experiment title

Experiment 3D measures the three-source statistical composite on an exact
integer radius-25 shell.

## 2. Objective

At fixed actual radius, determine whether composite passive lattice response
depends on direction.

## 3. Relationship to Experiments 3B and 3C

Experiment 3B validated cyclic X/Y/Z source rotations. Experiment 3C found
strong directionality on a radius-mismatched shell. Experiment 3D directly
remeasures all three sources on an exact shell; radius-20 values are not reused.

## 4. Exact-radius shell construction

All signed coordinate permutations of five primitive triples produce 150
unique offsets satisfying `x²+y²+z²=625`, hence actual radius exactly 25.

## 5. Detector-orbit validation

Orbit counts are 6, 24, 24, 48, and 48. Every offset is unique, every opposite
exists in the same orbit, and every orbit is closed under signed permutations.

## 6. Simulation configuration

Three directly measured 181³ runs use source (90,90,90), amplitude magnitude
200, period 18, 20° phase increment, 180 updates, passive detectors, and
unchanged solver, transport, boundary, and absorption behavior.

## 7. Reflection-safe cycle intersection

Meaningful arrival updates span {min(arrival)}–{max(arrival)}. The all-run,
all-detector intersection is cycles {min(common)}–{max(common)}, updates
{(min(common)-1)*18+1}–{max(common)*18}. It contains
{150 * 3 * len(common)} selected detector/source/cycle rows.

## 8. Source-relative rotational consistency

Acceleration-magnitude rotational CV has median {np.median(rot):.6g} and
maximum {max(rot):.6g}; unmatched canonical directions: zero.

## 9. Composite metric definitions

Pooled source RMS `sqrt(mean(RMS_source²))` is primary. Mean-of-source-RMS is
retained; true pooled-sample RMS is equal to the reported pooled RMS because
all sources and selected cycles contribute equal sample counts.

## 10. All-direction angular variation

Pooled acceleration RMS has CV {float(primary['directionalCV']):.6g}, spread
{float(primary['directionalSpread']):.6g}, minimum
`{primary['minimumDetector']}`, and maximum `{primary['maximumDetector']}`.
Mean-of-RMS directional CV is {mean_cv:.6g}.

## 11. Opposite-direction symmetry

There are 75 pairs. Acceleration even residual median is
{np.median(parity):.6g} and maximum {max(parity):.6g}.

## 12. Within-orbit cubic symmetry

{orbit_text}

## 13. Between-orbit variation

The largest acceleration orbit-mean difference is
{float(largest['symmetricRelativeDifference']):.6g} between
`{largest['firstOrbit']}` and `{largest['secondOrbit']}`.

## 14. Cubic-invariant analysis

For acceleration RMS, Pearson correlation with I4 is
{float(inv['pearsonCorrelation']):.6g}, Spearman correlation is
{float(inv['spearmanCorrelation']):.6g}, and the descriptive
`a0+a4*I4` fit has R² {float(inv['rSquared']):.6g}. This is not a field law.

## 15. Per-cycle consistency

Each common cycle has its own directional CV, spread, orbit means, and parity
evidence in the CSV outputs and charts.

## 16. Metric-definition sensitivity

Pooled RMS is never below mean-of-RMS. Their detector-level differences are
reported without conflating pooled samples with arithmetic RMS averaging.

## 17. Limitations

Only five cubic orbits, three discrete source orientations, and
{len(common)} safe cycles are sampled. The composite is statistical rather
than one physical source.

## 18. Conclusions

Exact-radius angular variation remains strongly directional: pooled
acceleration RMS has CV {float(primary['directionalCV']):.6g}, reduced from
Experiment 3C's 0.108352 but still well above the 5% descriptive threshold.
Members within every cubic orbit are numerically equivalent, while
inequivalent orbit means differ by as much as
{float(largest['symmetricRelativeDifference']):.6g}. The cubic invariant I4
describes most of this sampled structure (R² {float(inv['rSquared']):.6g}),
but is only a descriptive angular model. The evidence supports discrete cubic
symmetry with between-orbit angular structure, not spherical isotropy,
continuous rotational invariance, inverse-square behavior, gravitational
equivalence, or a monopole field.

## 19. Recommended next experiment

Repeat selected exact-radius integer shells at multiple radii to test whether
the measured orbit differences persist, without fitting a radial law first.
"""
    (COMBINED / "report.md").write_text(text, encoding="utf-8")


def main():
    COMBINED.mkdir(parents=True, exist_ok=True)
    geometry_rows, _ = geometry()
    runs, validation, safety, common = load_runs(geometry_rows)
    geom = {row["detectorLabel"]: row for row in geometry_rows}
    cycle_rows, run_metrics = aggregate_cycles(runs, geom, common)
    detectors, composite_cycles, definitions = composites(
        geometry_rows, runs, run_metrics, cycle_rows, common)
    mapping, rotational_rows = rotational(geometry_rows, run_metrics)
    isotropy, parity_rows, orbit_rows, pairwise = statistics(
        detectors, composite_cycles, common)
    invariant = invariant_analysis(detectors)
    write_csv(ROOT / "detector-geometry.csv", geometry_rows)
    write_csv(COMBINED / "input-validation.csv", validation)
    write_csv(COMBINED / "safe-cycle-intersection.csv", safety)
    write_csv(COMBINED / "source-relative-rotation-map.csv", mapping)
    write_csv(COMBINED / "source-relative-comparison.csv", rotational_rows)
    write_csv(COMBINED / "rotational-residuals.csv", rotational_rows)
    write_csv(COMBINED / "composite-detector-summary.csv", detectors)
    write_csv(COMBINED / "composite-cycle-summary.csv", composite_cycles)
    write_csv(COMBINED / "composite-opposite-parity.csv", parity_rows)
    write_csv(COMBINED / "cubic-orbit-summary.csv", orbit_rows)
    write_csv(COMBINED / "cubic-orbit-pairwise-comparison.csv", pairwise)
    write_csv(COMBINED / "angular-isotropy-summary.csv", isotropy)
    write_csv(COMBINED / "cubic-invariant-analysis.csv", invariant)
    write_csv(COMBINED / "metric-definition-comparison.csv", definitions)
    charts(geometry_rows, detectors, composite_cycles, parity_rows,
           orbit_rows, isotropy, invariant, rotational_rows)
    validate_charts()
    report(common, runs, detectors, isotropy, parity_rows, orbit_rows,
           pairwise, rotational_rows, invariant)
    primary = next(row for row in isotropy if row["summaryType"] ==
                   "AGGREGATE" and row["metric"] ==
                   "accelerationMagnitudeRms")
    print("PASS", "common", common, "CV", primary["directionalCV"],
          "spread", primary["directionalSpread"])


if __name__ == "__main__":
    main()
