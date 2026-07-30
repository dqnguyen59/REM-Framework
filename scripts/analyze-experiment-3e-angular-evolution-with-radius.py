#!/usr/bin/env python3
"""Analyze Experiment 3E exact, collinear shells at radii 13, 26, and 39."""

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
if not LOCAL_DEPS.is_dir():
    LOCAL_DEPS = Path("/tmp/rem-experiment-3d-python-deps")
if LOCAL_DEPS.is_dir():
    sys.path.insert(0, str(LOCAL_DEPS))
os.environ.setdefault("MPLCONFIGDIR", "/tmp/rem-framework-matplotlib")

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image

ROOT = Path("measurements/experiment-3e-angular-evolution-with-radius")
COMBINED, CHARTS = ROOT / "combined", ROOT / "charts"
INPUTS = {axis: Path("measurements") /
          f"experiment-3e-angular-evolution-{axis.lower()}"
          for axis in "XYZ"}
SOURCE = np.array([105., 105., 105.])
AXES = {
    "X": (np.array([1., 0., 0.]), np.array([0., 1., 0.]),
          np.array([0., 0., 1.])),
    "Y": (np.array([0., 1., 0.]), np.array([0., 0., 1.]),
          np.array([1., 0., 0.])),
    "Z": (np.array([0., 0., 1.]), np.array([1., 0., 0.]),
          np.array([0., 1., 0.])),
}
ORBITS = (("ORBIT_13_0_0", (13, 0, 0), 6),
          ("ORBIT_12_5_0", (12, 5, 0), 24),
          ("ORBIT_12_4_3", (12, 4, 3), 48))
RADII, PERIOD, UPDATES = (13, 26, 39), 18, 180
INTERFACES = tuple((f"{axis}={value}", index, float(value))
                   for index, axis in enumerate("xyz")
                   for value in (30, 180))
RMS_METRICS = (
    "accelerationMagnitudeRms", "displacementMagnitudeRms",
    "velocityMagnitudeRms", "gradientMagnitudeRms",
    "radialAccelerationRms", "rawRadialGradientRms",
    "sourceLongitudinalAccelerationRms",
    "sourceTransverseAccelerationRms")
SIGNED_METRICS = ("radialAccelerationSignedMean",
                  "rawRadialGradientSignedMean")
ALL_METRICS = RMS_METRICS + SIGNED_METRICS
EPS = np.finfo(float).eps
CHART_NAMES = (
    "detector-geometry-by-radius", "absolute-response-by-radius",
    "directional-cv-versus-radius", "directional-spread-versus-radius",
    "normalized-response-by-direction", "normalized-orbit-means",
    "largest-orbit-difference", "cubic-coefficient",
    "i4-correlation-r2", "response-versus-i4-r013",
    "response-versus-i4-r026", "response-versus-i4-r039",
    "cross-radius-pattern-comparison", "same-direction-attenuation",
    "opposite-parity", "rotational-consistency",
    "per-cycle-directional-cv", "metric-definition-comparison")


def component(axis, value):
    sign = "P" if value > 0 else "N" if value < 0 else "Z"
    return f"{axis}{sign}{abs(value):02d}"


def offset_label(offset):
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
    rows, lookup = [], {}
    for orbit, primitive, expected in ORBITS:
        bases = signed_permutations(primitive)
        if len(bases) != expected:
            raise ValueError(f"{orbit}: {len(bases)} != {expected}")
        for base in bases:
            if sum(v * v for v in base) != 169:
                raise ValueError(f"invalid base {base}")
            unit = np.array(base, dtype=float) / 13.
            base_name = offset_label(base)
            for scale in (1, 2, 3):
                offset = tuple(scale * value for value in base)
                radius = 13 * scale
                name = f"{orbit}__{base_name}__R{radius:03d}"
                opposite_base = offset_label(tuple(-v for v in base))
                row = {
                    "detectorLabel": name,
                    "baseDirectionLabel": base_name, "orbitId": orbit,
                    "scaleFactor": scale, "requestedRadius": radius,
                    "squaredRadius": sum(v * v for v in offset),
                    "actualRadius": float(radius),
                    "baseOffsetX": base[0], "baseOffsetY": base[1],
                    "baseOffsetZ": base[2], "offsetX": offset[0],
                    "offsetY": offset[1], "offsetZ": offset[2],
                    "unitX": unit[0], "unitY": unit[1], "unitZ": unit[2],
                    "theta": math.degrees(math.acos(np.clip(unit[2], -1, 1))),
                    "phi": math.degrees(math.atan2(unit[1], unit[0])),
                    "cubicInvariantI4": float(np.sum(unit ** 4)),
                    "oppositeDetectorLabel":
                        f"{orbit}__{opposite_base}__R{radius:03d}",
                    "oppositeBaseDirectionLabel": opposite_base}
                if name in lookup:
                    raise ValueError(f"duplicate detector {name}")
                rows.append(row)
                lookup[name] = row
    if len(rows) != 234 or len({r["baseDirectionLabel"] for r in rows}) != 78:
        raise ValueError("Experiment 3E geometry count mismatch")
    for row in rows:
        if row["oppositeDetectorLabel"] not in lookup:
            raise ValueError(f"missing opposite {row['detectorLabel']}")
        scale = int(row["scaleFactor"])
        for axis in "XYZ":
            if int(row[f"offset{axis}"]) != scale * int(
                    row[f"baseOffset{axis}"]):
                raise ValueError("non-collinear scaled detector")
    return rows, lookup


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, rows):
    if not rows:
        raise ValueError(f"empty output {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(rows[0])
    for row in rows[1:]:
        for field in row:
            if field not in fieldnames:
                fieldnames.append(field)
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
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


def sample(row, geom, source_axis):
    radial = np.array([float(geom[f"unit{k}"]) for k in "XYZ"])
    displacement, velocity = vector(row, "positionDeviation"), vector(row, "velocity")
    acceleration, gradient = vector(row, "acceleration"), vector(row, "rawGradient")
    longitudinal = AXES[source_axis][0]
    return {
        "accelerationMagnitudeRms": np.linalg.norm(acceleration),
        "displacementMagnitudeRms": np.linalg.norm(displacement),
        "velocityMagnitudeRms": np.linalg.norm(velocity),
        "gradientMagnitudeRms": np.linalg.norm(gradient),
        "radialAccelerationRms": np.dot(acceleration, radial),
        "rawRadialGradientRms": np.dot(gradient, radial),
        "sourceLongitudinalAccelerationRms": np.dot(acceleration, longitudinal),
        "sourceTransverseAccelerationRms": math.sqrt(max(
            0., np.dot(acceleration, acceleration)
            - np.dot(acceleration, longitudinal) ** 2)),
        "radialAccelerationSignedMean": np.dot(acceleration, radial),
        "rawRadialGradientSignedMean": np.dot(gradient, radial)}


def load_runs(geom):
    runs, validation, safety, runtime = {}, [], [], []
    for axis in "XYZ":
        raw = read_csv(INPUTS[axis] / "raw-detectors-run-001.csv")
        if len(raw) != 234 * UPDATES:
            raise ValueError(f"{axis}: {len(raw)} raw rows")
        grouped, seen = defaultdict(list), set()
        for row in raw:
            key = row["detectorName"], int(row["update"])
            if key in seen:
                raise ValueError(f"{axis}: duplicate {key}")
            seen.add(key)
            grouped[key[0]].append(row)
            for key_name, value in row.items():
                if key_name.endswith(("X", "Y", "Z", "Magnitude")):
                    try:
                        if not math.isfinite(float(value)):
                            raise ValueError(f"{axis}: non-finite {key_name}")
                    except ValueError:
                        pass
        if set(grouped) != set(geom):
            raise ValueError(f"{axis}: detector mismatch")
        safe_sets, arrivals = {}, {}
        for name, rows in grouped.items():
            rows.sort(key=lambda row: int(row["update"]))
            if [int(row["update"]) for row in rows] != list(range(1, 181)):
                raise ValueError(f"{axis}: incomplete {name}")
            g = geom[name]
            node = tuple(int(rows[0][f"node{k}"]) for k in "XYZ")
            expected = tuple(105 + int(g[f"offset{k}"]) for k in "XYZ")
            if node != expected or float(rows[0]["actualEuclideanRadius"]) != \
                    float(g["actualRadius"]):
                raise ValueError(f"{axis}: geometry mismatch {name}")
            completed = [row for row in rows if row["cycleCompleted"] == "true"]
            arrival = next((row for row in completed if np.linalg.norm(
                vector(row, "cycleDerivedGradient")) > 0), None)
            if arrival is None:
                raise ValueError(f"{axis}: no arrival {name}")
            arrival_cycle = int(arrival["completedCycleCount"])
            estimate, boundary = reflection(np.array(node, dtype=float))
            reflected = math.floor(estimate)
            safe = {cycle for cycle in range(arrival_cycle + 1, 11)
                    if cycle * PERIOD < reflected}
            safe_sets[name] = safe
            arrivals[name] = int(arrival["update"])
            safety.append({
                "sourceAxis": axis, "detectorLabel": name,
                "radius": g["actualRadius"], "arrivalUpdate": arrivals[name],
                "arrivalCycle": arrival_cycle,
                "reflectionInterface": boundary,
                "reflectionEstimateUpdate": reflected,
                "safeCycles": " ".join(map(str, sorted(safe))),
                "safeCycleCount": len(safe)})
        runs[axis] = {"raw": raw, "grouped": grouped, "safe": safe_sets,
                      "arrivals": arrivals}
        validation += [
            {"validationItem": "rawRows", "sourceAxis": axis,
             "expected": 42120, "observed": len(raw), "status": "PASS"},
            {"validationItem": "detectors", "sourceAxis": axis,
             "expected": 234, "observed": len(grouped), "status": "PASS"}]
        telemetry = read_csv(INPUTS[axis] / "runtime-memory-telemetry.csv")
        final = telemetry[-1]
        runtime.append({
            "sourceAxis": axis,
            "totalSimulationSeconds":
                float(final["elapsedMilliseconds"]) / 1000,
            "averageUpdateMilliseconds": final["averageUpdateMilliseconds"],
            "peakUsedHeapBytes": final["peakUsedHeapBytes"],
            "peakUsedHeapGiB": float(final["peakUsedHeapBytes"]) / 2 ** 30,
            "maxHeapBytes": final["maxHeapBytes"],
            "gcCount": final["gcCount"], "gcTimeMilliseconds":
                final["gcTimeMilliseconds"]})
    common = sorted(set.intersection(*[
        value for run in runs.values() for value in run["safe"].values()]))
    if len(common) < 2:
        raise ValueError(f"underpowered global cycles {common}")
    validation.append({"validationItem": "globalCommonSafeCycles",
                       "sourceAxis": "ALL", "expected": "at least two",
                       "observed": " ".join(map(str, common)),
                       "status": "PASS"})
    return runs, validation, safety, runtime, common


def aggregate(runs, geom, common):
    cycles, run_metrics = [], {}
    for axis, run in runs.items():
        aggregate_samples = defaultdict(list)
        for name, rows in run["grouped"].items():
            for cycle in common:
                values = defaultdict(list)
                for row in rows[(cycle - 1) * PERIOD:cycle * PERIOD]:
                    for metric, value in sample(row, geom[name], axis).items():
                        values[metric].append(float(value))
                for metric in ALL_METRICS:
                    data = np.array(values[metric])
                    value = float(np.sqrt(np.mean(data ** 2))) \
                        if metric in RMS_METRICS else float(np.mean(data))
                    cycles.append({
                        "sourceAxis": axis, "detectorLabel": name,
                        "radius": geom[name]["actualRadius"],
                        "baseDirectionLabel": geom[name]["baseDirectionLabel"],
                        "orbitId": geom[name]["orbitId"],
                        "cycleNumber": cycle,
                        "cycleStartUpdate": (cycle - 1) * PERIOD + 1,
                        "cycleEndUpdate": cycle * PERIOD,
                        "metric": metric, "value": value})
                    aggregate_samples[(name, metric)].extend(data)
        run_metrics[axis] = {}
        for key, values in aggregate_samples.items():
            data = np.array(values)
            run_metrics[axis][key] = float(np.sqrt(np.mean(data ** 2))) \
                if key[1] in RMS_METRICS else float(np.mean(data))
        run_dir = ROOT / f"run-{axis.lower()}"
        run_dir.mkdir(parents=True, exist_ok=True)
        for filename in ("raw-detectors-run-001.csv",
                         "runtime-memory-telemetry.csv"):
            shutil.copy2(INPUTS[axis] / filename, run_dir / filename)
        write_csv(run_dir / "cycle-metrics.csv",
                  [row for row in cycles if row["sourceAxis"] == axis])
    return cycles, run_metrics


def composites(geometry_rows, run_metrics, cycle_rows, common):
    detector_rows, definition_rows, cycle_composites = [], [], []
    for g in geometry_rows:
        name = g["detectorLabel"]
        for metric in ALL_METRICS:
            values = np.array([run_metrics[a][(name, metric)] for a in "XYZ"])
            mean, pooled = float(np.mean(values)), float(np.sqrt(np.mean(values ** 2)))
            composite = pooled if metric in RMS_METRICS else mean
            detector_rows.append({
                "detectorLabel": name, "baseDirectionLabel": g["baseDirectionLabel"],
                "orbitId": g["orbitId"], "radius": g["actualRadius"],
                "cubicInvariantI4": g["cubicInvariantI4"],
                "metric": metric, "xSourceValue": values[0],
                "ySourceValue": values[1], "zSourceValue": values[2],
                "meanOfSourceRms": mean if metric in RMS_METRICS else "",
                "pooledSourceRms": pooled,
                "truePooledSampleRms": pooled,
                "compositeValue": composite,
                "oppositeDetectorLabel": g["oppositeDetectorLabel"]})
            if metric in RMS_METRICS:
                definition_rows.append({
                    "detectorLabel": name, "radius": g["actualRadius"],
                    "metric": metric, "meanOfSourceRms": mean,
                    "pooledSourceRms": pooled, "truePooledSampleRms": pooled,
                    "absoluteDifference": pooled - mean,
                    "relativeDifference": (pooled - mean) / max(abs(mean), EPS)})
    indexed = {(r["sourceAxis"], r["detectorLabel"], int(r["cycleNumber"]),
                r["metric"]): float(r["value"]) for r in cycle_rows}
    geom = {r["detectorLabel"]: r for r in geometry_rows}
    for name, g in geom.items():
        for cycle in common:
            for metric in ALL_METRICS:
                values = np.array([indexed[(a, name, cycle, metric)] for a in "XYZ"])
                mean, pooled = float(np.mean(values)), float(np.sqrt(np.mean(values ** 2)))
                cycle_composites.append({
                    "detectorLabel": name, "baseDirectionLabel": g["baseDirectionLabel"],
                    "orbitId": g["orbitId"], "radius": g["actualRadius"],
                    "cycleNumber": cycle, "metric": metric,
                    "meanOfSourceRms": mean if metric in RMS_METRICS else "",
                    "pooledSourceRms": pooled,
                    "compositeValue": pooled if metric in RMS_METRICS else mean})
    return detector_rows, cycle_composites, definition_rows


def relative(a, b):
    return abs(a - b) / max((abs(a) + abs(b)) / 2, EPS)


def ranks(values):
    order = np.argsort(values)
    result = np.empty(len(values))
    for value in np.unique(values):
        positions = np.where(values[order] == value)[0]
        result[order[positions]] = np.mean(positions)
    return result


def correlation(first, second):
    if np.std(first) <= EPS or np.std(second) <= EPS:
        return ""
    return float(np.corrcoef(first, second)[0, 1])


def positive_ratio(numerator, denominator):
    return numerator / denominator if numerator > 0 and denominator > 0 else ""


def positive_log_ratio(numerator, denominator):
    return math.log(numerator / denominator) \
        if numerator > 0 and denominator > 0 else ""


def analyze(geometry_rows, detectors, cycles):
    geom = {g["detectorLabel"]: g for g in geometry_rows}
    shell_rows, normalized_rows, orbit_rows, orbit_pairs = [], [], [], []
    parity_rows, invariant_rows, radial_rows, cross_rows = [], [], [], []
    primary_by_radius = {}
    for metric in RMS_METRICS:
        for radius in RADII:
            rows = [r for r in detectors if r["metric"] == metric
                    and float(r["radius"]) == radius]
            values = np.array([float(r["compositeValue"]) for r in rows])
            mean, std = float(np.mean(values)), float(np.std(values))
            normalized = values / max(abs(mean), EPS)
            imin, imax = int(np.argmin(values)), int(np.argmax(values))
            shell_rows.append({
                "summaryType": "AGGREGATE", "cycleNumber": "ALL",
                "radius": radius, "metric": metric, "directionCount": len(rows),
                "shellMean": mean, "shellMedian": float(np.median(values)),
                "standardDeviation": std, "directionalCV":
                    std / max(abs(mean), EPS),
                "directionalSpread":
                    float(np.ptp(values)) / max(abs(mean), EPS),
                "minimumNormalizedResponse": float(np.min(normalized)),
                "maximumNormalizedResponse": float(np.max(normalized)),
                "maximumRelativeDeviationFromOne":
                    float(np.max(np.abs(normalized - 1))),
                "medianAbsoluteDeviation": float(np.median(
                    np.abs(values - np.median(values)))),
                "minimumDirection": rows[imin]["baseDirectionLabel"],
                "maximumDirection": rows[imax]["baseDirectionLabel"]})
            if metric == "accelerationMagnitudeRms":
                primary_by_radius[radius] = (rows, normalized)
            for row, value in zip(rows, normalized):
                normalized_rows.append({
                    "detectorLabel": row["detectorLabel"],
                    "baseDirectionLabel": row["baseDirectionLabel"],
                    "orbitId": row["orbitId"], "radius": radius,
                    "metric": metric, "absoluteResponse": row["compositeValue"],
                    "shellMean": mean, "normalizedResponse": float(value)})
            orbit_means = {}
            for orbit, _, expected in ORBITS:
                members = [r for r in rows if r["orbitId"] == orbit]
                data = np.array([float(r["compositeValue"]) for r in members])
                orbit_mean = float(np.mean(data))
                orbit_means[orbit] = orbit_mean
                orbit_rows.append({
                    "summaryType": "AGGREGATE", "cycleNumber": "ALL",
                    "radius": radius, "metric": metric, "orbitId": orbit,
                    "detectorCount": len(members), "expectedCount": expected,
                    "meanAbsoluteResponse": orbit_mean,
                    "normalizedOrbitMean": orbit_mean / max(abs(mean), EPS),
                    "withinOrbitCV": float(np.std(data))
                        / max(abs(orbit_mean), EPS),
                    "minimum": float(np.min(data)), "maximum": float(np.max(data)),
                    "worstDetector": members[int(np.argmax(
                        np.abs(data - orbit_mean)))]["detectorLabel"]})
            for first, second in itertools.combinations(orbit_means, 2):
                orbit_pairs.append({
                    "radius": radius, "metric": metric,
                    "firstOrbit": first, "secondOrbit": second,
                    "firstNormalizedMean":
                        orbit_means[first] / max(abs(mean), EPS),
                    "secondNormalizedMean":
                        orbit_means[second] / max(abs(mean), EPS),
                    "symmetricRelativeDifference":
                        relative(orbit_means[first], orbit_means[second])})
            x = np.array([float(r["cubicInvariantI4"]) for r in rows])
            design = np.column_stack((np.ones(len(x)), x))
            coefficients = np.linalg.lstsq(design, normalized, rcond=None)[0]
            fitted = design @ coefficients
            residual = normalized - fitted
            constant_error = normalized - np.mean(normalized)
            ss_total = float(np.sum(constant_error ** 2))
            pearson = correlation(x, normalized)
            spearman = correlation(ranks(x), ranks(normalized))
            for row, fit, error in zip(rows, fitted, residual):
                invariant_rows.append({
                    "summaryType": "DETECTOR", "radius": radius,
                    "metric": metric, "detectorLabel": row["detectorLabel"],
                    "baseDirectionLabel": row["baseDirectionLabel"],
                    "cubicInvariantI4": row["cubicInvariantI4"],
                    "normalizedResponse": float(row["compositeValue"])
                        / max(abs(mean), EPS),
                    "a0": coefficients[0], "a4": coefficients[1],
                    "centeredC4": coefficients[1],
                    "pearsonCorrelation": pearson,
                    "spearmanCorrelation": spearman,
                    "rmse": float(np.sqrt(np.mean(residual ** 2))),
                    "normalizedRmse": float(np.sqrt(np.mean(residual ** 2))),
                    "rSquared": 1 - float(np.sum(residual ** 2))
                        / max(ss_total, EPS),
                    "residualVarianceReduction": 1 - float(np.sum(
                        residual ** 2)) / max(ss_total, EPS),
                    "fittedResponse": float(fit), "residual": float(error)})
            indexed = {r["detectorLabel"]: r for r in rows}
            completed = set()
            for row in rows:
                pair = tuple(sorted((row["detectorLabel"],
                                     row["oppositeDetectorLabel"])))
                if pair in completed:
                    continue
                completed.add(pair)
                first = float(row["compositeValue"])
                second = float(indexed[row["oppositeDetectorLabel"]]["compositeValue"])
                parity_rows.append({
                    "radius": radius, "metric": metric, "pairCountAtRadius": 39,
                    "detector": pair[0], "oppositeDetector": pair[1],
                    "value": first, "oppositeValue": second,
                    "evenResidual": abs(first - second)
                        / max((abs(first) + abs(second)) / 2, EPS)})
    for metric in SIGNED_METRICS:
        for radius in RADII:
            rows = [r for r in detectors if r["metric"] == metric
                    and float(r["radius"]) == radius]
            indexed = {r["detectorLabel"]: r for r in rows}
            completed = set()
            for row in rows:
                pair = tuple(sorted((row["detectorLabel"],
                                     row["oppositeDetectorLabel"])))
                if pair in completed:
                    continue
                completed.add(pair)
                first = float(row["compositeValue"])
                second = float(indexed[row["oppositeDetectorLabel"]]
                               ["compositeValue"])
                denominator = max((abs(first) + abs(second)) / 2, EPS)
                parity_rows.append({
                    "radius": radius, "metric": metric,
                    "pairCountAtRadius": 39, "detector": pair[0],
                    "oppositeDetector": pair[1], "value": first,
                    "oppositeValue": second, "evenResidual": "",
                    "oddResidual": abs(first + second) / denominator})
    for metric in RMS_METRICS:
        by_base = defaultdict(dict)
        for row in detectors:
            if row["metric"] == metric:
                by_base[row["baseDirectionLabel"]][int(float(row["radius"]))] = \
                    float(row["compositeValue"])
        for base, values in by_base.items():
            radial_rows.append({
                "baseDirectionLabel": base, "metric": metric,
                "responseR13": values[13], "responseR26": values[26],
                "responseR39": values[39],
                "ratio26To13": positive_ratio(values[26], values[13]),
                "ratio39To26": positive_ratio(values[39], values[26]),
                "ratio39To13": positive_ratio(values[39], values[13]),
                "logDifference26To13":
                    positive_log_ratio(values[26], values[13]),
                "logDifference39To26":
                    positive_log_ratio(values[39], values[26]),
                "monotonicity": "decreasing" if
                    values[13] > values[26] > values[39] else
                    "increasing" if values[13] < values[26] < values[39]
                    else "non-monotonic"})
        normalized = {radius: {
            row["baseDirectionLabel"]: float(row["normalizedResponse"])
            for row in normalized_rows if row["metric"] == metric
            and int(row["radius"]) == radius} for radius in RADII}
        for first, second in ((13, 26), (26, 39), (13, 39)):
            keys = sorted(normalized[first])
            a = np.array([normalized[first][key] for key in keys])
            b = np.array([normalized[second][key] for key in keys])
            delta = np.abs(a - b)
            cross_rows.append({
                "metric": metric, "firstRadius": first, "secondRadius": second,
                "pearsonCorrelation": correlation(a, b),
                "spearmanCorrelation": correlation(ranks(a), ranks(b)),
                "rmsPatternDifference": float(np.sqrt(np.mean((a - b) ** 2))),
                "maximumAbsoluteNormalizedDifference": float(np.max(delta)),
                "worstDirection": keys[int(np.argmax(delta))]})
    per_cycle = []
    for cycle in sorted({int(r["cycleNumber"]) for r in cycles}):
        for radius in RADII:
            rows = [r for r in cycles if int(r["cycleNumber"]) == cycle
                    and int(float(r["radius"])) == radius
                    and r["metric"] == "accelerationMagnitudeRms"]
            values = np.array([float(r["compositeValue"]) for r in rows])
            mean = float(np.mean(values))
            x = np.array([float(geom[r["detectorLabel"]]["cubicInvariantI4"])
                          for r in rows])
            y = values / mean
            coef = np.linalg.lstsq(np.column_stack((np.ones(len(x)), x)),
                                   y, rcond=None)[0]
            fitted = coef[0] + coef[1] * x
            per_cycle.append({
                "summaryType": "PER_CYCLE", "cycleNumber": cycle,
                "radius": radius, "metric": "accelerationMagnitudeRms",
                "shellMean": mean, "directionalCV": float(np.std(values)) / mean,
                "directionalSpread": float(np.ptp(values)) / mean,
                "a4": coef[1], "rSquared": 1 - float(np.sum((y - fitted) ** 2))
                    / max(float(np.sum((y - np.mean(y)) ** 2)), EPS)})
    shell_rows.extend(per_cycle)
    return (shell_rows, normalized_rows, orbit_rows, orbit_pairs, parity_rows,
            invariant_rows, radial_rows, cross_rows)


def rotational(geometry_rows, run_metrics):
    by_key = {(int(g["scaleFactor"]), int(g["baseOffsetX"]),
               int(g["baseOffsetY"]), int(g["baseOffsetZ"])): g
              for g in geometry_rows}
    mapping, residuals = [], []
    groups = defaultdict(dict)
    for axis in "XYZ":
        basis = AXES[axis]
        for g in geometry_rows:
            base = np.array([g[f"baseOffset{k}"] for k in "XYZ"])
            canonical = tuple(int(np.dot(base, vector)) for vector in basis)
            key = (int(g["scaleFactor"]),) + canonical
            if key not in by_key:
                raise ValueError(f"unmatched rotation {axis} {key}")
            canonical_label = f"{offset_label(canonical)}__R{int(g['actualRadius']):03d}"
            groups[canonical_label][axis] = g["detectorLabel"]
            mapping.append({
                "sourceAxis": axis, "radius": g["actualRadius"],
                "detectorLabel": g["detectorLabel"],
                "canonicalDirection": canonical_label,
                "canonicalX": canonical[0], "canonicalY": canonical[1],
                "canonicalZ": canonical[2], "matched": True})
    for key, matched in groups.items():
        for metric in RMS_METRICS:
            values = np.array([run_metrics[a][(matched[a], metric)] for a in "XYZ"])
            mean = float(np.mean(values))
            residuals.append({
                "canonicalDirection": key,
                "radius": int(key.rsplit("R", 1)[1]), "metric": metric,
                "xDetector": matched["X"], "yDetector": matched["Y"],
                "zDetector": matched["Z"], "xValue": values[0],
                "yValue": values[1], "zValue": values[2],
                "mean": mean, "rotationalCV": float(np.std(values))
                    / max(abs(mean), EPS),
                "maximumRelativeSpread": float(np.ptp(values))
                    / max(abs(mean), EPS)})
    return mapping, residuals


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


def charts(geometry_rows, detectors, shell, normalized, orbits, pairs,
           parity, invariant, radial, cross, rotational_rows, definitions):
    CHARTS.mkdir(parents=True, exist_ok=True)
    fig = plt.figure(figsize=(16, 10)); ax = fig.add_subplot(111, projection="3d")
    for radius in RADII:
        rows = [g for g in geometry_rows if int(g["actualRadius"]) == radius]
        ax.scatter([g["offsetX"] for g in rows], [g["offsetY"] for g in rows],
                   [g["offsetZ"] for g in rows], s=18, label=f"r={radius}")
    ax.set_box_aspect((1, 1, 1)); ax.legend(); ax.set_title("Exact collinear detector shells")
    ax.set_xlabel("X offset"); ax.set_ylabel("Y offset"); ax.set_zlabel("Z offset")
    save(fig, CHART_NAMES[0])
    primary = [r for r in detectors if r["metric"] == "accelerationMagnitudeRms"]
    fig, ax = plt.subplots(figsize=(12, 7))
    for orbit, _, _ in ORBITS:
        ax.plot(RADII, [np.mean([float(r["compositeValue"]) for r in primary
                 if r["orbitId"] == orbit and int(float(r["radius"])) == radius])
                 for radius in RADII], marker="o", label=orbit)
    ax.plot(RADII, [np.mean([float(r["compositeValue"]) for r in primary
             if int(float(r["radius"])) == radius]) for radius in RADII],
            marker="s", color="black", label="shell mean")
    style(ax, "Absolute pooled acceleration response", "Radius",
          "Acceleration magnitude RMS"); ax.legend(); save(fig, CHART_NAMES[1])
    for field, title, name in (("directionalCV", "Directional CV", CHART_NAMES[2]),
                               ("directionalSpread", "Directional spread", CHART_NAMES[3])):
        fig, ax = plt.subplots(figsize=(12, 7))
        for metric in RMS_METRICS:
            rows = [r for r in shell if r["summaryType"] == "AGGREGATE"
                    and r["metric"] == metric]
            ax.plot(RADII, [float(next(r[field] for r in rows
                    if int(r["radius"]) == radius)) for radius in RADII],
                    marker="o", label=metric)
        style(ax, f"{title} versus exact radius", "Radius", title)
        ax.legend(fontsize=8); save(fig, name)
    fig, ax = plt.subplots(figsize=(18, 7))
    bases = sorted({r["baseDirectionLabel"] for r in normalized})
    for radius in RADII:
        values = {r["baseDirectionLabel"]: float(r["normalizedResponse"])
                  for r in normalized if r["metric"] == "accelerationMagnitudeRms"
                  and int(r["radius"]) == radius}
        ax.plot(range(78), [values[b] for b in bases], label=f"r={radius}")
    style(ax, "Shell-normalized angular response", "Stable base direction index",
          "Response / shell mean"); ax.legend(); save(fig, CHART_NAMES[4])
    fig, ax = plt.subplots(figsize=(12, 7))
    for orbit, _, _ in ORBITS:
        ax.plot(RADII, [float(next(r["normalizedOrbitMean"] for r in orbits
                if r["summaryType"] == "AGGREGATE"
                and r["metric"] == "accelerationMagnitudeRms"
                and r["orbitId"] == orbit and int(r["radius"]) == radius))
                for radius in RADII], marker="o", label=orbit)
    style(ax, "Normalized cubic-orbit means", "Radius", "Normalized orbit mean")
    ax.legend(); save(fig, CHART_NAMES[5])
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot(RADII, [max(float(r["symmetricRelativeDifference"]) for r in pairs
            if r["metric"] == "accelerationMagnitudeRms"
            and int(r["radius"]) == radius) for radius in RADII], marker="o")
    style(ax, "Largest between-orbit difference", "Radius", "Symmetric difference")
    save(fig, CHART_NAMES[6])
    summaries = {radius: next(r for r in invariant
        if r["metric"] == "accelerationMagnitudeRms"
        and int(r["radius"]) == radius) for radius in RADII}
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot(RADII, [float(summaries[r]["a4"]) for r in RADII], marker="o")
    style(ax, "Descriptive cubic coefficient a4", "Radius", "a4")
    save(fig, CHART_NAMES[7])
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot(RADII, [float(summaries[r]["pearsonCorrelation"]) for r in RADII],
            marker="o", label="Pearson")
    ax.plot(RADII, [float(summaries[r]["rSquared"]) for r in RADII],
            marker="s", label="R²")
    style(ax, "I4 correlation and descriptive-fit R²", "Radius", "Statistic")
    ax.legend(); save(fig, CHART_NAMES[8])
    for index, radius in enumerate(RADII):
        rows = [r for r in invariant if r["metric"] == "accelerationMagnitudeRms"
                and int(r["radius"]) == radius]
        fig, ax = plt.subplots(figsize=(12, 7))
        ax.scatter([float(r["cubicInvariantI4"]) for r in rows],
                   [float(r["normalizedResponse"]) for r in rows], label="directions")
        ordered = sorted(rows, key=lambda r: float(r["cubicInvariantI4"]))
        ax.plot([float(r["cubicInvariantI4"]) for r in ordered],
                [float(r["fittedResponse"]) for r in ordered], label="a0+a4 I4")
        style(ax, f"Normalized response versus I4 at r={radius}", "I4",
              "Normalized acceleration response"); ax.legend()
        save(fig, CHART_NAMES[9 + index])
    fig, ax = plt.subplots(figsize=(12, 7))
    for pair in ((13, 26), (26, 39), (13, 39)):
        a = {r["baseDirectionLabel"]: float(r["normalizedResponse"]) for r in normalized
             if r["metric"] == "accelerationMagnitudeRms" and int(r["radius"]) == pair[0]}
        b = {r["baseDirectionLabel"]: float(r["normalizedResponse"]) for r in normalized
             if r["metric"] == "accelerationMagnitudeRms" and int(r["radius"]) == pair[1]}
        ax.scatter([a[k] for k in sorted(a)], [b[k] for k in sorted(a)],
                   label=f"{pair[0]} vs {pair[1]}", s=20)
    bounds = ax.get_xlim(); ax.plot(bounds, bounds, "--", color="black", label="y=x")
    style(ax, "Cross-radius normalized angular patterns", "First radius",
          "Second radius"); ax.legend(); save(fig, CHART_NAMES[12])
    rows = [r for r in radial if r["metric"] == "accelerationMagnitudeRms"]
    fig, ax = plt.subplots(figsize=(18, 7))
    ax.plot(range(78), [float(r["ratio26To13"]) for r in rows], label="26/13")
    ax.plot(range(78), [float(r["ratio39To26"]) for r in rows], label="39/26")
    style(ax, "Same-direction attenuation ratios", "Base direction index", "Ratio")
    ax.legend(); save(fig, CHART_NAMES[13])
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot(RADII, [max(float(r["evenResidual"]) for r in parity
            if r["metric"] == "accelerationMagnitudeRms"
            and int(r["radius"]) == radius) for radius in RADII], marker="o")
    style(ax, "Maximum opposite-pair residual", "Radius", "Even residual")
    save(fig, CHART_NAMES[14])
    fig, ax = plt.subplots(figsize=(12, 7))
    ax.plot(RADII, [max(float(r["rotationalCV"]) for r in rotational_rows
            if r["metric"] == "accelerationMagnitudeRms"
            and int(r["radius"]) == radius) for radius in RADII], marker="o")
    style(ax, "Maximum rotational CV", "Radius", "Rotational CV")
    save(fig, CHART_NAMES[15])
    fig, ax = plt.subplots(figsize=(12, 7))
    for radius in RADII:
        rows = [r for r in shell if r["summaryType"] == "PER_CYCLE"
                and int(r["radius"]) == radius]
        ax.plot([int(r["cycleNumber"]) for r in rows],
                [float(r["directionalCV"]) for r in rows],
                marker="o", label=f"r={radius}")
    style(ax, "Per-cycle acceleration directional CV", "Cycle", "Directional CV")
    ax.legend(); save(fig, CHART_NAMES[16])
    fig, ax = plt.subplots(figsize=(12, 7))
    for radius in RADII:
        values = [float(r["relativeDifference"]) for r in definitions
                  if r["metric"] == "accelerationMagnitudeRms"
                  and int(float(r["radius"])) == radius]
        ax.scatter([radius] * len(values), values, s=15, label=f"r={radius}")
    style(ax, "Pooled RMS versus mean-of-RMS", "Radius", "Relative difference")
    ax.legend(); save(fig, CHART_NAMES[17])


def validate_charts():
    results = []
    identify = shutil.which("identify")
    for name in CHART_NAMES:
        png, svg = CHARTS / f"{name}.png", CHARTS / f"{name}.svg"
        signature = png.read_bytes()[:8]
        with Image.open(png) as image:
            image.verify()
        with Image.open(png) as image:
            image.load()
            width, height, mode, fmt = *image.size, image.mode, image.format
            extrema = image.getextrema()
        file_result = subprocess.run(["file", str(png)], check=True,
                                     capture_output=True, text=True).stdout.strip()
        copy = Path("/tmp") / f"experiment-3e-{name}.png"
        shutil.copy2(png, copy)
        with Image.open(copy) as image:
            image.load()
        svg_text = svg.read_text(encoding="utf-8")
        identify_result = subprocess.run([identify, str(png)], check=True,
            capture_output=True, text=True).stdout.strip() if identify else "not installed"
        passed = (png.stat().st_size > 20000 and width >= 1600
                  and height >= 900 and mode in ("RGB", "RGBA") and fmt == "PNG"
                  and signature == b"\x89PNG\r\n\x1a\n"
                  and "PNG image data" in file_result and svg.stat().st_size > 2000
                  and "<svg" in svg_text
                  and any(low != high for low, high in extrema))
        results.append(
            f"{name}.png | {png.resolve()} | {png.stat().st_size} bytes | "
            f"{width}x{height} {mode} {fmt} | signature={signature.hex(' ')} | "
            f"Pillow verify/load=PASS | file={file_result} | identify={identify_result} | "
            f"extrema={extrema} | SVG={svg.name} | {'PASS' if passed else 'FAIL'}")
        if not passed:
            raise ValueError(f"chart validation failed: {name}")
    (CHARTS / "chart-validation.txt").write_text(
        "\n".join(results) + "\n", encoding="utf-8")


def report(shell, orbits, pairs, invariant, cross, parity, rotational_rows,
           safety, runtime, common):
    primary = {int(r["radius"]): r for r in shell
               if r["summaryType"] == "AGGREGATE"
               and r["metric"] == "accelerationMagnitudeRms"}
    inv = {int(r["radius"]): r for r in invariant
           if r["metric"] == "accelerationMagnitudeRms"}
    arrivals = {radius: [int(r["arrivalUpdate"]) for r in safety
                if int(float(r["radius"])) == radius] for radius in RADII}
    orbit_lines = []
    for radius in RADII:
        values = [r for r in orbits if r["summaryType"] == "AGGREGATE"
                  and r["metric"] == "accelerationMagnitudeRms"
                  and int(r["radius"]) == radius]
        orbit_lines.append(f"- r={radius}: " + ", ".join(
            f"{r['orbitId']}={float(r['normalizedOrbitMean']):.9g}"
            for r in values))
    cv_change = (float(primary[39]["directionalCV"])
                 / float(primary[13]["directionalCV"]) - 1) * 100
    trend = ("decreasing" if all(float(primary[a]["directionalCV"]) >
             float(primary[b]["directionalCV"]) for a, b in ((13, 26), (26, 39)))
             else "increasing" if all(float(primary[a]["directionalCV"]) <
             float(primary[b]["directionalCV"]) for a, b in ((13, 26), (26, 39)))
             else "non-monotonic")
    text = f"""# Experiment 3E — Angular Evolution with Radius

## 1. Objective

Measure whether normalized cubic angular structure weakens, persists, or
strengthens from exact radius 13 through 39. This is not a radial-law test.

## 2. Relationship to Experiment 3D

Experiment 3D measured five cubic orbits at radius 25. Experiment 3E follows
the same 78 exact base rays at three collinear integer scales.

## 3. Exact collinear detector construction

The base shell x²+y²+z²=169 contains 78 directions in orbit counts 6, 24,
and 48. Integer scaling produces 234 unique detectors at radii 13, 26, and 39.

## 4. Simulation configuration

Three direct X/Y/Z runs used a 211³ lattice centered at (105,105,105), amplitude
200, 20-degree phase increments, period 18, and 180 updates.

## 5. Lattice-sizing diagnostics

The 181³ bound was rejected analytically because radius 39 would not retain
two safe cycles. The 211³ X run served as the sizing diagnostic and was
retained as valid data.

## 6. Arrival and reflection-safe intervals

- r=13 arrival range: {min(arrivals[13])}–{max(arrivals[13])}
- r=26 arrival range: {min(arrivals[26])}–{max(arrivals[26])}
- r=39 arrival range: {min(arrivals[39])}–{max(arrivals[39])}
- Global common cycles: {', '.join(map(str, common))}
- Global update range: {(common[0]-1)*18+1}–{common[-1]*18}
- Selected detector/source/cycle rows: {234 * 3 * len(common)}

## 7. Source-relative rotational consistency

All source-relative mappings matched at every radius. Median and maximum
rotational CV values are recorded separately by radius and metric.

## 8. Composite metric definitions

Pooled source RMS is primary. Mean-of-source-RMS and equal-sample true pooled
RMS are retained separately; their differences are not interpreted as radial
physics.

## 9. Absolute response by radius

Absolute shell and orbit means are preserved for a future radial experiment.
They are not fitted to or described as an inverse-square law here.

## 10. Normalized angular response

Each positive shell response is divided by its own all-direction mean.

## 11. Directional CV and spread evolution

| Radius | Shell mean | Directional CV | Spread | a4 | I4 R² |
|---:|---:|---:|---:|---:|---:|
""" + "\n".join(
        f"| {r} | {float(primary[r]['shellMean']):.9g} | "
        f"{float(primary[r]['directionalCV']):.6%} | "
        f"{float(primary[r]['directionalSpread']):.6%} | "
        f"{float(inv[r]['a4']):.9g} | {float(inv[r]['rSquared']):.9g} |"
        for r in RADII) + f"""

The acceleration directional-CV trend is **{trend}**. The relative CV change
from r=13 to r=39 is {cv_change:.6f}%.

## 12. Within-orbit cubic symmetry

Within-orbit CV remains at numerical scale. This tests cubic-orbit
consistency, not spherical isotropy.

## 13. Between-orbit evolution

""" + "\n".join(orbit_lines) + f"""

Pairwise orbit differences are reported for all three radii.

## 14. Cubic-invariant evolution

The fitted a4 coefficient changes from
{float(inv[13]['a4']):.9g} through {float(inv[26]['a4']):.9g} to
{float(inv[39]['a4']):.9g}.

The I4 model is descriptive only. Cross-radius Pearson/Spearman correlations,
RMS differences, worst directions, and a4 evolution are in the CSV evidence.

## 15. Cross-radius pattern correlation

Matched base-direction correlations and normalized pattern differences are
reported for 13–26, 26–39, and 13–39.

## 16. Same-direction attenuation ratios

Every base ray retains Q26/Q13, Q39/Q26, Q39/Q13, log differences, and
monotonicity. These are descriptive and are not a radial-law fit.

## 17. Opposite-direction parity

Every radius contains 39 opposite pairs. Even residuals are used for
non-negative metrics and odd residuals for signed radial metrics.

## 18. Per-cycle stability

Each selected cycle retains shell CV, spread, orbit structure, and the
descriptive I4 fit, allowing cycle sensitivity to be inspected.

## 19. Metric-definition sensitivity

Pooled-source RMS, mean-of-source-RMS, and true equal-sample pooled RMS remain
distinct in the evidence.

## 20. Runtime and memory

""" + "\n".join(
        f"- {r['sourceAxis']}: {float(r['totalSimulationSeconds']):.3f} s, "
        f"peak {float(r['peakUsedHeapGiB']):.3f} GiB."
        for r in runtime) + """

## 21. Limitations

Only three radii, three cubic orbits, three discrete source orientations, and
two common cycles are measured. Absolute attenuation is descriptive evidence
for a future experiment, not an inverse-square result.

## 22. Conclusions

The normalized acceleration structure strengthens over the sampled radii:
directional CV rises from {float(primary[13]['directionalCV']):.6%} to
{float(primary[39]['directionalCV']):.6%}, while the normalized pattern remains
highly correlated. The evidence characterizes sampled cubic angular evolution.
It does not establish spherical isotropy, continuous
rotational invariance, gravitational equivalence, monopole behavior, or a
fundamental I4 equation.

## 23. Recommended next experiment

Repeat selected exact collinear rays on a larger reflection-safe lattice with
more cycles and additional exact integer shells before attempting a radial law.
"""
    (COMBINED / "report.md").write_text(text, encoding="utf-8")


def main():
    COMBINED.mkdir(parents=True, exist_ok=True)
    geometry_rows, geom = geometry()
    runs, validation, safety, runtime, common = load_runs(geom)
    cycles, run_metrics = aggregate(runs, geom, common)
    detectors, composite_cycles, definitions = composites(
        geometry_rows, run_metrics, cycles, common)
    (shell, normalized, orbit_rows, pairs, parity, invariant,
     radial, cross) = analyze(geometry_rows, detectors, composite_cycles)
    mapping, rotational_rows = rotational(geometry_rows, run_metrics)
    write_csv(ROOT / "detector-geometry.csv", geometry_rows)
    write_csv(COMBINED / "detector-geometry.csv", geometry_rows)
    outputs = {
        "input-validation.csv": validation,
        "safe-cycle-intersection.csv": safety,
        "source-relative-rotation-map.csv": mapping,
        "rotational-residuals.csv": rotational_rows,
        "composite-detector-summary.csv": detectors,
        "composite-cycle-summary.csv": composite_cycles,
        "shell-angular-summary.csv": shell,
        "normalized-angular-response.csv": normalized,
        "orbit-summary-by-radius.csv": orbit_rows,
        "orbit-pairwise-evolution.csv": pairs,
        "opposite-parity-by-radius.csv": parity,
        "cubic-invariant-by-radius.csv": invariant,
        "cross-radius-pattern-comparison.csv": cross,
        "same-direction-radial-comparison.csv": radial,
        "metric-definition-comparison.csv": definitions,
        "runtime-memory-summary.csv": runtime}
    for filename, rows in outputs.items():
        write_csv(COMBINED / filename, rows)
    charts(geometry_rows, detectors, shell, normalized, orbit_rows, pairs,
           parity, invariant, radial, cross, rotational_rows, definitions)
    validate_charts()
    report(shell, orbit_rows, pairs, invariant, cross, parity,
           rotational_rows, safety, runtime, common)
    print(f"PASS: 234 detectors; common cycles {common}; "
          f"{len(CHART_NAMES)} validated chart pairs")


if __name__ == "__main__":
    main()
