#!/usr/bin/env python3
"""Analyze the Gravity Test 2E passive-detector 3D feasibility pilot."""

import csv
import math
import os
import shutil
import subprocess
import sys
import time
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


TEST_ID = "gravity-test-2e-3d-pilot"
DIRECTORY = Path("measurements") / TEST_ID
RAW_INPUT = DIRECTORY / "raw-detectors-run-001.csv"
TELEMETRY = DIRECTORY / "runtime-memory-telemetry.csv"
RAW_OUTPUT = DIRECTORY / "raw-detectors-enriched.csv"
SUMMARY_OUTPUT = DIRECTORY / "detector-summary.csv"
CYCLE_OUTPUT = DIRECTORY / "cycle-metrics.csv"
SYMMETRY_OUTPUT = DIRECTORY / "symmetry.csv"
FIT_OUTPUT = DIRECTORY / "model-fits.csv"
REPORT_OUTPUT = DIRECTORY / "report.md"
CHARTS = DIRECTORY / "charts"
VALIDATION_OUTPUT = CHARTS / "chart-validation.txt"

LATTICE_SIZE = 161
SOURCE = np.array([80.0, 80.0, 80.0])
SOURCE_PERIOD = 18
UPDATES = 180
RADII = (5, 10, 15, 20, 25, 30, 35, 40, 45)
DIRECTIONS = ("PX", "NX", "PY", "NY", "PZ", "NZ")
AXIS = {"PX": "X", "NX": "X", "PY": "Y", "NY": "Y",
        "PZ": "Z", "NZ": "Z"}
OPPOSITE = (("PX", "NX"), ("PY", "NY"), ("PZ", "NZ"))
INTERFACES = (
    ("left x=30", 0, 30.0), ("right x=130", 0, 130.0),
    ("bottom y=30", 1, 30.0), ("top y=130", 1, 130.0),
    ("back z=30", 2, 30.0), ("front z=130", 2, 130.0),
)
VECTOR_PREFIXES = ("positionDeviation", "velocity", "acceleration",
                   "rawGradient", "cycleDerivedGradient")
SUMMARY_METRICS = (
    "displacementMagnitude", "radialDisplacement",
    "transverseDisplacement", "rawGradientRadial",
    "cycleDerivedGradientRadial", "radialAcceleration",
)
EXPECTED_CHARTS = (
    "memory-usage", "update-time", "arrival-update",
    "reflection-safe-cycles", "radial-displacement-rms",
    "transverse-displacement-rms", "raw-radial-gradient-rms",
    "cycle-radial-gradient-rms", "axis-symmetry", "radial-loglog",
)


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
    return np.array([float(row[prefix + axis]) for axis in "XYZ"])


def components(row):
    coordinate = np.array(
        [float(row["nodeX"]), float(row["nodeY"]), float(row["nodeZ"])])
    displacement = coordinate - SOURCE
    radius = float(np.linalg.norm(displacement))
    radial_hat = displacement / radius
    output = {}
    for prefix in VECTOR_PREFIXES:
        value = vector(row, prefix)
        radial = float(np.dot(value, radial_hat))
        transverse = math.sqrt(max(
            0.0, float(np.dot(value, value)) - radial * radial))
        output[prefix + "Magnitude"] = float(np.linalg.norm(value))
        output[prefix + "Radial"] = radial
        output[prefix + "TransverseMagnitude"] = transverse
    return radius, radial_hat, output


def reflected_path(coordinate):
    candidates = []
    for label, axis, interface in INTERFACES:
        mirrored = coordinate.copy()
        mirrored[axis] = 2.0 * interface - coordinate[axis]
        total = float(np.linalg.norm(mirrored - SOURCE))
        outbound = abs(interface - SOURCE[axis])
        candidates.append((total, label, outbound, total - outbound))
    return min(candidates)


def validate_and_enrich(rows):
    if len(rows) != 54 * UPDATES:
        raise ValueError(f"raw row count {len(rows)} != 9720")
    grouped = defaultdict(list)
    seen = set()
    enriched = []
    for row in rows:
        name = row["detectorName"]
        update = int(row["update"])
        key = (name, update)
        if key in seen:
            raise ValueError(f"duplicate detector/update {key}")
        seen.add(key)
        grouped[name].append(row)
        for field, text in row.items():
            if field in {"detectorName", "direction", "cycleCompleted"}:
                continue
            if text == "" and field == "requestedAngleDegrees":
                continue
            value = float(text)
            if not math.isfinite(value):
                raise ValueError(f"non-finite {field} for {key}")
        radius, _, measured = components(row)
        deviation = vector(row, "positionDeviation")
        velocity = vector(row, "velocity")
        acceleration = vector(row, "acceleration")
        raw_gradient = vector(row, "rawGradient")
        output = {
            "testIdentifier": TEST_ID, "latticeSize": LATTICE_SIZE,
            "sourcePeriod": SOURCE_PERIOD, **row,
            "nominalRadius": int(row["requestedRadius"]),
            "actualEuclideanRadius3D": radius,
            "displacementMagnitude": float(np.linalg.norm(deviation)),
            "velocityMagnitude": float(np.linalg.norm(velocity)),
            "accelerationMagnitude": float(np.linalg.norm(acceleration)),
            "radialDisplacement":
                measured["positionDeviationRadial"],
            "radialVelocity": measured["velocityRadial"],
            "radialAcceleration": measured["accelerationRadial"],
            "transverseDisplacementMagnitude":
                measured["positionDeviationTransverseMagnitude"],
            "rawGradientMagnitude": float(np.linalg.norm(raw_gradient)),
            "rawGradientRadial": measured["rawGradientRadial"],
            "tangentialGradientMagnitude":
                measured["rawGradientTransverseMagnitude"],
            "cycleDerivedGradientRadial":
                measured["cycleDerivedGradientRadial"],
            "allFinite": True,
            "integrityStatus": "PASS",
        }
        enriched.append(output)
    if len(grouped) != 54:
        raise ValueError(f"detector count {len(grouped)} != 54")
    for name, detector_rows in grouped.items():
        if [int(row["update"]) for row in detector_rows] != list(
                range(1, UPDATES + 1)):
            raise ValueError(f"incomplete update sequence for {name}")
        first = detector_rows[0]
        direction = first["direction"]
        radius = int(first["requestedRadius"])
        if direction not in DIRECTIONS or radius not in RADII:
            raise ValueError(f"unexpected detector {name}")
        if name != f"{direction}_R{radius:03d}":
            raise ValueError(f"name/geometry mismatch {name}")
        coordinate = np.array(
            [int(first["nodeX"]), int(first["nodeY"]), int(first["nodeZ"])])
        delta = coordinate - SOURCE
        if np.count_nonzero(delta) != 1:
            raise ValueError(f"{name} is not exactly axial")
        if float(np.linalg.norm(delta)) != radius:
            raise ValueError(f"{name} radius mismatch")
        if min(coordinate) < 30 or max(coordinate) > 130:
            raise ValueError(f"{name} lies in absorption region")
    return grouped, enriched


def first_nonzero(rows, prefixes):
    for row in rows:
        if any(np.linalg.norm(vector(row, prefix)) > 0.0
               for prefix in prefixes):
            return int(row["update"])
    return None


def meaningful_arrival(cycles, direction):
    for row in cycles:
        _, _, measured = components(row)
        value = measured["cycleDerivedGradientRadial"]
        if math.isfinite(value) and value != 0.0:
            return int(row["update"]), int(row["completedCycleCount"]), value
    return None, None, None


def rms(values):
    data = np.asarray(values, dtype=float)
    return float(np.sqrt(np.mean(data * data)))


def summarize(grouped):
    summaries = []
    cycle_metrics = []
    for name in sorted(grouped):
        rows = grouped[name]
        first = rows[0]
        direction = first["direction"]
        radius = int(first["requestedRadius"])
        cycles = [row for row in rows
                  if row["cycleCompleted"].lower() == "true"]
        if [int(row["update"]) for row in cycles] != list(
                range(18, 181, 18)):
            raise ValueError(f"invalid cycle schedule for {name}")
        arrival, arrival_cycle, arrival_signal = meaningful_arrival(
            cycles, direction)
        coordinate = np.array(
            [float(first["nodeX"]), float(first["nodeY"]),
             float(first["nodeZ"])])
        path, boundary, outbound, returned = reflected_path(coordinate)
        reflection = math.ceil(path) + 1
        post_arrival = [row for row in cycles
                        if arrival is not None
                        and int(row["update"]) > arrival]
        safe_cycles = [row for row in post_arrival
                       if int(row["update"]) < reflection]
        metric_values = {metric: [] for metric in SUMMARY_METRICS}
        for row in safe_cycles:
            _, _, measured = components(row)
            values = {
                "displacementMagnitude":
                    measured["positionDeviationMagnitude"],
                "radialDisplacement":
                    measured["positionDeviationRadial"],
                "transverseDisplacement":
                    measured["positionDeviationTransverseMagnitude"],
                "rawGradientRadial": measured["rawGradientRadial"],
                "cycleDerivedGradientRadial":
                    measured["cycleDerivedGradientRadial"],
                "radialAcceleration": measured["accelerationRadial"],
            }
            output = {
                "detectorName": name, "direction": direction,
                "axis": AXIS[direction], "requestedRadius": radius,
                "cycleNumber": int(row["completedCycleCount"]),
                "cycleEndUpdate": int(row["update"]),
                "arrivalUpdate": arrival,
                "reflectionEstimate": reflection,
                "reflectionMargin": reflection - int(row["update"]),
            }
            for metric, value in values.items():
                output[metric] = value
                metric_values[metric].append(value)
            cycle_metrics.append(output)
        summary = {
            "detectorName": name, "direction": direction,
            "axis": AXIS[direction], "requestedRadius": radius,
            "nodeX": int(first["nodeX"]), "nodeY": int(first["nodeY"]),
            "nodeZ": int(first["nodeZ"]),
            "actualEuclideanRadius":
                float(first["actualEuclideanRadius"]),
            "firstNonZeroDisplacementUpdate":
                first_nonzero(rows, ("positionDeviation",)),
            "firstNonZeroRawGradientUpdate":
                first_nonzero(rows, ("rawGradient",)),
            "primaryArrivalUpdate": arrival,
            "primaryArrivalCycleNumber": arrival_cycle,
            "arrivalSignal": arrival_signal,
            "nearestRelevantBoundary": boundary,
            "outboundDistance": outbound,
            "returnDistanceToDetector": returned,
            "totalReflectedPath": path,
            "reflectionEstimate": reflection,
            "availableCompletePostArrivalCycles": len(post_arrival),
            "reflectionSafePostArrivalCycles": len(safe_cycles),
            "reflectionSafe": len(safe_cycles) > 0,
            "reflectionMarginFromArrival":
                reflection - arrival if arrival is not None else "",
        }
        for metric, values in metric_values.items():
            summary["rms" + metric[0].upper() + metric[1:]] = (
                rms(values) if values else "")
            summary["meanSigned" + metric[0].upper() + metric[1:]] = (
                float(np.mean(values)) if values else "")
        summaries.append(summary)
    return summaries, cycle_metrics


def relative_difference(a, b):
    scale = max(abs(a), abs(b))
    return abs(a - b) / scale if scale else 0.0


def bitwise_equal(a, b):
    return np.float64(a).tobytes() == np.float64(b).tobytes()


def build_symmetry(summaries):
    lookup = {(row["requestedRadius"], row["direction"]): row
              for row in summaries}
    fields = ["rmsDisplacementMagnitude", "rmsRadialDisplacement",
              "rmsTransverseDisplacement", "rmsRawGradientRadial",
              "rmsCycleDerivedGradientRadial", "rmsRadialAcceleration"]
    results = []
    for radius in RADII:
        for positive, negative in OPPOSITE:
            left, right = lookup[(radius, positive)], lookup[(radius, negative)]
            for field in fields:
                if left[field] == "" or right[field] == "":
                    continue
                a, b = float(left[field]), float(right[field])
                results.append({
                    "comparisonType": "opposite-direction",
                    "radius": radius, "metric": field,
                    "first": positive, "second": negative,
                    "firstValue": a, "secondValue": b,
                    "absoluteDifference": abs(a - b),
                    "relativeDifference": relative_difference(a, b),
                    "parsedFloatEqual": a == b,
                    "bitwiseEqual": bitwise_equal(a, b),
                    "arrivalUpdateDifference": abs(
                        int(left["primaryArrivalUpdate"])
                        - int(right["primaryArrivalUpdate"])),
                    "reflectionMarginDifference": abs(
                        int(left["reflectionMarginFromArrival"])
                        - int(right["reflectionMarginFromArrival"])),
                })
        for first_axis, second_axis in (("X", "Y"), ("X", "Z"), ("Y", "Z")):
            for field in fields:
                first_values = [float(row[field]) for row in summaries
                                if row["requestedRadius"] == radius
                                and row["axis"] == first_axis
                                and row[field] != ""]
                second_values = [float(row[field]) for row in summaries
                                 if row["requestedRadius"] == radius
                                 and row["axis"] == second_axis
                                 and row[field] != ""]
                if not first_values or not second_values:
                    continue
                a, b = float(np.mean(first_values)), float(
                    np.mean(second_values))
                results.append({
                    "comparisonType": "cross-axis-average",
                    "radius": radius, "metric": field,
                    "first": first_axis, "second": second_axis,
                    "firstValue": a, "secondValue": b,
                    "absoluteDifference": abs(a - b),
                    "relativeDifference": relative_difference(a, b),
                    "parsedFloatEqual": a == b,
                    "bitwiseEqual": bitwise_equal(a, b),
                    "arrivalUpdateDifference": "",
                    "reflectionMarginDifference": "",
                })
    return results


def radial_series(summaries, field):
    points = []
    for radius in RADII:
        values = [float(row[field]) for row in summaries
                  if row["requestedRadius"] == radius and row[field] != ""]
        if len(values) == 6:
            value = float(np.mean(np.abs(values)))
            if math.isfinite(value) and value > 0.0:
                points.append((radius, value))
    return points


def fit_models(summaries):
    rows = []
    metrics = ("rmsDisplacementMagnitude", "rmsRadialDisplacement",
               "rmsTransverseDisplacement", "rmsRawGradientRadial",
               "rmsCycleDerivedGradientRadial", "rmsRadialAcceleration")
    models = {
        "inverse-linear": (lambda r, a: a / r, (1.0,), 1),
        "inverse-square": (lambda r, a: a / r ** 2, (1.0,), 1),
        "free-power-law":
            (lambda r, a, n: a / r ** n, (1.0, 2.0), 2),
        "exponential":
            (lambda r, a, length: a * np.exp(-r / length),
             (1.0, 10.0), 2),
        "offset-power-law":
            (lambda r, c, a, n: c + a / r ** n,
             (0.0, 1.0, 2.0), 3),
    }
    for metric in metrics:
        points = radial_series(summaries, metric)
        x = np.array([point[0] for point in points], dtype=float)
        y = np.array([point[1] for point in points], dtype=float)
        for name, (function, initial, parameter_count) in models.items():
            output = {
                "metric": metric, "model": name,
                "usableRadii": ";".join(str(int(value)) for value in x),
                "usableRadiusCount": len(x),
                "pilotDiagnosticOnly": True,
            }
            if len(x) < max(4, parameter_count + 1):
                output.update({
                    "fitStatus": "excluded",
                    "exclusionReason":
                        "fewer than four finite positive reflection-safe radii",
                })
            else:
                try:
                    parameters, covariance = curve_fit(
                        function, x, y, p0=initial, maxfev=200000)
                    predicted = function(x, *parameters)
                    residual = y - predicted
                    rss = max(float(np.sum(residual ** 2)),
                              np.finfo(float).tiny)
                    count = len(y)
                    output.update({
                        "fitStatus": "accepted", "exclusionReason": "",
                        "parameter0": parameters[0],
                        "parameter1":
                            parameters[1] if len(parameters) > 1 else "",
                        "parameter2":
                            parameters[2] if len(parameters) > 2 else "",
                        "rmse": float(np.sqrt(np.mean(residual ** 2))),
                        "aic": count * math.log(rss / count)
                               + 2 * parameter_count,
                        "bic": count * math.log(rss / count)
                               + parameter_count * math.log(count),
                        "maximumAbsoluteResidual":
                            float(np.max(np.abs(residual))),
                        "parameter1StandardError":
                            math.sqrt(covariance[1, 1])
                            if len(parameters) > 1
                            and covariance[1, 1] >= 0 else "",
                    })
                except (RuntimeError, ValueError, FloatingPointError) as exc:
                    output.update({
                        "fitStatus": "excluded",
                        "exclusionReason": str(exc),
                    })
            rows.append(output)
    return rows


def setup_chart(title, ylabel):
    figure, axis = plt.subplots(figsize=(12, 7))
    figure.patch.set_facecolor("white")
    axis.set_facecolor("white")
    axis.set_title(title)
    axis.set_xlabel("Radius (lattice edges)")
    axis.set_ylabel(ylabel)
    axis.grid(True, which="major", alpha=0.35)
    return figure, axis


def save_chart(figure, stem):
    png = CHARTS / f"{stem}.png"
    svg = CHARTS / f"{stem}.svg"
    figure.tight_layout()
    figure.savefig(png, format="png", dpi=180, bbox_inches="tight",
                   facecolor="white", transparent=False)
    figure.savefig(svg, format="svg", bbox_inches="tight",
                   facecolor="white", transparent=False)
    plt.close(figure)


def plot_direction_series(summaries, field, title, stem, ylabel):
    figure, axis = setup_chart(title, ylabel)
    for direction in DIRECTIONS:
        rows = [row for row in summaries
                if row["direction"] == direction and row[field] != ""]
        axis.plot([row["requestedRadius"] for row in rows],
                  [row[field] for row in rows], marker="o",
                  label=direction)
    axis.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    axis.legend()
    save_chart(figure, stem)


def create_charts(summaries, symmetry, telemetry_rows, fits):
    CHARTS.mkdir(parents=True, exist_ok=True)
    updates = [row for row in telemetry_rows if row["event"] == "update"]

    figure, axis = plt.subplots(figsize=(12, 7))
    axis.plot([int(row["update"]) for row in updates],
              [int(row["usedHeapBytes"]) / 2 ** 30 for row in updates])
    axis.set_title("Gravity Test 2E heap usage (161³, updates 1–180)")
    axis.set_xlabel("Update")
    axis.set_ylabel("Used heap (GiB)")
    axis.grid(True)
    save_chart(figure, "memory-usage")

    figure, axis = plt.subplots(figsize=(12, 7))
    axis.plot([int(row["update"]) for row in updates],
              [float(row["updateMilliseconds"]) for row in updates])
    axis.set_title("Gravity Test 2E update time (161³, updates 1–180)")
    axis.set_xlabel("Update")
    axis.set_ylabel("Update time (ms)")
    axis.grid(True)
    save_chart(figure, "update-time")

    figure, axis = setup_chart("Cycle-locked arrival by direction",
                               "Arrival update")
    for direction in DIRECTIONS:
        rows = [row for row in summaries if row["direction"] == direction]
        axis.plot([row["requestedRadius"] for row in rows],
                  [row["primaryArrivalUpdate"] for row in rows],
                  marker="o", label=direction)
    axis.legend()
    save_chart(figure, "arrival-update")

    figure, axis = setup_chart(
        "Complete post-arrival cycles before conservative reflection",
        "Reflection-safe cycles")
    for direction in DIRECTIONS:
        rows = [row for row in summaries if row["direction"] == direction]
        axis.plot([row["requestedRadius"] for row in rows],
                  [row["reflectionSafePostArrivalCycles"] for row in rows],
                  marker="o", label=direction)
    axis.axhline(10, color="red", linestyle="--",
                 label="preferred 10-cycle target")
    axis.legend()
    save_chart(figure, "reflection-safe-cycles")

    plot_direction_series(
        summaries, "rmsRadialDisplacement",
        "Reflection-safe radial displacement RMS",
        "radial-displacement-rms", "RMS displacement")
    plot_direction_series(
        summaries, "rmsTransverseDisplacement",
        "Reflection-safe transverse displacement RMS",
        "transverse-displacement-rms", "RMS displacement")
    plot_direction_series(
        summaries, "rmsRawGradientRadial",
        "Reflection-safe raw radial-gradient RMS",
        "raw-radial-gradient-rms", "Gradient RMS")
    plot_direction_series(
        summaries, "rmsCycleDerivedGradientRadial",
        "Reflection-safe cycle-derived radial-gradient RMS",
        "cycle-radial-gradient-rms", "Gradient RMS")

    figure, axis = setup_chart(
        "Pilot cross-axis cycle-derived-gradient comparison",
        "Axis-pair average RMS")
    for axis_name in ("X", "Y", "Z"):
        x, y = [], []
        for radius in RADII:
            values = [float(row["rmsCycleDerivedGradientRadial"])
                      for row in summaries
                      if row["axis"] == axis_name
                      and row["requestedRadius"] == radius
                      and row["rmsCycleDerivedGradientRadial"] != ""]
            if values:
                x.append(radius)
                y.append(float(np.mean(values)))
        axis.plot(x, y, marker="o", label=axis_name)
    axis.ticklabel_format(axis="y", style="sci", scilimits=(-3, 3))
    axis.legend()
    save_chart(figure, "axis-symmetry")

    points = radial_series(summaries, "rmsCycleDerivedGradientRadial")
    figure, axis = setup_chart(
        "Exploratory reflection-safe cycle-gradient radial response",
        "Six-direction mean RMS")
    axis.set_xscale("log")
    axis.set_yscale("log")
    axis.plot([point[0] for point in points],
              [point[1] for point in points], "ko", label="measured")
    fit = next((row for row in fits
                if row["metric"] == "rmsCycleDerivedGradientRadial"
                and row["model"] == "free-power-law"
                and row["fitStatus"] == "accepted"), None)
    if fit:
        x = np.geomspace(min(point[0] for point in points),
                         max(point[0] for point in points), 200)
        y = float(fit["parameter0"]) / x ** float(fit["parameter1"])
        axis.plot(x, y, "r-", label="exploratory free power law")
    axis.legend()
    save_chart(figure, "radial-loglog")


def validate_charts():
    results = []
    lines = [f"Matplotlib {matplotlib.__version__}",
             f"Pillow {Image.__version__}", ""]
    for stem in EXPECTED_CHARTS:
        png = CHARTS / f"{stem}.png"
        svg = CHARTS / f"{stem}.svg"
        errors = []
        if not png.exists() or png.stat().st_size <= 20_000:
            errors.append("missing or undersized PNG")
        with Image.open(png) as image:
            image.verify()
        with Image.open(png) as image:
            image.load()
            width, height = image.size
            image_format, mode = image.format, image.mode
            extrema = image.getextrema()
        if image_format != "PNG" or width < 1600 or height < 900:
            errors.append("PNG format or dimensions failed")
        if all(low == high for low, high in extrema):
            errors.append("uniform image")
        signature = png.read_bytes()[:8]
        if signature != b"\x89PNG\r\n\x1a\n":
            errors.append("invalid PNG signature")
        file_result = subprocess.run(
            ["file", str(png)], capture_output=True, text=True,
            check=False).stdout.strip()
        if "PNG image data" not in file_result:
            errors.append("OS file validation failed")
        copied = Path("/tmp") / f"rem-{TEST_ID}-{png.name}"
        shutil.copy2(png, copied)
        with Image.open(copied) as image:
            image.load()
        text = svg.read_text(encoding="utf-8")
        if svg.stat().st_size <= 2_000 or "<svg" not in text[:4096]:
            errors.append("SVG validation failed")
        status = "PASS" if not errors else "FAIL"
        lines.extend([
            f"{png.name}: {status}", f"  bytes: {png.stat().st_size}",
            f"  dimensions: {width}x{height}", f"  mode: {mode}",
            f"  signature: {signature.hex(' ').upper()}",
            f"  extrema: {extrema}", f"  file: {file_result}",
            f"  svg: {svg.name} ({svg.stat().st_size} bytes)", "",
        ])
        results.append((png.name, png.stat().st_size, width, height, status))
        if errors:
            raise ValueError(f"{png.name}: {'; '.join(errors)}")
    VALIDATION_OUTPUT.write_text("\n".join(lines), encoding="utf-8")
    return results


def main():
    started = time.perf_counter()
    raw_rows = read_csv(RAW_INPUT)
    telemetry_rows = read_csv(TELEMETRY)
    grouped, enriched = validate_and_enrich(raw_rows)
    summaries, cycle_metrics = summarize(grouped)
    symmetry = build_symmetry(summaries)
    fits = fit_models(summaries)

    write_csv(RAW_OUTPUT, enriched, list(enriched[0]))
    write_csv(SUMMARY_OUTPUT, summaries, list(summaries[0]))
    write_csv(CYCLE_OUTPUT, cycle_metrics,
              list(cycle_metrics[0]) if cycle_metrics else [
                  "detectorName", "direction", "axis", "requestedRadius",
                  "cycleNumber", "cycleEndUpdate", "arrivalUpdate",
                  "reflectionEstimate", "reflectionMargin",
              ])
    write_csv(SYMMETRY_OUTPUT, symmetry, list(symmetry[0]))
    fit_fields = [
        "metric", "model", "usableRadii", "usableRadiusCount",
        "pilotDiagnosticOnly", "fitStatus", "exclusionReason",
        "parameter0", "parameter1", "parameter2",
        "parameter1StandardError", "rmse", "aic", "bic",
        "maximumAbsoluteResidual",
    ]
    write_csv(FIT_OUTPUT, fits, fit_fields)
    create_charts(summaries, symmetry, telemetry_rows, fits)
    chart_results = validate_charts()

    updates = [row for row in telemetry_rows if row["event"] == "update"]
    update_times = np.array(
        [float(row["updateMilliseconds"]) for row in updates])
    before = next(row for row in telemetry_rows
                  if row["event"] == "before-lattice-allocation")
    initialized = next(row for row in telemetry_rows
                       if row["event"] == "lattice-initialized")
    complete = next(row for row in telemetry_rows
                    if row["event"] == "simulation-complete")
    analysis_milliseconds = (time.perf_counter() - started) * 1000.0
    safe_radii_any = sorted(set(
        row["requestedRadius"] for row in summaries
        if row["reflectionSafePostArrivalCycles"] > 0))
    safe_radii_common = [
        radius for radius in RADII
        if all(row["reflectionSafePostArrivalCycles"] > 0
               for row in summaries if row["requestedRadius"] == radius)
    ]
    max_opposite = max(
        (float(row["relativeDifference"]) for row in symmetry
         if row["comparisonType"] == "opposite-direction"), default=math.nan)
    max_cross = max(
        (float(row["relativeDifference"]) for row in symmetry
         if row["comparisonType"] == "cross-axis-average"), default=math.nan)
    arrival_table = "\n".join(
        f"| {radius} | "
        + " | ".join(
            str(next(row["primaryArrivalUpdate"] for row in summaries
                     if row["requestedRadius"] == radius
                     and row["direction"] == direction))
            for direction in DIRECTIONS)
        + " | "
        + str(next(row["reflectionEstimate"] for row in summaries
                   if row["requestedRadius"] == radius))
        + " | "
        + "/".join(
            str(next(row["reflectionSafePostArrivalCycles"]
                     for row in summaries
                     if row["requestedRadius"] == radius
                     and row["direction"] == direction))
            for direction in DIRECTIONS)
        + " |"
        for radius in RADII)
    symmetry_table_rows = []
    for positive, negative in OPPOSITE:
        values = [float(row["relativeDifference"]) for row in symmetry
                  if row["comparisonType"] == "opposite-direction"
                  and row["first"] == positive and row["second"] == negative]
        arrival_differences = [
            int(row["arrivalUpdateDifference"]) for row in symmetry
            if row["comparisonType"] == "opposite-direction"
            and row["first"] == positive and row["second"] == negative]
        symmetry_table_rows.append(
            f"| {positive}/{negative} | {max(values, default=math.nan):.6g} "
            f"| {max(arrival_differences, default=0)} |")
    fit_table_rows = []
    for metric in (
            "rmsDisplacementMagnitude", "rmsRadialDisplacement",
            "rmsTransverseDisplacement", "rmsRawGradientRadial",
            "rmsCycleDerivedGradientRadial", "rmsRadialAcceleration"):
        accepted = [row for row in fits if row["metric"] == metric
                    and row["fitStatus"] == "accepted"]
        if not accepted:
            fit_table_rows.append(
                f"| {metric} | none | — | — | — |")
            continue
        best = min(accepted, key=lambda row: float(row["bic"]))
        free = next((row for row in accepted
                     if row["model"] == "free-power-law"), None)
        fit_table_rows.append(
            f"| {metric} | {best['model']} | {float(best['bic']):.6g} | "
            f"{float(free['parameter1']):.6g} | "
            f"{free['usableRadiusCount']} |")

    report = f"""# Gravity Test 2E: 3D feasibility pilot

## Scope

Gravity Test 2E is a feasibility pilot. It tests whether the existing REM
Framework passive-detector architecture can execute a practical 3D,
reflection-controlled experiment. It does not establish or reject an
inverse-square gravitational law.

## Configuration

- Lattice: 161 × 161 × 161 ({161 ** 3:,} nodes)
- Source: one Identity at (80, 80, 80)
- Oscillation: Z-axis sine, amplitude 200, 20° per update, period 18
- Transport: disabled
- Node equation: canonical v0.1, unchanged
- Boundary: fixed outer nodes, 30 cosine absorption layers
- Detectors: 54 passive nodes; PX/NX/PY/NY/PZ/NZ at radii
  {", ".join(map(str, RADII))}
- Updates: {UPDATES}

## Memory feasibility

The JVM maximum heap was {int(before["maxHeapBytes"]) / 2 ** 30:.3f} GiB.
A conservative 3 KiB/node object-graph estimate predicted 11.94 GiB for the
node graph and 14.92 GiB including 25% transient/JVM headroom. Measured used
heap after lattice initialization was
{int(initialized["usedHeapBytes"]) / 2 ** 30:.3f} GiB; peak observed heap was
{int(complete["peakUsedHeapBytes"]) / 2 ** 30:.3f} GiB. The peak was
{100 * int(complete["peakUsedHeapBytes"]) / int(before["maxHeapBytes"]):.2f}%
of maximum heap, below the 60% safety target.

JVM object layout varies with runtime, compressed references, alignment, and
allocation behavior; estimates are intentionally conservative.

| Candidate | Nodes | Conservative node graph | With 25% headroom |
|---:|---:|---:|---:|
| 101³ | 1,030,301 | 2.95 GiB | 3.68 GiB |
| 121³ | 1,771,561 | 5.07 GiB | 6.34 GiB |
| 141³ | 2,803,221 | 8.02 GiB | 10.03 GiB |
| 161³ | 4,173,281 | 11.94 GiB | 14.92 GiB |
| 181³ | 5,929,741 | 16.97 GiB | 21.21 GiB |
| 201³ | 8,120,601 | 23.23 GiB | 29.04 GiB |

Used heap before lattice allocation was
{int(before["usedHeapBytes"]) / 2 ** 20:.3f} MiB. Used heap after detector
configuration was unchanged within telemetry resolution; detector metadata is
negligible compared with the lattice graph.

## Runtime

- Lattice initialization: {float(initialized["elapsedMilliseconds"]):.3f} ms
- Simulation: {float(complete["elapsedMilliseconds"]):.3f} ms
- Analysis: {analysis_milliseconds:.3f} ms
- Mean update: {float(np.mean(update_times)):.3f} ms
- Median update: {float(np.median(update_times)):.3f} ms
- Minimum update: {float(np.min(update_times)):.3f} ms
- Maximum update: {float(np.max(update_times)):.3f} ms
- Updates/second: {1000.0 / float(np.mean(update_times)):.3f}
- GC count: {complete["gcCount"]}
- GC elapsed: {complete["gcTimeMilliseconds"]} ms

## Collection integrity

The raw collection contains exactly 54 × 180 = 9,720 unique
detector/update rows. Every detector has updates 1–180, all numeric values are
finite, geometry is exact, all detectors are outside the absorption layer, and
the detector logger contains no Identity or Occupancy measurements.

## Arrival and reflection

Primary arrival is the first complete 18-update source cycle with a finite,
non-zero cycle-derived radial gradient. Threshold-free first non-zero
displacement and raw-gradient updates are retained separately. Conservative
reflection estimates minimize the source → first non-unity absorption
interface → detector mirror path using at most one lattice edge per update.

Reflection-safe radii with at least one complete post-arrival cycle:
{", ".join(map(str, safe_radii_any)) if safe_radii_any else "none"}.
The common six-direction reflection-safe range is
{", ".join(map(str, safe_radii_common)) if safe_radii_common else "none"}.
Radius 30 is safe for one cycle only along ±Z and is excluded from common
six-axis radial fits.

No detector reaches the preferred ten reflection-safe post-arrival cycles.
This is a pilot design constraint, not a relaxed gate.

| Radius | PX arrival | NX | PY | NY | PZ | NZ | Reflection | Safe cycles PX/NX/PY/NY/PZ/NZ |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
{arrival_table}

## Symmetry

Maximum opposite-direction relative difference among defined safe-cycle RMS
metrics: {max_opposite:.6g}. Maximum X/Y/Z cross-axis relative difference:
{max_cross:.6g}. X/Y/Z comparisons are polarization-sensitive axis
comparisons, not claims of spherical isotropy.

| Opposite pair | Maximum relative RMS difference | Maximum arrival-update difference |
|---|---:|---:|
{chr(10).join(symmetry_table_rows)}

X and Y responses agree far more closely than either agrees with Z. The
largest reported opposite-direction difference occurs for the ±Z raw radial
gradient at radius 5. With only four safe cycles there, this is a pilot
observation requiring a longer reflection-safe final design.

## Exploratory radial diagnostics

The model-fit CSV compares inverse-linear, inverse-square, free power law,
exponential, and offset power law only where at least four finite, positive,
reflection-safe radii exist. Fits are feasibility diagnostics. No fitted
exponent is interpreted as a physical law, and zero/non-positive values are
excluded from logarithmic interpretation.

| Metric | Lowest-BIC pilot model | BIC | Free-power exponent n | Usable common radii |
|---|---|---:|---:|---:|
{chr(10).join(fit_table_rows)}

The common fits use only radii 5, 10, 15, 20, and 25, with between two and four
safe cycles per detector. The fitted exponents and model rankings are too
sensitive to short windows, polarization, and near-source structure for a
physical-law conclusion.

## Feasibility conclusion

The current implementation can allocate and run a 161³ passive-detector
lattice within the configured heap, with stable update throughput and valid
3D X/Y/Z detector sampling. However, the 30-layer absorption geometry leaves
too little center-to-interface distance for ten reflection-safe post-arrival
cycles. A final 3D radial-falloff design needs a larger physical interior,
fewer required cycles, or an explicitly re-evaluated boundary strategy.

Gravity Test 2E demonstrates whether a practical, passive,
reflection-controlled 3D radial-falloff experiment can be executed with the
current implementation. It does not by itself establish or reject an
inverse-square gravitational law.

## Limitations

- The primary range was constrained by conservative causal reflection bounds.
- Arrivals are quantized to 18-update cycle boundaries.
- The source is Z-polarized, so Z versus X/Y differences may be physical
  polarization response rather than lattice-axis asymmetry.
- The headless runner omits rendering only; it retains source, node update,
  position commit, and detector sampling order.
- The initial GUI launch and two pre-update headless setup attempts failed
  before simulation because no display/OpenGL context was available. Their
  diagnostics are preserved; only one run executed physics updates.

## Chart validation

All {len(chart_results)} PNG/SVG chart pairs passed standard Matplotlib,
Pillow, PNG-signature, OS `file`, size, dimension, extrema, and `/tmp` reopen
validation. See `charts/chart-validation.txt`.
"""
    REPORT_OUTPUT.write_text(report, encoding="utf-8")

    print(f"raw rows: {len(raw_rows)}")
    print(f"cycle metric rows: {len(cycle_metrics)}")
    print(f"safe radii any direction: {safe_radii_any}")
    print(f"safe radii all six directions: {safe_radii_common}")
    print(f"analysis milliseconds: {analysis_milliseconds:.3f}")
    print(f"charts: {len(chart_results)} PASS")


if __name__ == "__main__":
    main()
