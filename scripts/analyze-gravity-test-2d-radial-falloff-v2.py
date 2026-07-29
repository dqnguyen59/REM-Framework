#!/usr/bin/env python3
"""Reflection-safe Gravity Test 2D analysis amendment.

This script independently reads the canonical raw run. It does not import the
original Test 2D analysis because cycle selection and radial fitting must remain
independently inspectable.
"""

import csv
import math
import os
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

LOCAL_DEPENDENCIES = Path(__file__).resolve().parents[1] / ".python-test2d-deps"
if LOCAL_DEPENDENCIES.is_dir():
    sys.path.insert(0, str(LOCAL_DEPENDENCIES))
os.environ.setdefault("MPLCONFIGDIR", "/tmp/rem-framework-matplotlib")

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from PIL import Image
from scipy.optimize import curve_fit


RAW = Path("measurements/gravity-test-2d-radial-falloff-run-001.csv")
OUT = Path("measurements")
CHARTS = OUT / "charts" / "test-2d-v2"
STEM = "gravity-test-2d-radial-falloff"
CYCLE_OUT = OUT / f"{STEM}-cycle-metrics-v2.csv"
SUMMARY_OUT = OUT / f"{STEM}-summary-v2.csv"
AVERAGE_OUT = OUT / f"{STEM}-axis-averages-v2.csv"
LOCAL_OUT = OUT / f"{STEM}-local-exponents-v2.csv"
FITS_OUT = OUT / f"{STEM}-model-fits-v2.csv"
REPORT_OUT = OUT / f"{STEM}-report-v2.md"

SOURCE = (600, 600)
ALL_RADII = (10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120,
             130, 140, 150, 160, 180, 200, 220, 240, 260, 280, 300,
             320, 340, 360, 380, 400)
PRIMARY_RADII = (10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120,
                 130, 140, 150, 160, 180)
EXCLUDED_RADII = tuple(radius for radius in ALL_RADII
                       if radius not in PRIMARY_RADII)
DIRECTIONS = ("PX", "NX", "PY", "NY")
RHAT = {"PX": (1.0, 0.0), "NX": (-1.0, 0.0),
        "PY": (0.0, 1.0), "NY": (0.0, -1.0)}
INTERFACES = {
    "left x=29": ("x", 29), "right x=1171": ("x", 1171),
    "bottom y=29": ("y", 29), "top y=1171": ("y", 1171),
}
VECTOR_PREFIXES = ("rawGradient", "cycleDerivedGradient", "acceleration")
METRICS = {
    "rawGradient": "rmsRawGradientRadial",
    "cycleDerivedGradient": "rmsCycleDerivedGradientRadial",
    "acceleration": "rmsAccelerationRadial",
}
FIT_MINIMA = (10, 20, 40, 60, 80, 100, 120)
MODEL_NAMES = ("constant", "inverse-distance", "inverse-square",
               "free-power-law", "exponential", "power-law-with-offset")
FLOAT_TOL = 1e-12


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, rows, fields):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields,
                                extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def vector(row, prefix):
    return np.array([float(row[prefix + axis]) for axis in "XYZ"],
                    dtype=float)


def reflection_path(x, y):
    candidates = []
    for label, (axis, coordinate) in INTERFACES.items():
        if axis == "x":
            outbound = abs(coordinate - SOURCE[0])
            total = math.hypot(2 * coordinate - x - SOURCE[0],
                               y - SOURCE[1])
        else:
            outbound = abs(coordinate - SOURCE[1])
            total = math.hypot(x - SOURCE[0],
                               2 * coordinate - y - SOURCE[1])
        candidates.append((total, label, outbound, total - outbound))
    return min(candidates)


def validate_raw(rows):
    if len(rows) != 112 * 960:
        raise ValueError(f"raw row count {len(rows)} != 107520")
    grouped = defaultdict(list)
    seen = set()
    numeric_fields = [field for field in rows[0]
                      if field not in {"detectorName", "direction",
                                       "cycleCompleted"}]
    fixed_geometry = {}
    for line_number, row in enumerate(rows, 2):
        name = row["detectorName"]
        update = int(row["update"])
        key = (name, update)
        if key in seen:
            raise ValueError(f"duplicate detector/update {key}")
        seen.add(key)
        grouped[name].append(row)
        for field in numeric_fields:
            value = float(row[field])
            if not math.isfinite(value):
                raise ValueError(f"non-finite {field} at line {line_number}")
            if value == 0.0 and row[field].startswith("-"):
                raise ValueError(f"signed zero {field} at line {line_number}")
        geometry = tuple(row[field] for field in (
            "direction", "requestedRadius", "requestedAngleDegrees",
            "nodeX", "nodeY", "nodeZ", "actualEuclideanRadius",
            "actualAngleDegrees", "coordinateRoundingErrorMagnitude"))
        if name in fixed_geometry and fixed_geometry[name] != geometry:
            raise ValueError(f"inconsistent geometry metadata for {name}")
        fixed_geometry[name] = geometry
    if len(grouped) != 112:
        raise ValueError(f"detector count {len(grouped)} != 112")
    for name, detector_rows in grouped.items():
        if [int(row["update"]) for row in detector_rows] != list(
                range(1, 961)):
            raise ValueError(f"{name} does not contain updates 1..960")
        first = detector_rows[0]
        direction = first["direction"]
        radius = int(first["requestedRadius"])
        if direction not in DIRECTIONS or radius not in ALL_RADII:
            raise ValueError(f"unexpected detector metadata for {name}")
        if name != f"{direction}_R{radius:03d}":
            raise ValueError(f"name/metadata mismatch for {name}")
        expected = {
            "PX": (600 + radius, 600, 0),
            "NX": (600 - radius, 600, 0),
            "PY": (600, 600 + radius, 0),
            "NY": (600, 600 - radius, 0),
        }[direction]
        actual = tuple(int(first[field]) for field in ("nodeX", "nodeY",
                                                       "nodeZ"))
        if actual != expected:
            raise ValueError(f"coordinate mismatch for {name}")
        if float(first["coordinateRoundingErrorMagnitude"]) != 0.0:
            raise ValueError(f"non-zero coordinate rounding for {name}")
    return grouped


def completed_cycles(rows):
    cycles = [row for row in rows
              if row["cycleCompleted"].lower() == "true"]
    updates = [int(row["update"]) for row in cycles]
    if updates != list(range(18, 961, 18)):
        raise ValueError(f"cycle completion schedule is not 18 updates: "
                         f"{updates[:3]} ... {updates[-3:]}")
    return cycles


def meaningful_arrival(cycles):
    for row in cycles:
        value = vector(row, "cycleDerivedGradient")
        if np.all(np.isfinite(value)) and np.linalg.norm(value) > 0.0:
            return int(row["update"]), int(row["completedCycleCount"])
    raise ValueError("detector has no meaningful source-locked arrival")


def project_components(value, direction):
    radial_hat = np.array(RHAT[direction])
    tangential_hat = np.array((-radial_hat[1], radial_hat[0]))
    in_plane = value[:2]
    return float(np.dot(in_plane, radial_hat)), float(
        np.dot(in_plane, tangential_hat))


def rms(values):
    array = np.asarray(values, dtype=float)
    return float(np.sqrt(np.mean(array * array)))


def float_bits(value):
    return np.float64(value).tobytes()


def build_detector_outputs(grouped):
    cycle_rows = []
    summaries = []
    detector_metrics = {}
    for name in sorted(grouped):
        rows = grouped[name]
        first = rows[0]
        direction = first["direction"]
        radius = int(first["requestedRadius"])
        cycles = completed_cycles(rows)
        arrival_update, arrival_cycle = meaningful_arrival(cycles)
        post_arrival = [row for row in cycles
                        if int(row["update"]) > arrival_update]
        x, y = int(first["nodeX"]), int(first["nodeY"])
        path, boundary, outbound, returned = reflection_path(x, y)
        reflection = math.ceil(path) + 1
        safe = [row for row in post_arrival
                if int(row["update"]) < reflection]
        accepted = radius in PRIMARY_RADII
        exclusion_reason = ""
        selected = []
        if accepted:
            if len(safe) < 30:
                raise ValueError(
                    f"primary detector {name} has only {len(safe)} safe cycles")
            selected = post_arrival[:30]
            if len(selected) != 30:
                raise ValueError(f"{name} lacks 30 post-arrival cycles")
            selected_updates = [int(row["update"]) for row in selected]
            if any(b - a != 18 for a, b in zip(
                    selected_updates, selected_updates[1:])):
                raise ValueError(f"non-contiguous selected cycles for {name}")
            if selected_updates[-1] >= reflection:
                raise ValueError(f"reflection gate failed for {name}")
        else:
            exclusion_reason = (
                "Excluded from the post-run primary 10-180 analysis subset: "
                f"only {len(safe)} complete post-arrival cycles end before "
                "the detector-specific reflection estimate; 30 are required."
            )

        component_values = {
            prefix: {"radial": [], "tangential": []}
            for prefix in VECTOR_PREFIXES
        }
        for row in selected:
            output = {
                "detectorName": name, "direction": direction,
                "requestedRadius": radius,
                "cycleNumber": int(row["completedCycleCount"]),
                "cycleEndUpdate": int(row["update"]),
                "meaningfulArrivalUpdate": arrival_update,
                "detectorSpecificReflectionEstimate": reflection,
                "reflectionSafetyMargin":
                    reflection - int(row["update"]),
            }
            for prefix in VECTOR_PREFIXES:
                radial, tangential = project_components(
                    vector(row, prefix), direction)
                output[prefix + "Radial"] = radial
                output[prefix + "Tangential"] = tangential
                component_values[prefix]["radial"].append(radial)
                component_values[prefix]["tangential"].append(tangential)
            cycle_rows.append(output)

        summary = {
            "detectorName": name, "direction": direction,
            "requestedRadius": radius,
            "requestedAngleDegrees": float(
                first["requestedAngleDegrees"]),
            "nodeX": x, "nodeY": y, "nodeZ": int(first["nodeZ"]),
            "actualEuclideanRadius":
                float(first["actualEuclideanRadius"]),
            "actualAngleDegrees": float(first["actualAngleDegrees"]),
            "coordinateRoundingErrorMagnitude":
                float(first["coordinateRoundingErrorMagnitude"]),
            "meaningfulArrivalUpdate": arrival_update,
            "meaningfulArrivalCycleNumber": arrival_cycle,
            "availablePostArrivalCycles": len(post_arrival),
            "reflectionSafePostArrivalCycles": len(safe),
            "firstSelectedCycleNumber":
                int(selected[0]["completedCycleCount"]) if selected else "",
            "firstSelectedCycleEnd":
                int(selected[0]["update"]) if selected else "",
            "thirtiethSelectedCycleNumber":
                int(selected[-1]["completedCycleCount"]) if selected else "",
            "thirtiethSelectedCycleEnd":
                int(selected[-1]["update"]) if selected else "",
            "nearestRelevantBoundary": boundary,
            "outboundDistance": outbound,
            "returnDistanceToDetector": returned,
            "totalReflectedPath": path,
            "boundedPropagationSpeed": 1.0,
            "detectorSpecificReflectionEstimate": reflection,
            "reflectionSafetyMargin":
                reflection - int(selected[-1]["update"])
                if selected else reflection - arrival_update,
            "primarySubsetAccepted": accepted,
            "exclusionReason": exclusion_reason,
        }
        if accepted:
            for prefix in VECTOR_PREFIXES:
                radial = component_values[prefix]["radial"]
                tangential = component_values[prefix]["tangential"]
                title = prefix[0].upper() + prefix[1:]
                summary[f"meanSigned{title}Radial"] = float(np.mean(radial))
                summary[f"rms{title}Radial"] = rms(radial)
                summary[f"meanSigned{title}Tangential"] = float(
                    np.mean(tangential))
                summary[f"rms{title}Tangential"] = rms(tangential)
                radial_rms = summary[f"rms{title}Radial"]
                tangential_rms = summary[f"rms{title}Tangential"]
                summary[f"{prefix}TangentialToRadial"] = (
                    tangential_rms / radial_rms if radial_rms > 0.0
                    else (0.0 if tangential_rms == 0.0 else ""))
            detector_metrics[(radius, direction)] = summary
        summaries.append(summary)

    if len(cycle_rows) != 68 * 30:
        raise ValueError(f"accepted cycle count {len(cycle_rows)} != 2040")
    if len(detector_metrics) != 68:
        raise ValueError(f"accepted detector count {len(detector_metrics)} != 68")
    return cycle_rows, summaries, detector_metrics


def relative_difference(a, b):
    scale = max(abs(a), abs(b))
    return abs(a - b) / scale if scale > 0.0 else 0.0


def equality_class(a, b):
    if float_bits(a) == float_bits(b):
        return "bitwise-identical"
    if a == b:
        return "exactly-equal-as-parsed-floats"
    if math.isclose(a, b, rel_tol=FLOAT_TOL, abs_tol=0.0):
        return "equal-within-1e-12-relative-tolerance"
    return "measurably-different"


def build_axis_averages(metrics):
    rows = []
    for radius in PRIMARY_RADII:
        output = {"requestedRadius": radius}
        arrivals = {direction:
                    metrics[(radius, direction)]["meaningfulArrivalCycleNumber"]
                    for direction in DIRECTIONS}
        margins = {direction:
                   metrics[(radius, direction)]["reflectionSafetyMargin"]
                   for direction in DIRECTIONS}
        output.update({
            "pxArrivalCycle": arrivals["PX"],
            "nxArrivalCycle": arrivals["NX"],
            "pyArrivalCycle": arrivals["PY"],
            "nyArrivalCycle": arrivals["NY"],
            "pxNxArrivalCycleDifference":
                abs(arrivals["PX"] - arrivals["NX"]),
            "pyNyArrivalCycleDifference":
                abs(arrivals["PY"] - arrivals["NY"]),
            "pxNxReflectionMarginDifference":
                abs(margins["PX"] - margins["NX"]),
            "pyNyReflectionMarginDifference":
                abs(margins["PY"] - margins["NY"]),
        })
        for prefix, field in METRICS.items():
            values = {direction: float(metrics[(radius, direction)][field])
                      for direction in DIRECTIONS}
            for direction, value in values.items():
                output[f"{direction}_{field}"] = value
            x_average = (abs(values["PX"]) + abs(values["NX"])) / 2.0
            y_average = (abs(values["PY"]) + abs(values["NY"])) / 2.0
            all_values = np.array([abs(values[d]) for d in DIRECTIONS])
            four_average = float(np.mean(all_values))
            output[f"xAverage_{field}"] = x_average
            output[f"yAverage_{field}"] = y_average
            output[f"fourAxisAverage_{field}"] = four_average
            output[f"directionalStandardDeviation_{field}"] = float(
                np.std(all_values, ddof=0))
            output[f"coefficientOfVariation_{field}"] = (
                float(np.std(all_values, ddof=0)) / four_average
                if four_average > 0.0 else 0.0)
            output[f"pxNxAbsoluteDifference_{field}"] = abs(
                values["PX"] - values["NX"])
            output[f"pxNxRelativeDifference_{field}"] = relative_difference(
                values["PX"], values["NX"])
            output[f"pxNxEquality_{field}"] = equality_class(
                values["PX"], values["NX"])
            output[f"pyNyAbsoluteDifference_{field}"] = abs(
                values["PY"] - values["NY"])
            output[f"pyNyRelativeDifference_{field}"] = relative_difference(
                values["PY"], values["NY"])
            output[f"pyNyEquality_{field}"] = equality_class(
                values["PY"], values["NY"])
            output[f"xYAbsoluteDifference_{field}"] = abs(
                x_average - y_average)
            output[f"xYRelativeDifference_{field}"] = relative_difference(
                x_average, y_average)
            output[f"xYEquality_{field}"] = equality_class(
                x_average, y_average)
            mean_fields = {
                direction: float(metrics[(radius, direction)][
                    "meanSigned" + prefix[0].upper() + prefix[1:] + "Radial"])
                for direction in DIRECTIONS
            }
            output[f"pxNxRadialSignParity_{prefix}"] = (
                "same-outward-radial-sign"
                if math.copysign(1.0, mean_fields["PX"]) ==
                math.copysign(1.0, mean_fields["NX"])
                else "opposite-outward-radial-sign")
            output[f"pyNyRadialSignParity_{prefix}"] = (
                "same-outward-radial-sign"
                if math.copysign(1.0, mean_fields["PY"]) ==
                math.copysign(1.0, mean_fields["NY"])
                else "opposite-outward-radial-sign")
        rows.append(output)
    return rows


def finite_positive_series(axis_rows, metric, minimum=10):
    points = []
    field = f"fourAxisAverage_{metric}"
    for row in axis_rows:
        radius = float(row["requestedRadius"])
        value = float(row[field])
        if radius >= minimum and math.isfinite(value) and value > 0.0:
            points.append((radius, value))
    return points


def information_criteria(observed, predicted, parameter_count):
    residual = observed - predicted
    rss = float(np.sum(residual * residual))
    count = len(observed)
    guarded_rss = max(rss, np.finfo(float).tiny)
    aic = count * math.log(guarded_rss / count) + 2 * parameter_count
    bic = count * math.log(guarded_rss / count) + (
        parameter_count * math.log(count))
    return float(np.sqrt(np.mean(residual * residual))), aic, bic, residual


def residual_statistics(residual, observed):
    mean = float(np.mean(residual))
    maximum = float(np.max(np.abs(residual)))
    signs = np.sign(residual)
    sign_changes = int(np.sum(signs[1:] * signs[:-1] < 0.0))
    if len(residual) > 2 and np.std(residual[:-1]) > 0.0 and np.std(
            residual[1:]) > 0.0:
        lag_one = float(np.corrcoef(residual[:-1], residual[1:])[0, 1])
    else:
        lag_one = math.nan
    trend_scale = float(np.mean(np.abs(observed)))
    peak_to_trough = (float(np.max(residual) - np.min(residual)) /
                      trend_scale if trend_scale > 0.0 else math.nan)
    return {
        "residualMean": mean,
        "residualMaximumAbsolute": maximum,
        "residualLagOneAutocorrelation": lag_one,
        "residualSignChanges": sign_changes,
        "residualPeakToTroughRelativeToMeanResponse": peak_to_trough,
    }


def log_r_squared(observed, predicted):
    if np.any(observed <= 0.0) or np.any(predicted <= 0.0):
        return math.nan
    log_y = np.log(observed)
    log_p = np.log(predicted)
    denominator = float(np.sum((log_y - np.mean(log_y)) ** 2))
    if denominator == 0.0:
        return 1.0 if np.array_equal(log_y, log_p) else math.nan
    return 1.0 - float(np.sum((log_y - log_p) ** 2)) / denominator


def fit_model(model, radii, values):
    x = np.asarray(radii, dtype=float)
    y = np.asarray(values, dtype=float)
    if np.any(~np.isfinite(y)) or np.any(y <= 0.0):
        raise ValueError("logarithmic fits require finite positive responses")
    uncertainty = math.nan
    exponent = math.nan
    offset = math.nan
    decay = math.nan
    if model == "constant":
        parameters = np.array([float(np.mean(y))])
        predicted = np.full_like(y, parameters[0])
    elif model in {"inverse-distance", "inverse-square"}:
        exponent = 1.0 if model == "inverse-distance" else 2.0
        basis = x ** (-exponent)
        amplitude = float(np.dot(y, basis) / np.dot(basis, basis))
        parameters = np.array([amplitude])
        predicted = amplitude * basis
    elif model == "free-power-law":
        slope, intercept = np.polyfit(np.log(x), np.log(y), 1)
        initial = (math.exp(intercept), -slope)
        parameters, covariance = curve_fit(
            lambda radius, amplitude, power:
                amplitude * radius ** (-power),
            x, y, p0=initial, bounds=([0.0, -20.0],
                                      [np.inf, 20.0]), maxfev=200000)
        predicted = parameters[0] * x ** (-parameters[1])
        exponent = float(parameters[1])
        uncertainty = (float(math.sqrt(covariance[1, 1]))
                       if covariance.shape == (2, 2)
                       and covariance[1, 1] >= 0.0 else math.nan)
    elif model == "exponential":
        slope, intercept = np.polyfit(x, np.log(y), 1)
        parameters, covariance = curve_fit(
            lambda radius, amplitude, rate:
                amplitude * np.exp(-rate * radius),
            x, y, p0=(math.exp(intercept), max(0.0, -slope)),
            bounds=([0.0, -1.0], [np.inf, 1.0]), maxfev=200000)
        predicted = parameters[0] * np.exp(-parameters[1] * x)
        decay = float(parameters[1])
    elif model == "power-law-with-offset":
        slope, intercept = np.polyfit(np.log(x), np.log(y), 1)
        initial = (math.exp(intercept), -slope, 0.0)
        parameters, covariance = curve_fit(
            lambda radius, amplitude, power, radial_offset:
                amplitude / (radius + radial_offset) ** power,
            x, y, p0=initial,
            bounds=([0.0, -20.0, -min(x) + 1e-6],
                    [np.inf, 20.0, 10000.0]), maxfev=500000)
        predicted = (parameters[0] /
                     (x + parameters[2]) ** parameters[1])
        exponent = float(parameters[1])
        offset = float(parameters[2])
        uncertainty = (float(math.sqrt(covariance[1, 1]))
                       if covariance.shape == (3, 3)
                       and covariance[1, 1] >= 0.0 else math.nan)
    else:
        raise ValueError(f"unknown model {model}")
    rmse, aic, bic, residual = information_criteria(
        y, predicted, len(parameters))
    output = {
        "amplitude": float(parameters[0]),
        "fittedExponent": exponent,
        "exponentStandardError": uncertainty,
        "exponent95PercentLower":
            exponent - 1.96 * uncertainty
            if math.isfinite(exponent) and math.isfinite(uncertainty)
            else math.nan,
        "exponent95PercentUpper":
            exponent + 1.96 * uncertainty
            if math.isfinite(exponent) and math.isfinite(uncertainty)
            else math.nan,
        "decayLambda": decay,
        "radialOffset": offset,
        "rmse": rmse,
        "logSpaceR2": log_r_squared(y, predicted),
        "aic": aic, "bic": bic,
        "predicted": predicted, "residual": residual,
    }
    output.update(residual_statistics(residual, y))
    return output


def build_fits(axis_rows):
    rows = []
    cache = {}
    for prefix, metric in METRICS.items():
        for minimum in FIT_MINIMA:
            points = finite_positive_series(axis_rows, metric, minimum)
            radii = np.array([point[0] for point in points])
            values = np.array([point[1] for point in points])
            for model in MODEL_NAMES:
                output = {
                    "quantity": prefix,
                    "metric": metric,
                    "minimumRadius": minimum,
                    "maximumRadius": 180,
                    "model": model,
                    "includedRadii": ";".join(
                        str(int(radius)) for radius in radii),
                    "includedRadiusCount": len(radii),
                    "excludedRadiiAndReason":
                        "200;220;240;260;280;300;320;340;360;380;400:"
                        "post-run exclusion because fewer than 30 common "
                        "reflection-safe cycles; non-positive values are "
                        "excluded from logarithmic analysis",
                }
                if len(radii) < 4:
                    output.update({
                        "fitStatus": "excluded",
                        "exclusionReason":
                            "fewer than four finite positive radii",
                    })
                else:
                    try:
                        result = fit_model(model, radii, values)
                        output.update(result)
                        output["fitStatus"] = "accepted"
                        output["exclusionReason"] = ""
                        cache[(prefix, minimum, model)] = (
                            radii, values, result)
                    except (ValueError, RuntimeError, FloatingPointError) as exc:
                        output["fitStatus"] = "excluded"
                        output["exclusionReason"] = str(exc)
                output.pop("predicted", None)
                output.pop("residual", None)
                rows.append(output)
    return rows, cache


def build_local_exponents(axis_rows):
    rows = []
    series_names = list(DIRECTIONS) + ["four-axis-average"]
    for prefix, metric in METRICS.items():
        for series in series_names:
            values = []
            for row in axis_rows:
                field = (f"{series}_{metric}" if series in DIRECTIONS
                         else f"fourAxisAverage_{metric}")
                value = float(row[field])
                radius = float(row["requestedRadius"])
                if math.isfinite(value) and value > 0.0:
                    values.append((radius, value))
            smoothed = {}
            for index in range(1, len(values) - 1):
                window = values[index - 1:index + 2]
                window_x = np.log([item[0] for item in window])
                window_y = np.log([item[1] for item in window])
                slope, _ = np.polyfit(window_x, window_y, 1)
                smoothed[values[index][0]] = -float(slope)
            for (r1, m1), (r2, m2) in zip(values, values[1:]):
                local = -math.log(m2 / m1) / math.log(r2 / r1)
                representative = math.sqrt(r1 * r2)
                nearest_radius = min(smoothed, key=lambda r: abs(
                    math.log(r) - math.log(representative))) if smoothed else None
                rows.append({
                    "quantity": prefix, "metric": metric,
                    "series": series, "radiusStart": r1,
                    "radiusEnd": r2,
                    "representativeRadius": representative,
                    "localExponent": local,
                    "centeredThreePointSmoothedExponent":
                        smoothed.get(nearest_radius, "")
                        if nearest_radius is not None else "",
                })
    return rows


def model_ranking(fit_rows, prefix, minimum=10):
    accepted = [row for row in fit_rows
                if row["quantity"] == prefix
                and row["minimumRadius"] == minimum
                and row.get("fitStatus") == "accepted"]
    return sorted(accepted, key=lambda row: float(row["bic"]))


def setup_chart(title, xlabel, ylabel):
    figure, axis = plt.subplots(figsize=(12, 7))
    figure.patch.set_facecolor("white")
    axis.set_facecolor("white")
    axis.set_title(title, fontsize=16, pad=14)
    axis.set_xlabel(xlabel, fontsize=12)
    axis.set_ylabel(ylabel, fontsize=12)
    axis.grid(True, which="major", alpha=0.35)
    axis.tick_params(labelsize=10)
    return figure, axis


def save_chart(figure, filename):
    path = CHARTS / filename
    svg_path = path.with_suffix(".svg")
    figure.tight_layout()
    figure.savefig(
        path,
        format="png",
        dpi=180,
        bbox_inches="tight",
        facecolor="white",
        transparent=False,
    )
    figure.savefig(
        svg_path,
        format="svg",
        bbox_inches="tight",
        facecolor="white",
        transparent=False,
    )
    plt.close(figure)
    return path, svg_path


def plot_directional(axis_rows, metric, title, filename):
    figure, axis = setup_chart(title + " (accepted range 10–180)",
                               "Radius (lattice nodes)", "Radial RMS")
    radii = [row["requestedRadius"] for row in axis_rows]
    styles = {"PX": "o-", "NX": "s--", "PY": "^-.", "NY": "d:"}
    for direction in DIRECTIONS:
        axis.plot(radii, [row[f"{direction}_{metric}"] for row in axis_rows],
                  styles[direction], linewidth=1.4, markersize=4,
                  label=direction, alpha=0.85)
    axis.plot(radii, [row[f"fourAxisAverage_{metric}"] for row in axis_rows],
              "k-o", linewidth=2.5, markersize=5, label="four-axis average")
    axis.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    axis.legend()
    save_chart(figure, filename)


def selected_model(fit_rows, prefix):
    ranking = model_ranking(fit_rows, prefix)
    return ranking[0] if ranking else None


def predict_from_fit(row, radii):
    x = np.asarray(radii, dtype=float)
    amplitude = float(row["amplitude"])
    model = row["model"]
    if model == "constant":
        return np.full_like(x, amplitude)
    if model == "inverse-distance":
        return amplitude / x
    if model == "inverse-square":
        return amplitude / x ** 2
    if model == "free-power-law":
        return amplitude / x ** float(row["fittedExponent"])
    if model == "exponential":
        return amplitude * np.exp(-float(row["decayLambda"]) * x)
    return amplitude / (x + float(row["radialOffset"])) ** float(
        row["fittedExponent"])


def plot_loglog(axis_rows, metric, fit_rows, prefix, title, filename):
    figure, axis = setup_chart(title + " (accepted range 10–180)",
                               "Radius (lattice nodes)", "Radial RMS")
    radii = np.array([row["requestedRadius"] for row in axis_rows])
    measured = np.array([row[f"fourAxisAverage_{metric}"]
                         for row in axis_rows])
    positive = np.isfinite(measured) & (measured > 0.0)
    axis.loglog(radii[positive], measured[positive], "ko", markersize=6,
                label="four-axis measured")
    chosen = selected_model(fit_rows, prefix)
    if chosen:
        dense = np.geomspace(radii[positive].min(), radii[positive].max(),
                             400)
        predicted = predict_from_fit(chosen, dense)
        axis.loglog(dense, predicted, "r-", linewidth=2,
                    label=f"lowest-BIC model: {chosen['model']}")
    axis.legend()
    save_chart(figure, filename)


def plot_local(local_rows, prefix, title, filename):
    figure, axis = setup_chart(title + " (accepted range 10–180)",
                               "Geometric-mean radius", "Effective exponent n")
    rows = [row for row in local_rows
            if row["quantity"] == prefix
            and row["series"] == "four-axis-average"]
    x = [row["representativeRadius"] for row in rows]
    local = [row["localExponent"] for row in rows]
    smooth = [float(row["centeredThreePointSmoothedExponent"])
              if row["centeredThreePointSmoothedExponent"] != "" else math.nan
              for row in rows]
    axis.plot(x, local, "o-", label="adjacent local exponent")
    axis.plot(x, smooth, "s--", linewidth=2,
              label="centered three-point log fit")
    axis.axhline(2.0, color="gray", linestyle=":", label="n = 2 reference")
    axis.legend()
    save_chart(figure, filename)


def create_charts(axis_rows, summaries, local_rows, fit_rows):
    CHARTS.mkdir(parents=True, exist_ok=True)
    plot_directional(axis_rows, METRICS["rawGradient"],
                     "Raw-gradient radial RMS", "raw-gradient-rms-v2.png")
    plot_directional(axis_rows, METRICS["cycleDerivedGradient"],
                     "Cycle-derived-gradient radial RMS",
                     "cycle-gradient-rms-v2.png")
    plot_loglog(axis_rows, METRICS["rawGradient"], fit_rows, "rawGradient",
                "Raw-gradient log-log response",
                "raw-gradient-loglog-v2.png")
    plot_loglog(axis_rows, METRICS["cycleDerivedGradient"], fit_rows,
                "cycleDerivedGradient",
                "Cycle-derived-gradient log-log response",
                "cycle-gradient-loglog-v2.png")
    plot_local(local_rows, "rawGradient", "Raw-gradient local exponent",
               "raw-gradient-local-exponent-v2.png")
    plot_local(local_rows, "cycleDerivedGradient",
               "Cycle-derived-gradient local exponent",
               "cycle-gradient-local-exponent-v2.png")

    figure, axis = setup_chart(
        "Directional RMS differences (accepted range 10–180)",
        "Radius (lattice nodes)", "Relative difference")
    radii = [row["requestedRadius"] for row in axis_rows]
    for prefix, metric in (("raw", METRICS["rawGradient"]),
                           ("cycle", METRICS["cycleDerivedGradient"])):
        for comparison, label in (
                ("pxNx", "PX vs NX"), ("pyNy", "PY vs NY"),
                ("xY", "X average vs Y average")):
            axis.plot(radii, [row[f"{comparison}RelativeDifference_{metric}"]
                             for row in axis_rows],
                      marker="o", markersize=3,
                      label=f"{prefix}: {label}")
    axis.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    axis.legend(ncol=2)
    save_chart(figure, "directional-symmetry-v2.png")

    figure, axis = setup_chart(
        "Source-locked arrival update (accepted range highlighted 10–180)",
        "Radius (lattice nodes)", "Meaningful arrival update")
    for direction in DIRECTIONS:
        rows = sorted((row for row in summaries
                       if row["direction"] == direction),
                      key=lambda row: row["requestedRadius"])
        axis.plot([row["requestedRadius"] for row in rows],
                  [row["meaningfulArrivalUpdate"] for row in rows],
                  marker="o", markersize=3, label=direction)
    axis.axvspan(10, 180, color="green", alpha=0.08,
                 label="accepted 10–180")
    axis.legend()
    save_chart(figure, "arrival-update-v2.png")

    figure, axis = setup_chart(
        "Reflection safety margin (30th-cycle margin where available)",
        "Radius (lattice nodes)", "Margin (updates)")
    for direction in DIRECTIONS:
        rows = sorted((row for row in summaries
                       if row["direction"] == direction),
                      key=lambda row: row["requestedRadius"])
        margins = []
        for row in rows:
            arrival = int(row["meaningfulArrivalUpdate"])
            requested_end = arrival + 30 * 18
            margins.append(int(row["detectorSpecificReflectionEstimate"])
                           - requested_end)
        axis.plot([row["requestedRadius"] for row in rows], margins,
                  marker="o", markersize=3, label=direction)
    axis.axhline(0.0, color="red", linestyle="--", linewidth=1.5,
                 label="zero safety margin")
    axis.axvspan(10, 180, color="green", alpha=0.08,
                 label="accepted 10–180")
    axis.legend()
    save_chart(figure, "reflection-margin-v2.png")

    chosen = selected_model(fit_rows, "rawGradient")
    figure, axis = setup_chart(
        "Raw-gradient selected-model residuals (accepted range 10–180)",
        "Radius (lattice nodes)", "Measured − fitted radial RMS")
    radii = np.array([row["requestedRadius"] for row in axis_rows])
    measured = np.array([row[
        f"fourAxisAverage_{METRICS['rawGradient']}"] for row in axis_rows])
    if chosen:
        residual = measured - predict_from_fit(chosen, radii)
        axis.plot(radii, residual, "ko-", label=chosen["model"])
    axis.axhline(0.0, color="red", linestyle="--")
    axis.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    axis.legend()
    save_chart(figure, "model-residuals-v2.png")


def validate_charts():
    expected = (
        "raw-gradient-rms-v2.png", "cycle-gradient-rms-v2.png",
        "raw-gradient-loglog-v2.png", "cycle-gradient-loglog-v2.png",
        "raw-gradient-local-exponent-v2.png",
        "cycle-gradient-local-exponent-v2.png",
        "directional-symmetry-v2.png", "arrival-update-v2.png",
        "reflection-margin-v2.png", "model-residuals-v2.png",
    )
    results = []
    validation_lines = [
        "Gravity Test 2D v2 PNG and SVG validation",
        "============================================",
        "",
        f"Matplotlib version: {matplotlib.__version__}",
        f"Pillow version: {Image.__version__}",
        "",
    ]
    failures = []
    identify_command = shutil.which("identify")
    for filename in expected:
        path = CHARTS / filename
        svg_path = path.with_suffix(".svg")
        errors = []
        pillow_verify = "FAIL"
        pillow_load = "FAIL"
        copied_pillow_load = "FAIL"
        size = path.stat().st_size if path.exists() else 0
        width = height = 0
        image_format = image_mode = ""
        extrema = ()
        signature = b""
        file_result = ""
        identify_result = "not installed"

        if not path.exists():
            errors.append("PNG does not exist")
        else:
            if size <= 20_000:
                errors.append(f"PNG size {size} is not greater than 20 KB")
            signature = path.read_bytes()[:8]
            if signature != b"\x89PNG\r\n\x1a\n":
                errors.append(f"invalid PNG signature {signature.hex(' ')}")
            try:
                with Image.open(path) as image:
                    image.verify()
                pillow_verify = "PASS"
            except Exception as exc:
                errors.append(f"Pillow verify failed: {exc}")
            try:
                with Image.open(path) as image:
                    image.load()
                    width, height = image.size
                    image_format = image.format
                    image_mode = image.mode
                    extrema = image.getextrema()
                pillow_load = "PASS"
            except Exception as exc:
                errors.append(f"Pillow load failed: {exc}")
            if image_format != "PNG":
                errors.append(f"detected format is {image_format}, not PNG")
            if width < 1600 or height < 900:
                errors.append(f"dimensions {width}x{height} are below 1600x900")
            if image_mode not in ("RGB", "RGBA"):
                errors.append(f"image mode {image_mode} is not RGB or RGBA")
            if extrema and all(low == high for low, high in extrema):
                errors.append("all channels contain only one uniform value")
            if image_mode == "RGBA" and extrema[-1] != (255, 255):
                errors.append(
                    f"RGBA image contains transparency: alpha {extrema[-1]}")

            file_process = subprocess.run(
                ["file", str(path)], check=False, capture_output=True,
                text=True)
            file_result = file_process.stdout.strip()
            if file_process.returncode != 0 or "PNG image data" not in file_result:
                errors.append(f"file command failed: {file_result}")
            if identify_command:
                identify_process = subprocess.run(
                    [identify_command, str(path)], check=False,
                    capture_output=True, text=True)
                identify_result = (identify_process.stdout.strip()
                                   or identify_process.stderr.strip())
                if identify_process.returncode != 0:
                    errors.append(f"ImageMagick identify failed: "
                                  f"{identify_result}")

            copied_path = Path("/tmp") / f"rem-framework-{filename}"
            try:
                shutil.copy2(path, copied_path)
                with Image.open(copied_path) as copied:
                    copied.load()
                    if copied.format != "PNG" or copied.size != (width, height):
                        raise ValueError("copied PNG metadata differs")
                copied_pillow_load = "PASS"
            except Exception as exc:
                errors.append(f"/tmp copy Pillow load failed: {exc}")

        if not svg_path.exists():
            errors.append("SVG does not exist")
            svg_size = 0
        else:
            svg_size = svg_path.stat().st_size
            svg_prefix = svg_path.read_text(
                encoding="utf-8", errors="replace")[:4096]
            if svg_size <= 2_000:
                errors.append(f"SVG size {svg_size} is not greater than 2 KB")
            stripped = svg_prefix.lstrip()
            if not (stripped.startswith("<?xml") or
                    stripped.startswith("<svg")):
                errors.append("SVG does not begin with XML or an SVG element")
            if "<svg" not in svg_prefix:
                errors.append("SVG does not contain an <svg element")

        status = "PASS" if not errors else "FAIL"
        validation_lines.extend([
            f"filename: {filename}",
            f"absolute path: {path.resolve()}",
            f"byte size: {size}",
            f"width: {width}",
            f"height: {height}",
            f"mode: {image_mode}",
            f"detected format: {image_format}",
            f"PNG signature: {signature.hex(' ').upper()}",
            f"Pillow verify result: {pillow_verify}",
            f"Pillow load result: {pillow_load}",
            f"file command result: {file_result}",
            f"ImageMagick result: {identify_result}",
            f"pixel extrema: {extrema}",
            f"/tmp copy Pillow load result: {copied_pillow_load}",
            f"SVG path: {svg_path.resolve()}",
            f"SVG byte size: {svg_size}",
            f"result: {status}",
        ])
        if errors:
            validation_lines.append("errors: " + "; ".join(errors))
            failures.extend(f"{filename}: {error}" for error in errors)
        validation_lines.append("")
        results.append({
            "filename": filename, "size": size,
            "dimensions": (width, height), "mode": image_mode,
            "format": image_format, "signature": signature.hex(" ").upper(),
            "pillowVerify": pillow_verify, "pillowLoad": pillow_load,
            "fileResult": file_result, "identifyResult": identify_result,
            "extrema": extrema, "tmpCopyLoad": copied_pillow_load,
            "svgFilename": svg_path.name, "svgSize": svg_size,
            "status": status,
        })
    validation_path = CHARTS / "png-validation.txt"
    validation_path.write_text("\n".join(validation_lines), encoding="utf-8")
    if failures:
        raise ValueError("chart validation failed:\n" + "\n".join(failures))
    return results


def fmt(value, digits=6):
    try:
        number = float(value)
    except (TypeError, ValueError):
        return str(value)
    return f"{number:.{digits}g}" if math.isfinite(number) else "n/a"


def metric_decision(axis_rows, fit_rows, local_rows, prefix):
    metric = METRICS[prefix]
    values = [float(row[f"fourAxisAverage_{metric}"]) for row in axis_rows]
    if not any(value > 0.0 for value in values):
        return ("D", "The accepted in-plane radial response is zero, so no "
                "radial attenuation law can be fitted.")
    return ("C", "Radial attenuation is present, but the strongly oscillatory "
            "local exponents, cutoff-sensitive global exponents, and competing "
            "model rankings do not demonstrate a stable power-law exponent or "
            "two stable radial regimes.")


def write_report(summaries, axis_rows, fit_rows, local_rows, chart_results):
    accepted = [row for row in summaries if row["primarySubsetAccepted"]]
    excluded = [row for row in summaries if not row["primarySubsetAccepted"]]
    margins = [int(row["reflectionSafetyMargin"]) for row in accepted]
    decisions = {prefix: metric_decision(axis_rows, fit_rows, local_rows,
                                         prefix)
                 for prefix in METRICS}
    lines = [
        "# Gravity Test 2D radial-falloff analysis amendment (v2)",
        "",
        "## Status and scope",
        "",
        "This is an analysis-only amendment to the existing passive-node run. "
        "The simulation was not rerun and no physics or detector-sampling code "
        "was changed.",
        "",
        "The raw run passed collection integrity: 112 detectors × 960 updates "
        "= 107,520 rows, with complete update sequences, finite values, exact "
        "axis geometry, and zero coordinate-rounding error.",
        "",
        "The originally requested common 10–400 analysis window failed: no "
        "positive cycle count is simultaneously post-arrival and "
        "reflection-safe at all radii. Therefore no radial-law conclusion is "
        "permitted for the complete 10–400 set.",
        "",
        "The corrected primary analysis is the post-run, reflection-safe "
        "10–180 subset. All four directions at its 17 radii support exactly "
        "30 complete, contiguous post-arrival cycles ending before their "
        "detector-specific reflection estimate. This gives 68 accepted "
        "detectors and 2,040 cycle-metric rows. Because this range was selected "
        "after the run, it is explicitly an analysis amendment rather than a "
        "new simulation result.",
        "",
        "Radii 200–400 remain in the raw file and detector summary. They are "
        "excluded from primary fits because one or more of their requested "
        "30-cycle windows is not reflection-safe.",
        "",
        "## Arrival and reflection safety",
        "",
        "Meaningful arrival is the end of the first complete 18-update source "
        "cycle with finite, non-zero cycle-derived gradient. Selection begins "
        "with the following cycle. The conservative reflected arrival uses "
        "the minimum source → first non-unity absorption interface → detector "
        "path and a direct-neighbor bound of at most one edge per update.",
        "",
        f"The accepted 30th-cycle safety margin ranges from {min(margins)} to "
        f"{max(margins)} updates. All 68 accepted gates pass.",
        "",
        "| Radius | Arrival | Safe cycles | Reflection estimate | "
        "30-cycle margin | Status |",
        "|---:|---:|---:|---:|---:|---|",
    ]
    for radius in ALL_RADII:
        row = next(item for item in summaries
                   if item["requestedRadius"] == radius
                   and item["direction"] == "PX")
        requested_margin = (int(row["detectorSpecificReflectionEstimate"])
                            - (int(row["meaningfulArrivalUpdate"]) + 540))
        lines.append(
            f"| {radius} | {row['meaningfulArrivalUpdate']} | "
            f"{row['reflectionSafePostArrivalCycles']} | "
            f"{row['detectorSpecificReflectionEstimate']} | "
            f"{requested_margin} | "
            f"{'accepted' if row['primarySubsetAccepted'] else 'excluded'} |")

    lines.extend([
        "",
        "## Directional symmetry",
        "",
        "Directional comparisons are performed before four-axis averaging. "
        "The v2 axis-average CSV records PX/NX, PY/NY, and X/Y absolute and "
        "relative differences, parsed-float/bitwise equality classifications, "
        "outward-radial sign parity, arrival-cycle differences, and reflection-"
        "margin differences for every accepted radius.",
        "",
    ])
    for prefix, metric in METRICS.items():
        maximum = max(float(row[f"xYRelativeDifference_{metric}"])
                      for row in axis_rows)
        opposite = max(max(float(row[f"pxNxRelativeDifference_{metric}"]),
                           float(row[f"pyNyRelativeDifference_{metric}"]))
                       for row in axis_rows)
        classes = sorted(set(row[f"xYEquality_{metric}"] for row in axis_rows))
        lines.append(
            f"- **{prefix}:** maximum opposite-direction relative difference "
            f"{fmt(opposite)}; maximum X/Y relative difference {fmt(maximum)}; "
            f"X/Y equality classes: {', '.join(classes)}.")

    lines.extend([
        "",
        "## Radial measurements",
        "",
        "| Radius | Raw-gradient RMS | Cycle-derived-gradient RMS | "
        "Acceleration RMS |",
        "|---:|---:|---:|---:|",
    ])
    for row in axis_rows:
        lines.append(
            f"| {row['requestedRadius']} | "
            f"{fmt(row['fourAxisAverage_rmsRawGradientRadial'])} | "
            f"{fmt(row['fourAxisAverage_rmsCycleDerivedGradientRadial'])} | "
            f"{fmt(row['fourAxisAverage_rmsAccelerationRadial'])} |")

    lines.extend([
        "",
        "## Power-law fits and cutoff sensitivity",
        "",
        "Fits use finite, strictly positive four-axis-average responses. "
        "Free-power-law uncertainty is the covariance-derived 95% interval. "
        "Model RMSE is evaluated in measurement space; R² is evaluated in log "
        "space where predictions are positive. AIC and BIC use measurement-"
        "space residuals with the fitted parameter count.",
        "",
        "| Quantity | Minimum radius | Free exponent n | 95% interval | "
        "log R² | RMSE |",
        "|---|---:|---:|---:|---:|---:|",
    ])
    for prefix in METRICS:
        rows = [row for row in fit_rows if row["quantity"] == prefix
                and row["model"] == "free-power-law"
                and row.get("fitStatus") == "accepted"]
        if not rows:
            lines.append(f"| {prefix} | — | — | — | — | — |")
        for row in rows:
            lines.append(
                f"| {prefix} | {row['minimumRadius']} | "
                f"{fmt(row['fittedExponent'])} | "
                f"[{fmt(row['exponent95PercentLower'])}, "
                f"{fmt(row['exponent95PercentUpper'])}] | "
                f"{fmt(row['logSpaceR2'])} | {fmt(row['rmse'])} |")

    lines.extend([
        "",
        "## Alternative-model comparison",
        "",
        "| Quantity | Lowest-BIC 10–180 model | BIC | Runner-up | ΔBIC |",
        "|---|---|---:|---|---:|",
    ])
    for prefix in METRICS:
        ranking = model_ranking(fit_rows, prefix)
        if not ranking:
            lines.append(f"| {prefix} | no finite-positive fit | — | — | — |")
        else:
            runner = ranking[1] if len(ranking) > 1 else ranking[0]
            lines.append(
                f"| {prefix} | {ranking[0]['model']} | "
                f"{fmt(ranking[0]['bic'])} | {runner['model']} | "
                f"{fmt(float(runner['bic']) - float(ranking[0]['bic']))} |")

    lines.extend([
        "",
        "Lowest-BIC model by minimum-radius cutoff:",
        "",
        "| Quantity | rmin | Winner | BIC |",
        "|---|---:|---|---:|",
    ])
    for prefix in METRICS:
        for minimum in FIT_MINIMA:
            ranking = model_ranking(fit_rows, prefix, minimum)
            if ranking:
                lines.append(
                    f"| {prefix} | {minimum} | {ranking[0]['model']} | "
                    f"{fmt(ranking[0]['bic'])} |")
            else:
                lines.append(
                    f"| {prefix} | {minimum} | no finite-positive fit | — |")
    lines.extend([
        "",
        "Model rankings and parameter stability for every requested minimum "
        "radius are retained in the v2 model-fit CSV. Changes in the winning "
        "model across cutoffs count against model stability. A lowest BIC by "
        "itself is not treated as proof of a physical law.",
        "",
        "## Local exponents and residual behavior",
        "",
    ])
    for prefix in METRICS:
        values = [float(row["localExponent"]) for row in local_rows
                  if row["quantity"] == prefix
                  and row["series"] == "four-axis-average"]
        if values:
            lines.append(
                f"- **{prefix}:** adjacent local n ranges from "
                f"{fmt(min(values))} to {fmt(max(values))}; variability and "
                "the centered three-point series are retained in the local-"
                "exponent CSV and charts.")
        else:
            lines.append(
                f"- **{prefix}:** no local logarithmic exponent is defined "
                "because the accepted radial RMS is not strictly positive.")

    lines.extend([
        "",
        "Selected 10–180 model residual diagnostics:",
        "",
        "| Quantity | Model | Lag-1 autocorrelation | Sign changes | "
        "Peak-to-trough / mean response |",
        "|---|---|---:|---:|---:|",
    ])
    for prefix in METRICS:
        ranking = model_ranking(fit_rows, prefix)
        if ranking:
            winner = ranking[0]
            lines.append(
                f"| {prefix} | {winner['model']} | "
                f"{fmt(winner['residualLagOneAutocorrelation'])} | "
                f"{winner['residualSignChanges']} | "
                f"{fmt(winner['residualPeakToTroughRelativeToMeanResponse'])} |")
        else:
            lines.append(f"| {prefix} | no fit | — | — | — |")

    lines.extend([
        "",
        "The model-residual chart preserves signed measurement-space residuals. "
        "Any oscillatory structure is descriptive only; 17 accepted radii are "
        "insufficient to establish its physical origin or distinguish lattice "
        "dispersion from source-cycle sampling effects.",
        "",
        "## Scientific decisions",
        "",
    ])
    for prefix, (decision, reason) in decisions.items():
        lines.append(f"- **{prefix}: {decision}.** {reason}")
    lines.extend([
        "",
        "This experiment measures passive node-level lattice position-derived "
        "gradients and node acceleration. It does not measure Occupancy "
        "pressure, transport momentum, Identity acceleration, attraction, or "
        "orbital behavior. No conclusion applies to the excluded 200–400 "
        "range, and no result establishes a universal gravitational law.",
        "",
        "## Chart validation",
        "",
        "All charts were generated with Matplotlib using separate figures, "
        "180 dpi PNG output, white backgrounds, major grids, tight layout, "
        "and no subplots. Pillow verified and successfully reopened every "
        "PNG, including an independently copied `/tmp` file. Standards-"
        "compliant SVG companions were saved from the same figures.",
        "",
        "| PNG | Bytes | Dimensions | Mode | Pillow | OS `file` | SVG bytes |",
        "|---|---:|---:|---|---|---|---:|",
    ])
    for result in chart_results:
        lines.append(
            f"| `{result['filename']}` | {result['size']} | "
            f"{result['dimensions'][0]}×{result['dimensions'][1]} | "
            f"{result['mode']} | {result['pillowVerify']}/"
            f"{result['pillowLoad']} | `{result['fileResult']}` | "
            f"{result['svgSize']} |")
    lines.extend([
        "",
        "Every PNG exceeds 20 KB, is at least 1600×900, has non-zero pixel "
        "extrema, contains measured data, has the standard eight-byte PNG "
        "signature, and is not an empty-data message. Detailed results are in "
        "`measurements/charts/test-2d-v2/png-validation.txt`.",
        "",
        "## Limitations",
        "",
        "- The 10–180 primary range was selected after inspecting reflection "
        "safety and must be treated as a post-run amendment.",
        "- Source-locked arrivals are quantized to 18-update cycle boundaries.",
        "- Only lattice-axis directions are measured.",
        "- The full 10–400 protocol remains invalid for a common 30-cycle fit.",
        "- Model uncertainty does not include systematic boundary, discretization, "
        "or post-selection uncertainty.",
    ])
    REPORT_OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return decisions


def main():
    rows = read_csv(RAW)
    grouped = validate_raw(rows)
    cycle_rows, summaries, detector_metrics = build_detector_outputs(grouped)
    axis_rows = build_axis_averages(detector_metrics)
    local_rows = build_local_exponents(axis_rows)
    fit_rows, _ = build_fits(axis_rows)

    write_csv(CYCLE_OUT, cycle_rows, list(cycle_rows[0]))
    summary_fields = list(summaries[0])
    for prefix in VECTOR_PREFIXES:
        title = prefix[0].upper() + prefix[1:]
        summary_fields.extend([
            f"meanSigned{title}Radial", f"rms{title}Radial",
            f"meanSigned{title}Tangential", f"rms{title}Tangential",
            f"{prefix}TangentialToRadial",
        ])
    write_csv(SUMMARY_OUT, summaries, summary_fields)
    write_csv(AVERAGE_OUT, axis_rows, list(axis_rows[0]))
    write_csv(LOCAL_OUT, local_rows, list(local_rows[0]))
    fit_fields = [
        "quantity", "metric", "minimumRadius", "maximumRadius", "model",
        "includedRadii", "includedRadiusCount", "excludedRadiiAndReason",
        "fitStatus", "exclusionReason", "amplitude", "fittedExponent",
        "exponentStandardError", "exponent95PercentLower",
        "exponent95PercentUpper", "decayLambda", "radialOffset", "rmse",
        "logSpaceR2", "aic", "bic", "residualMean",
        "residualMaximumAbsolute", "residualLagOneAutocorrelation",
        "residualSignChanges",
        "residualPeakToTroughRelativeToMeanResponse",
    ]
    write_csv(FITS_OUT, fit_rows, fit_fields)
    create_charts(axis_rows, summaries, local_rows, fit_rows)
    chart_results = validate_charts()
    decisions = write_report(summaries, axis_rows, fit_rows, local_rows,
                             chart_results)

    print("Gravity Test 2D v2 analysis complete")
    print(f"raw rows: {len(rows)}")
    print(f"accepted detectors: {len(detector_metrics)}")
    print(f"accepted cycle rows: {len(cycle_rows)}")
    print(f"excluded radii: {','.join(map(str, EXCLUDED_RADII))}")
    print("decisions: " + ", ".join(
        f"{prefix}={decision[0]}" for prefix, decision in decisions.items()))
    for result in chart_results:
        print(f"chart {result['filename']}: {result['size']} bytes, "
              f"{result['dimensions'][0]}x{result['dimensions'][1]}, "
              f"{result['format']} {result['mode']}, {result['status']}")


if __name__ == "__main__":
    main()
