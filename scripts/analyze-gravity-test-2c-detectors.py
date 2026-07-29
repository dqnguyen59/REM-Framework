#!/usr/bin/env python3
"""Analyze passive-detector Gravity Test 2C after the equilibrium gate."""

import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

SOURCE = (600, 600)
RADII = (20, 40, 60, 80, 100, 120)
ANGLES = (0, 30, 45, 60, 90)
CYCLES_REQUIRED = 30
PERSISTENCE_UPDATES = 3
REFLECTION_LIMIT = 1020
INPUT = Path("measurements/gravity-test-2c-detectors-run-001.csv")
BASELINE = Path("measurements/empty-lattice-equilibrium-validation-run-001.csv")
CYCLES = Path("measurements/gravity-test-2c-detector-cycle-metrics.csv")
SUMMARY = Path("measurements/gravity-test-2c-detector-summary.csv")
REPORT = Path("measurements/gravity-test-2c-detectors-report.md")

VECTOR_PREFIXES = ("rawGradient", "cycleDerivedGradient", "acceleration")
ARRIVAL_PREFIXES = ("positionDeviation", "velocity", "acceleration",
                    "rawGradient")


def vector(row, prefix):
    return tuple(float(row[prefix + axis]) for axis in "XYZ")


def magnitude(value):
    return math.sqrt(sum(component * component for component in value))


def rms(values):
    values = list(values)
    return math.sqrt(statistics.fmean(value * value for value in values))


def basis(row):
    dx = int(row["nodeX"]) - SOURCE[0]
    dy = int(row["nodeY"]) - SOURCE[1]
    length = math.hypot(dx, dy)
    return (dx / length, dy / length), (-dy / length, dx / length)


def components(row, prefix, radial, tangent):
    value = vector(row, prefix)
    return (value[0] * radial[0] + value[1] * radial[1],
            value[0] * tangent[0] + value[1] * tangent[1])


def baseline_result():
    rows = list(csv.DictReader(BASELINE.open(encoding="utf-8")))
    columns = [
        prefix + axis
        for prefix in ("positionDeviation", "velocity", "acceleration",
                       "rawGradient", "cycleDerivedGradient")
        for axis in "XYZ"
    ]
    maximum_component = max(abs(float(row[column]))
                            for row in rows for column in columns)
    maximum_magnitude = max(
        magnitude(vector(row, prefix))
        for row in rows
        for prefix in ("positionDeviation", "velocity", "acceleration",
                       "rawGradient", "cycleDerivedGradient"))
    first_nonzero = next((
        int(row["update"]) for row in rows
        if any(float(row[column]) != 0.0 for column in columns)), None)
    return rows, maximum_component, maximum_magnitude, first_nonzero


def response_magnitude(row):
    return max(magnitude(vector(row, prefix)) for prefix in ARRIVAL_PREFIXES)


def arrival(rows, tolerance):
    for index in range(len(rows) - PERSISTENCE_UPDATES + 1):
        window = rows[index:index + PERSISTENCE_UPDATES]
        if all(response_magnitude(row) > tolerance for row in window):
            return int(rows[index]["update"])
    raise ValueError(f"No sustained detector arrival for {rows[0]['detectorName']}")


def summarize_window(rows, arrival_update):
    completed = [
        row for row in rows
        if row["cycleCompleted"].lower() == "true"
        and int(row["update"]) >= arrival_update
    ][:CYCLES_REQUIRED]
    if len(completed) != CYCLES_REQUIRED:
        raise ValueError(
            f"{rows[0]['detectorName']} has only {len(completed)} complete "
            "post-arrival cycles")
    radial, tangent = basis(rows[0])
    metrics = {}
    cycle_rows = []
    for cycle_index, row in enumerate(completed, 1):
        values = {}
        for prefix in VECTOR_PREFIXES:
            values[prefix + "Radial"], values[prefix + "Tangential"] = (
                components(row, prefix, radial, tangent))
        cycle_rows.append({
            "detectorName": row["detectorName"],
            "requestedRadius": row["requestedRadius"],
            "requestedAngleDegrees": row["requestedAngleDegrees"],
            "actualEuclideanRadius": row["actualEuclideanRadius"],
            "actualAngleDegrees": row["actualAngleDegrees"],
            "cycleIndex": cycle_index,
            "update": row["update"],
            **values,
        })
    for prefix in VECTOR_PREFIXES:
        radial_values = [float(row[prefix + "Radial"]) for row in cycle_rows]
        tangent_values = [float(row[prefix + "Tangential"]) for row in cycle_rows]
        metrics["meanSigned" + prefix[0].upper() + prefix[1:] + "Radial"] = (
            statistics.fmean(radial_values))
        metrics["meanSigned" + prefix[0].upper() + prefix[1:] + "Tangential"] = (
            statistics.fmean(tangent_values))
        metrics["rms" + prefix[0].upper() + prefix[1:] + "Radial"] = rms(
            radial_values)
        metrics["rms" + prefix[0].upper() + prefix[1:] + "Tangential"] = rms(
            tangent_values)
    return completed[-1], metrics, cycle_rows


def ratio(numerator, denominator):
    if denominator == 0.0:
        return math.nan
    return abs(numerator) / abs(denominator)


def trend(values):
    finite = [value for value in values if math.isfinite(value)]
    if len(finite) < 2:
        return "not assessable"
    delta = finite[-1] - finite[0]
    scale = max(abs(finite[0]), abs(finite[-1]), 1.0e-300)
    if abs(delta) <= scale * 0.05:
        return "remains approximately constant"
    return "decreases" if delta < 0 else "increases"


def main():
    baseline_rows, tolerance, baseline_magnitude, first_nonzero = (
        baseline_result())
    rows = list(csv.DictReader(INPUT.open(encoding="utf-8")))
    grouped = defaultdict(list)
    for row in rows:
        grouped[row["detectorName"]].append(row)
    if len(rows) != 30 * 960 or len(grouped) != 30:
        raise ValueError("Detector collection integrity failed")

    summaries = {}
    all_cycles = []
    for name, detector_rows in grouped.items():
        detected = arrival(detector_rows, tolerance)
        window_end, metrics, cycle_rows = summarize_window(
            detector_rows, detected)
        first = detector_rows[0]
        summary = {
            "detectorName": name,
            "requestedRadius": int(first["requestedRadius"]),
            "requestedAngleDegrees": float(first["requestedAngleDegrees"]),
            "nodeX": int(first["nodeX"]),
            "nodeY": int(first["nodeY"]),
            "nodeZ": int(first["nodeZ"]),
            "actualEuclideanRadius": float(first["actualEuclideanRadius"]),
            "actualAngleDegrees": float(first["actualAngleDegrees"]),
            "coordinateRoundingErrorMagnitude": float(
                first["coordinateRoundingErrorMagnitude"]),
            "arrivalUpdate": detected,
            "windowEndUpdate": int(window_end["update"]),
            "reflectionEstimate": REFLECTION_LIMIT,
            "reflectionGatePass":
                int(window_end["update"]) < REFLECTION_LIMIT,
            **metrics,
        }
        for prefix in VECTOR_PREFIXES:
            radial_key = "rms" + prefix[0].upper() + prefix[1:] + "Radial"
            tangent_key = "rms" + prefix[0].upper() + prefix[1:] + "Tangential"
            summary[prefix + "TangentialToRadial"] = ratio(
                summary[tangent_key], summary[radial_key])
        summaries[(summary["requestedRadius"],
                   int(summary["requestedAngleDegrees"]))] = summary
        all_cycles.extend(cycle_rows)

    if not all(item["reflectionGatePass"] for item in summaries.values()):
        failures = [item for item in summaries.values()
                    if not item["reflectionGatePass"]]
        raise SystemExit(f"Reflection gate failed: {failures}")

    metric_keys = {
        "rawGradient": "rmsRawGradientRadial",
        "cycleDerivedGradient": "rmsCycleDerivedGradientRadial",
        "acceleration": "rmsAccelerationRadial",
    }
    for radius in RADII:
        for metric, key in metric_keys.items():
            axis = summaries[(radius, 0)][key]
            ratios = {
                angle: ratio(summaries[(radius, angle)][key], axis)
                for angle in ANGLES
            }
            finite_errors = [abs(value - 1.0) for value in ratios.values()
                             if math.isfinite(value)]
            error = max(finite_errors) if finite_errors else math.nan
            for angle in ANGLES:
                summaries[(radius, angle)][metric + "AxisRatio"] = ratios[angle]
                summaries[(radius, angle)][metric + "IsotropyError"] = error

    cycle_fields = list(all_cycles[0])
    with CYCLES.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=cycle_fields)
        writer.writeheader()
        writer.writerows(all_cycles)
    summary_fields = list(next(iter(summaries.values())))
    with SUMMARY.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=summary_fields)
        writer.writeheader()
        writer.writerows(summaries[key] for key in sorted(summaries))

    with REPORT.open("w", encoding="utf-8") as report:
        def line(value=""):
            print(value, file=report)

        line("# Gravity Test 2C — Passive Detector Isotropy")
        line()
        line("## Implementation and physics isolation")
        line()
        line("Detectors are immutable instrumentation records resolved to fixed "
             "lattice indices. Sampling copies node position, equilibrium "
             "position, velocity, acceleration, and a pure neighbor-distance "
             "gradient read. It creates no Identity or Occupancy and performs "
             "no physics write. No physics formula was changed.")
        line()
        line("Pressure and transport momentum are intentionally absent: those "
             "quantities belong to Occupancy, while this analysis is entirely "
             "node-level. The earlier active-receiver Test 2C is superseded "
             "because its receivers oscillated and generated fields.")
        line()
        line("## Empty-lattice equilibrium gate")
        line()
        line(f"- Rows: {len(baseline_rows)} (11 detectors × 960 updates)")
        line(f"- Maximum absolute component: `{tolerance}`")
        line(f"- Maximum vector magnitude: `{baseline_magnitude}`")
        line(f"- First non-zero update: `{first_nonzero}`")
        line(f"- Result: {'exact zero' if tolerance == 0 else 'below tolerance'}")
        line(f"- Derived numerical tolerance: `{tolerance}`")
        line()
        line("## Collection, arrival, and reflection")
        line()
        line(f"- Rows: {len(rows)} (30 detectors × 960 updates)")
        line(f"- Arrival criterion: first {PERSISTENCE_UPDATES} consecutive "
             "updates with a finite node response strictly above the validated "
             "empty-lattice tolerance.")
        ordered = all(
            min(summaries[(radius, angle)]["arrivalUpdate"] for angle in ANGLES)
            > min(summaries[(previous, angle)]["arrivalUpdate"]
                  for angle in ANGLES)
            for previous, radius in zip(RADII, RADII[1:]))
        line(f"- Increasing-radius arrival ordering: "
             f"{'PASS' if ordered else 'FAIL'}")
        line(f"- Conservative absorption-zone reflection estimate: update "
             f"`{REFLECTION_LIMIT}`")
        line("- Every 30-cycle window ends before the reflection estimate: PASS")
        line()
        line("| Detector | Arrival | Cycle 30 end | Reflection | Gate |")
        line("|---|---:|---:|---:|:---:|")
        for key in sorted(summaries):
            item = summaries[key]
            line(f"| {item['detectorName']} | {item['arrivalUpdate']} | "
                 f"{item['windowEndUpdate']} | {REFLECTION_LIMIT} | PASS |")
        line()
        line("## Isotropy ratios")
        for metric, key in metric_keys.items():
            line()
            line(f"### {metric}")
            line()
            line("| Radius | Q30 | Q45 | Q60 | Q90 | E(r) |")
            line("|---:|---:|---:|---:|---:|---:|")
            errors = []
            for radius in RADII:
                values = [summaries[(radius, angle)]
                          [metric + "AxisRatio"] for angle in ANGLES[1:]]
                error = summaries[(radius, 0)][metric + "IsotropyError"]
                errors.append(error)
                line("| " + str(radius) + " | "
                     + " | ".join(f"{value:.12g}" for value in values)
                     + f" | {error:.12g} |")
            line()
            line(f"Radius trend: **{trend(errors)}**.")
        line()
        line("Node acceleration has no in-plane radial component in this "
             "transverse-Z experiment, so its axis-normalized ratios and "
             "isotropy error are `NaN`; this is reported, not replaced with an "
             "Occupancy-derived transport acceleration.")
        line()
        line("## Symmetry and tangential leakage")
        line()
        line("| Radius | Metric | 0°/90° symmetry | 30°/60° symmetry | "
             "max tangential/radial |")
        line("|---:|---|---:|---:|---:|")
        for radius in RADII:
            for metric, key in metric_keys.items():
                zero = summaries[(radius, 0)][key]
                s_axis = ratio(summaries[(radius, 90)][key], zero)
                s_pair = ratio(summaries[(radius, 60)][key],
                               summaries[(radius, 30)][key])
                leakage = max(
                    summaries[(radius, angle)]
                    [metric + "TangentialToRadial"]
                    for angle in ANGLES)
                line(f"| {radius} | {metric} | {s_axis:.12g} | "
                     f"{s_pair:.12g} | {leakage:.12g} |")
        line()
        line("## Convergence assessment and limitations")
        line()
        line("The tables report measured angular ratios without claiming that "
             "visual output proves an emergent physical behavior. Coordinate "
             "rounding means requested and actual angles/radii differ at "
             "non-axis points. Only 30 complete cycles are analyzed, the "
             "lattice is a one-node-thick plane, and conclusions are limited "
             "to Node v0.1, this update order, precision, source amplitude, "
             "boundary implementation, and the pre-reflection windows above.")

    print(f"wrote {CYCLES}, {SUMMARY}, and {REPORT}")


if __name__ == "__main__":
    main()
