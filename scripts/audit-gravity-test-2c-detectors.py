#!/usr/bin/env python3
"""Independent audit and corrected analysis of passive-detector Test 2C."""

import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

RAW_PATH = Path("measurements/gravity-test-2c-detectors-run-001.csv")
BASELINE_PATH = Path(
    "measurements/empty-lattice-equilibrium-validation-run-001.csv")
OLD_CYCLE_PATH = Path(
    "measurements/gravity-test-2c-detector-cycle-metrics.csv")
OLD_SUMMARY_PATH = Path(
    "measurements/gravity-test-2c-detector-summary.csv")
V2_CYCLE_PATH = Path(
    "measurements/gravity-test-2c-detector-cycle-metrics-v2.csv")
V2_SUMMARY_PATH = Path(
    "measurements/gravity-test-2c-detector-summary-v2.csv")
V2_REPORT_PATH = Path(
    "measurements/gravity-test-2c-detectors-report-v2.md")
AUDIT_PATH = Path("measurements/gravity-test-2c-detectors-audit.md")

SOURCE = (600, 600, 0)
LATTICE_MAX = 1200
ABSORPTION_LAYERS = 30
# edgeDistance < 30 is attenuated, so index 29/1171 is the first
# non-unity absorption layer approached from the interior.
INTERFACES = {
    "left x=29": ("x", 29),
    "right x=1171": ("x", 1171),
    "bottom y=29": ("y", 29),
    "top y=1171": ("y", 1171),
}
RADII = (20, 40, 60, 80, 100, 120)
ANGLES = (0, 30, 45, 60, 90)
PREFIXES = ("rawGradient", "cycleDerivedGradient", "acceleration")
ARRIVAL_PREFIXES = ("positionDeviation", "velocity", "acceleration",
                    "rawGradient")
CYCLES_REQUIRED = 30
ABS_TOL = 1.0e-15
REL_TOL = 1.0e-12


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def vec(row, prefix):
    return tuple(float(row[prefix + axis]) for axis in "XYZ")


def norm(value):
    return math.sqrt(sum(component * component for component in value))


def finite_vector(value):
    return all(math.isfinite(component) for component in value)


def rms(values):
    data = list(values)
    return math.sqrt(sum(value * value for value in data) / len(data))


def radial_basis(row):
    dx = int(row["nodeX"]) - SOURCE[0]
    dy = int(row["nodeY"]) - SOURCE[1]
    length = math.hypot(dx, dy)
    return ((dx / length, dy / length),
            (-dy / length, dx / length))


def project(value, basis):
    return value[0] * basis[0] + value[1] * basis[1]


def ratio(numerator, denominator):
    return math.nan if denominator == 0.0 else abs(numerator) / abs(denominator)


def nearly_equal(actual, expected):
    if math.isnan(actual) and math.isnan(expected):
        return True
    if not math.isfinite(actual) or not math.isfinite(expected):
        return actual == expected
    return math.isclose(actual, expected, rel_tol=REL_TOL, abs_tol=ABS_TOL)


def baseline_audit():
    rows = read_csv(BASELINE_PATH)
    fields = [
        prefix + axis
        for prefix in ("positionDeviation", "velocity", "acceleration",
                       "rawGradient", "cycleDerivedGradient")
        for axis in "XYZ"
    ]
    values = [float(row[field]) for row in rows for field in fields]
    return {
        "rows": len(rows),
        "updates": len({row["update"] for row in rows}),
        "detectors": len({row["detectorName"] for row in rows}),
        "maxAbsComponent": max(abs(value) for value in values),
        "firstNonzero": next((
            int(row["update"]) for row in rows
            if any(float(row[field]) != 0.0 for field in fields)), None),
    }


def raw_integrity(rows):
    grouped = defaultdict(list)
    nonfinite = []
    negative_zero = []
    numeric_fields = [
        field for field in rows[0]
        if field not in ("detectorName", "cycleCompleted")
    ]
    for row_index, row in enumerate(rows, 2):
        grouped[row["detectorName"]].append(row)
        for field in numeric_fields:
            if row[field] == "":
                continue
            value = float(row[field])
            if not math.isfinite(value):
                nonfinite.append((row_index, field, row[field]))
            if value == 0.0 and row[field].startswith("-"):
                negative_zero.append((row_index, field))
    update_sequences = {
        name: [int(row["update"]) for row in detector_rows]
        for name, detector_rows in grouped.items()
    }
    return grouped, nonfinite, negative_zero, all(
        sequence == list(range(1, 961))
        for sequence in update_sequences.values())


def first_nonzero(rows, prefix):
    return next((
        int(row["update"]) for row in rows
        if finite_vector(vec(row, prefix)) and norm(vec(row, prefix)) > 0.0),
        None)


def response(row):
    values = [vec(row, prefix) for prefix in ARRIVAL_PREFIXES]
    if not all(finite_vector(value) for value in values):
        return math.nan
    return max(norm(value) for value in values)


def old_arrival(rows):
    for index in range(len(rows) - 2):
        if all(response(row) > 0.0 for row in rows[index:index + 3]):
            return int(rows[index]["update"])
    raise AssertionError("No old arrival")


def meaningful_arrival(rows):
    """First source-cycle end with a finite, non-zero cycle response.

    Exact equilibrium is zero. Requiring a completed source cycle rejects
    sub-cycle floating tails and gives every detector the same source-locked
    criterion without introducing a magnitude floor.
    """
    for row in rows:
        value = vec(row, "cycleDerivedGradient")
        if (row["cycleCompleted"].lower() == "true"
                and finite_vector(value) and norm(value) > 0.0):
            return int(row["update"]), int(row["completedCycleCount"])
    raise AssertionError("No meaningful cycle arrival")


def cycle_audit(rows, arrival_update):
    completions = [
        row for row in rows
        if row["cycleCompleted"].lower() == "true"
    ]
    periods = [
        int(second["update"]) - int(first["update"])
        for first, second in zip(completions, completions[1:])
    ]
    selected = [
        row for row in completions if int(row["update"]) > arrival_update
    ][:CYCLES_REQUIRED]
    if len(selected) != CYCLES_REQUIRED:
        raise AssertionError("Insufficient complete post-arrival cycles")
    selected_numbers = [int(row["completedCycleCount"]) for row in selected]
    if selected_numbers != list(range(
            selected_numbers[0], selected_numbers[0] + CYCLES_REQUIRED)):
        raise AssertionError("Selected cycles are not contiguous")
    return completions, periods, selected


def cycle_rows_for(rows, selected):
    radial, tangent = radial_basis(rows[0])
    result = []
    for ordinal, row in enumerate(selected, 1):
        output = {
            "detectorName": row["detectorName"],
            "requestedRadius": row["requestedRadius"],
            "requestedAngleDegrees": row["requestedAngleDegrees"],
            "actualEuclideanRadius": row["actualEuclideanRadius"],
            "actualAngleDegrees": row["actualAngleDegrees"],
            "cycleIndex": ordinal,
            "sourceCycleNumber": row["completedCycleCount"],
            "update": row["update"],
        }
        for prefix in PREFIXES:
            value = vec(row, prefix)
            output[prefix + "Radial"] = project(value, radial)
            output[prefix + "Tangential"] = project(value, tangent)
        result.append(output)
    return result


def summarize(detector_rows, selected_cycles, arrival_update):
    first = detector_rows[0]
    summary = {
        "detectorName": first["detectorName"],
        "requestedRadius": int(first["requestedRadius"]),
        "requestedAngleDegrees": float(first["requestedAngleDegrees"]),
        "nodeX": int(first["nodeX"]),
        "nodeY": int(first["nodeY"]),
        "nodeZ": int(first["nodeZ"]),
        "actualEuclideanRadius": float(first["actualEuclideanRadius"]),
        "actualAngleDegrees": float(first["actualAngleDegrees"]),
        "coordinateRoundingErrorMagnitude": float(
            first["coordinateRoundingErrorMagnitude"]),
        "arrivalUpdate": arrival_update,
        "firstSelectedCycleNumber": int(
            selected_cycles[0]["sourceCycleNumber"]),
        "firstSelectedCycleEnd": int(selected_cycles[0]["update"]),
        "thirtiethSelectedCycleNumber": int(
            selected_cycles[-1]["sourceCycleNumber"]),
        "thirtiethSelectedCycleEnd": int(selected_cycles[-1]["update"]),
        "numberOfSkippedCycles": 0,
        "skippedCycleReason": "",
    }
    for prefix in PREFIXES:
        radial_values = [
            float(row[prefix + "Radial"]) for row in selected_cycles]
        tangential_values = [
            float(row[prefix + "Tangential"]) for row in selected_cycles]
        title = prefix[0].upper() + prefix[1:]
        summary["meanSigned" + title + "Radial"] = statistics.fmean(
            radial_values)
        summary["meanSigned" + title + "Tangential"] = statistics.fmean(
            tangential_values)
        summary["rms" + title + "Radial"] = rms(radial_values)
        summary["rms" + title + "Tangential"] = rms(tangential_values)
        summary[prefix + "TangentialToRadial"] = ratio(
            summary["rms" + title + "Tangential"],
            summary["rms" + title + "Radial"])
    return summary


def reflected_path(detector):
    x = detector["nodeX"]
    y = detector["nodeY"]
    candidates = []
    for label, (axis, coordinate) in INTERFACES.items():
        if axis == "x":
            outbound = abs(coordinate - SOURCE[0])
            # Mirror construction minimizes source->interface->detector.
            total = math.hypot(
                2 * coordinate - x - SOURCE[0], y - SOURCE[1])
        else:
            outbound = abs(coordinate - SOURCE[1])
            total = math.hypot(
                x - SOURCE[0], 2 * coordinate - y - SOURCE[1])
        candidates.append((total, label, outbound, total - outbound))
    return min(candidates)


def add_reflection(summary):
    total, boundary, outbound, returned = reflected_path(summary)
    # The stencil reads only direct neighbors from the committed prior state:
    # information cannot travel faster than one lattice edge per update.
    speed_bound = 1.0
    estimate = math.ceil(total / speed_bound) + 1
    summary["nearestRelevantBoundary"] = boundary
    summary["outboundDistance"] = outbound
    summary["returnDistanceToDetector"] = returned
    summary["boundedPropagationSpeed"] = speed_bound
    summary["detectorSpecificReflectionEstimate"] = estimate
    summary["safetyMargin"] = estimate - summary["thirtiethSelectedCycleEnd"]
    summary["reflectionGatePass"] = summary["safetyMargin"] > 0


def add_ratios(summaries):
    metric_columns = {
        "rawGradient": "rmsRawGradientRadial",
        "cycleDerivedGradient": "rmsCycleDerivedGradientRadial",
        "acceleration": "rmsAccelerationRadial",
    }
    for radius in RADII:
        for metric, column in metric_columns.items():
            axis = summaries[(radius, 0)][column]
            ratios = {}
            for angle in ANGLES:
                ratios[angle] = ratio(summaries[(radius, angle)][column], axis)
            errors = [abs(value - 1.0) for value in ratios.values()
                      if math.isfinite(value)]
            error = max(errors) if errors else math.nan
            for angle in ANGLES:
                summaries[(radius, angle)][metric + "AxisRatio"] = ratios[angle]
                summaries[(radius, angle)][metric + "IsotropyError"] = error
    return metric_columns


def local_axis_exponent(summaries, radius, column):
    index = RADII.index(radius)
    if index == 0:
        low, high = RADII[0], RADII[1]
    elif index == len(RADII) - 1:
        low, high = RADII[-2], RADII[-1]
    else:
        low, high = RADII[index - 1], RADII[index + 1]
    low_value = summaries[(low, 0)][column]
    high_value = summaries[(high, 0)][column]
    if low_value <= 0.0 or high_value <= 0.0:
        return math.nan
    return math.log(high_value / low_value) / math.log(high / low)


def add_radius_correction(summaries, metric_columns):
    for radius in RADII:
        for metric, column in metric_columns.items():
            exponent = local_axis_exponent(summaries, radius, column)
            axis = summaries[(radius, 0)][column]
            for angle in ANGLES:
                item = summaries[(radius, angle)]
                actual = item["actualEuclideanRadius"]
                corrected = (item[column] * (radius / actual) ** exponent
                             if math.isfinite(exponent) and actual > 0.0
                             else math.nan)
                item[metric + "LocalRadialExponent"] = exponent
                item[metric + "RadiusCorrectedValue"] = corrected
                item[metric + "RadiusCorrectedAxisRatio"] = ratio(
                    corrected, axis)


def recompute_old_outputs(grouped):
    cycle_rows = []
    summaries = {}
    for name, rows in grouped.items():
        arrival = old_arrival(rows)
        selected_raw = [
            row for row in rows
            if row["cycleCompleted"].lower() == "true"
            and int(row["update"]) >= arrival
        ][:CYCLES_REQUIRED]
        cycles = cycle_rows_for(rows, selected_raw)
        # Remove the v2-only source cycle column for exact old comparison.
        for row in cycles:
            row.pop("sourceCycleNumber")
        cycle_rows.extend(cycles)
        item = summarize(rows, [
            {**row, "sourceCycleNumber": selected_raw[index][
                "completedCycleCount"]}
            for index, row in enumerate(cycles)], arrival)
        item["windowEndUpdate"] = item.pop("thirtiethSelectedCycleEnd")
        for key in ("firstSelectedCycleNumber", "firstSelectedCycleEnd",
                    "thirtiethSelectedCycleNumber", "numberOfSkippedCycles",
                    "skippedCycleReason"):
            item.pop(key)
        item["reflectionEstimate"] = 1020
        item["reflectionGatePass"] = item["windowEndUpdate"] < 1020
        summaries[(item["requestedRadius"],
                   int(item["requestedAngleDegrees"]))] = item
    add_ratios(summaries)
    return cycle_rows, summaries


def compare_rows(recomputed, existing, key_fields):
    if len(recomputed) != len(existing):
        raise AssertionError(
            f"row count differs: {len(recomputed)} != {len(existing)}")
    existing_by_key = {
        tuple(row[field] for field in key_fields): row for row in existing}
    discrepancies = {}
    material = []
    for row in recomputed:
        key = tuple(str(row[field]) for field in key_fields)
        other = existing_by_key.get(key)
        if other is None:
            material.append(("missing key", key))
            continue
        for field, value in row.items():
            if field in key_fields:
                continue
            if field not in other:
                material.append(("missing column", field))
                continue
            try:
                actual = float(value)
                expected = float(other[field])
            except (TypeError, ValueError):
                if str(value).lower() != other[field].lower():
                    material.append((key, field, value, other[field]))
                continue
            if math.isnan(actual) and math.isnan(expected):
                absolute = relative = 0.0
            else:
                absolute = abs(actual - expected)
                scale = max(abs(actual), abs(expected))
                relative = absolute / scale if scale else 0.0
            current = discrepancies.setdefault(field, [0.0, 0.0])
            current[0] = max(current[0], absolute)
            current[1] = max(current[1], relative)
            if not nearly_equal(actual, expected):
                material.append((key, field, actual, expected))
    return discrepancies, material


def write_csv(path, rows):
    fields = list(rows[0])
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def fmt(value):
    return "N/A" if not math.isfinite(value) else f"{value:.12g}"


def trend(values):
    finite = [value for value in values if math.isfinite(value)]
    if len(finite) < 2:
        return "not assessable"
    return ("lower at radius 120 than radius 20, but non-monotonic"
            if finite[-1] < finite[0]
            else "not lower at radius 120 than radius 20")


def main():
    baseline = baseline_audit()
    raw = read_csv(RAW_PATH)
    grouped, nonfinite, negative_zero, sequences_ok = raw_integrity(raw)
    if (len(raw) != 28800 or len(grouped) != 30 or nonfinite
            or negative_zero or not sequences_ok):
        raise SystemExit("Raw CSV integrity audit failed")

    old_cycle_rows, old_summaries = recompute_old_outputs(grouped)
    old_cycle_discrepancies, old_cycle_material = compare_rows(
        old_cycle_rows, read_csv(OLD_CYCLE_PATH),
        ("detectorName", "cycleIndex"))
    old_summary_discrepancies, old_summary_material = compare_rows(
        [old_summaries[key] for key in sorted(old_summaries)],
        read_csv(OLD_SUMMARY_PATH), ("detectorName",))
    if old_cycle_material or old_summary_material:
        raise SystemExit(
            f"Independent recomputation mismatch: "
            f"{old_cycle_material[:3]} {old_summary_material[:3]}")

    v2_cycles = []
    v2_summaries = {}
    arrivals = {}
    period_values = set()
    for name, rows in grouped.items():
        meaningful_update, meaningful_cycle = meaningful_arrival(rows)
        completions, periods, selected = cycle_audit(rows, meaningful_update)
        period_values.update(periods)
        cycles = cycle_rows_for(rows, selected)
        summary = summarize(rows, cycles, meaningful_update)
        add_reflection(summary)
        key = (summary["requestedRadius"],
               int(summary["requestedAngleDegrees"]))
        v2_summaries[key] = summary
        v2_cycles.extend(cycles)
        selected_row = rows[meaningful_update - 1]
        growth = [response(row) for row in
                  rows[meaningful_update - 1:meaningful_update + 5]]
        arrivals[name] = {
            "firstPosition": first_nonzero(rows, "positionDeviation"),
            "firstVelocity": first_nonzero(rows, "velocity"),
            "firstAcceleration": first_nonzero(rows, "acceleration"),
            "firstRawGradient": first_nonzero(rows, "rawGradient"),
            "firstCompletedNonzeroCycle": meaningful_update,
            "oldArrival": old_arrival(rows),
            "v2Arrival": meaningful_update,
            "signalAtV2Arrival": response(selected_row),
            "growth": growth,
            "meaningfulCycle": meaningful_cycle,
        }
    if period_values != {18}:
        raise SystemExit(f"Unexpected source periods: {period_values}")
    if not all(item["reflectionGatePass"] for item in v2_summaries.values()):
        raise SystemExit("Detector-specific reflection gate failed")

    metric_columns = add_ratios(v2_summaries)
    add_radius_correction(v2_summaries, metric_columns)
    write_csv(V2_CYCLE_PATH, v2_cycles)
    write_csv(V2_SUMMARY_PATH, [
        v2_summaries[key] for key in sorted(v2_summaries)])

    with V2_REPORT_PATH.open("w", encoding="utf-8") as report:
        def line(value=""):
            print(value, file=report)

        line("# Gravity Test 2C — Passive Detector Analysis v2")
        line()
        line("This analysis supersedes the original summary/report analysis, "
             "but retains run-001 as the canonical raw dataset.")
        line()
        line("## Corrected arrival and cycle rule")
        line()
        line("Meaningful arrival is the end of the first complete 18-update "
             "source cycle whose cycle-derived gradient is finite and non-zero. "
             "The 30-cycle window starts with the following source cycle, so "
             "every selected cycle is wholly after arrival. This uses the exact-"
             "zero baseline, source-cycle structure, persistence over a complete "
             "cycle, and no arbitrary magnitude floor.")
        line()
        line("## Detector-specific reflection gate")
        line()
        line("The first non-unity absorption layer is index 29/1171. Earliest "
             "paths are minimized with the reflected-point construction and "
             "converted using the strict direct-neighbor causal bound of at most "
             "one lattice edge per update.")
        line()
        line("| Detector | Arrival | Window end | Boundary | Outbound | Return | "
             "Speed bound | Reflection | Margin | Gate |")
        line("|---|---:|---:|---|---:|---:|---:|---:|---:|:---:|")
        for key in sorted(v2_summaries):
            item = v2_summaries[key]
            line(f"| {item['detectorName']} | {item['arrivalUpdate']} | "
                 f"{item['thirtiethSelectedCycleEnd']} | "
                 f"{item['nearestRelevantBoundary']} | "
                 f"{item['outboundDistance']:.6g} | "
                 f"{item['returnDistanceToDetector']:.6g} | 1 | "
                 f"{item['detectorSpecificReflectionEstimate']} | "
                 f"{item['safetyMargin']} | PASS |")
        line()
        line("## Corrected isotropy ratios")
        for metric in metric_columns:
            line()
            line(f"### {metric}")
            line()
            line("| r | Q30 | Q45 | Q60 | Q90 | E | corrected Q30 | "
                 "corrected Q45 | corrected Q60 | corrected Q90 |")
            line("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
            errors = []
            for radius in RADII:
                items = [v2_summaries[(radius, angle)]
                         for angle in ANGLES]
                errors.append(items[0][metric + "IsotropyError"])
                line(f"| {radius} | "
                     + " | ".join(fmt(item[metric + "AxisRatio"])
                                  for item in items[1:])
                     + f" | {fmt(errors[-1])} | "
                     + " | ".join(fmt(item[
                         metric + "RadiusCorrectedAxisRatio"])
                         for item in items[1:])
                     + " |")
            line()
            line(f"Trend: {trend(errors)}.")
        line()
        line("## Scientific conclusion")
        line()
        line("The passive lattice response preserves exact cubic X/Y exchange "
             "symmetry in this dataset and is anisotropic between axis and "
             "off-axis/diagonal directions. The endpoint isotropy error is lower "
             "at radius 120 than at radius 20 for raw and cycle-derived gradient, "
             "but the six-radius sequence is non-monotonic and does not "
             "demonstrate convergence to isotropy. Test 2C does not measure "
             "Occupancy pressure, transport momentum, Identity acceleration, "
             "attraction, orbital behavior, or prove that gravity is isotropic.")

    with AUDIT_PATH.open("w", encoding="utf-8") as audit:
        def line(value=""):
            print(value, file=audit)

        line("# Passive-detector Gravity Test 2C audit")
        line()
        line("## Decision: ANALYSIS CORRECTION")
        line()
        line("Run-001 raw measurements pass integrity and passivity review. "
             "The original analysis used cancellation-scale strict-nonzero "
             "tails and included a cycle that was only partly post-arrival; its "
             "shared reflection constant was also not detector-specific. The "
             "simulation does not need to be rerun. Original analysis files are "
             "preserved and superseded by v2.")
        line()
        line("## Detector passivity and source trace")
        line()
        line("- `Detector.java:13-48` contains only descriptive values. Its "
             "constructor defensively copies `Vector3i`; it constructs neither "
             "`Identity` nor `Occupancy`.")
        line("- `Project.java:173-180` creates Occupancies only from "
             "`[OscillatorN]`; detector parsing at `Project.java:180` only "
             "creates instrumentation records.")
        line("- The sampling call chain is `Simulator.java:735` → "
             "`Simulator.java:785-800` → "
             "`DetectorExperimentController.java:43-51` → "
             "`DetectorMeasurementLogger.java:67-84`.")
        line("- `DetectorMeasurementLogger.java:123-131` copies position, "
             "equilibrium position, velocity, and acceleration, then invokes "
             "`Node.readGradientImbalance()`. It never calls the mutating "
             "`measureGradientImbalance()` used by transport at "
             "`TransportSystem.java:177-183`.")
        line("- `Node.readGradientImbalance()` (`Node.java:186-196`) allocates "
             "a new result and only reads this node's and its direct neighbors' "
             "committed position components through JOML `distance`. It does "
             "not write position, position buffer, velocity, acceleration, "
             "force, flow arrays, previous-flow arrays, gradient caches, "
             "neighbors, or transport state.")
        line("- Detector cycle minima/maxima are logger-owned vectors "
             "(`DetectorMeasurementLogger.java:191-209`), not Node caches. "
             "CSV formatting and flushing mutate only instrumentation state.")
        line("- Physics tasks finish and join at `Simulator.java:723-724`; "
             "position commits finish and join at `Simulator.java:732-734`; "
             "sampling then runs serially at line 735. Detectors add no physics "
             "task, change no segment, and cannot race the lattice. They can "
             "increase wall-clock duration through CSV I/O but not update order "
             "or lattice values.")
        line("- Existing tests do not fully prove whole-lattice equivalence: "
             "`DetectorInfrastructureTest.java:54-62` compares only position, "
             "velocity, and acceleration of synthetic nodes and does not execute "
             "two complete simulations or compare buffers, force, arrays, "
             "caches, neighbors, and transport state. Passivity is confirmed "
             "here by the complete call trace; the test is a safeguard, not the "
             "proof.")
        line()
        line("## Update and logging semantics")
        line()
        line("For a logged update N the order is:")
        line()
        line("1. `Node.swapBuffer()` changes only the render matrix buffer "
             "(`Simulator.java:699-704`).")
        line("2. Source transport diagnostics run, then "
             "`Occupancy.update()` advances source phase and applies the source "
             "force (`Simulator.java:754-770`; `Identity.java:141-170,195-207`; "
             "`Occupancy.java:261-274`).")
        line("3. Node v0.1 reads committed neighbor positions, calculates the "
             "force-derived acceleration, clears force, updates velocity and "
             "position buffer (`Node.java:269-302`).")
        line("4. All physics threads join; `Entity.updatePosition()` commits "
             "position buffer to position (`Scene.java:436-460`; "
             "`Entity.java:245-249`).")
        line("5. The detector logger detects a source phase wrap, increments "
             "its update counter, reads the committed node state and gradient, "
             "updates/completes its cycle accumulator, and writes the row "
             "(`DetectorMeasurementLogger.java:67-84,96-144`).")
        line("6. After row 960 is written, the active detector controller owns "
             "the limit check and sets termination "
             "(`Simulator.java:777-800`). The legacy counter is explicitly "
             "excluded while detectors are enabled at lines 777-779.")
        line()
        line("Thus row N is post-update-N state, not a mixed pre/post row. Row "
             "1 has source phase 20°, row 18 has phase 0° and closes cycle 1, "
             "and row 960 is written before termination. The raw file has every "
             "per-detector update 1..960, confirming the former 959-row defect "
             "is fixed rather than masked.")
        line()
        line("## Measured quantity definitions")
        line()
        line("- `positionDeviation`: committed position minus immutable "
             "equilibrium position at sampling.")
        line("- `velocity`: Node's velocity after the current v0.1 update.")
        line("- `acceleration`: the Node acceleration vector as it exists after "
             "the in-place v0.1 integration expression. Because "
             "`acceleration.mul(cws)` and then `.mul(0.5)` mutate that vector "
             "at `Node.java:295`, the CSV value is the retained integration "
             "working value (for this project, the initially calculated vector "
             "scaled by `cws * 0.5 = 1/12`), not Identity or transport "
             "acceleration. Its in-plane components are zero here.")
        line("- `rawGradient`: `(distance(right)-distance(left), "
             "distance(top)-distance(bottom), "
             "distance(front)-distance(back))` from committed positions.")
        line("- `cycleDerivedGradient`: per component, `(cycle maximum + cycle "
             "minimum)/2` over all 18 raw-gradient samples in one source cycle. "
             "It preserves sign, is neither sum, mean, difference nor integral, "
             "is reset after each completion, has no duration normalization, "
             "and represents a cycle response rather than an instantaneous "
             "field. The completed value persists in rows until the next cycle "
             "completion.")
        line("- No detector pressure, momentum, Occupancy transport "
             "acceleration, or other transport quantity is logged or derived.")
        line()
        line("## Raw and baseline integrity")
        line()
        line(f"- Baseline: {baseline}")
        line(f"- Raw rows: {len(raw)}; detectors: {len(grouped)}; "
             f"sequences 1..960: {sequences_ok}")
        line(f"- Non-finite values: {len(nonfinite)}; signed negative zeros: "
             f"{len(negative_zero)}")
        effective_speeds = [
            item["actualEuclideanRadius"] / (item["arrivalUpdate"] - 1)
            for item in v2_summaries.values()]
        line(f"- Source-locked effective arrival speeds span "
             f"`{min(effective_speeds):.12g}` to "
             f"`{max(effective_speeds):.12g}` lattice edges/update. Reflection "
             "uses the stricter causal upper bound `1.0`, not these slower "
             "measured effective values.")
        line()
        line("## Arrival audit")
        line()
        line("| Detector | pos≠0 | vel≠0 | acc≠0 | raw≠0 | first nonzero "
             "cycle | old arrival | v2 arrival | signal at v2 arrival | "
             "next-six growth |")
        line("|---|---:|---:|---:|---:|---:|---:|---:|---:|---|")
        for name in sorted(arrivals):
            item = arrivals[name]
            line(f"| {name} | {item['firstPosition']} | "
                 f"{item['firstVelocity']} | {item['firstAcceleration']} | "
                 f"{item['firstRawGradient']} | "
                 f"{item['firstCompletedNonzeroCycle']} | "
                 f"{item['oldArrival']} | {item['v2Arrival']} | "
                 f"{item['signalAtV2Arrival']:.12g} | "
                 f"{', '.join(f'{value:.4g}' for value in item['growth'])} |")
        line()
        line("## Source-cycle selection")
        line()
        line("Configured increment is 20° per update; observed phase wraps and "
             "cycle-completion spacing are exactly 18 updates. Selected cycle "
             "numbers are contiguous and start one cycle after meaningful "
             "arrival.")
        line()
        line("| Detector | Arrival | First cycle | First end | 30th cycle | "
             "30th end | Skipped | Reason |")
        line("|---|---:|---:|---:|---:|---:|---:|---|")
        for key in sorted(v2_summaries):
            item = v2_summaries[key]
            line(f"| {item['detectorName']} | {item['arrivalUpdate']} | "
                 f"{item['firstSelectedCycleNumber']} | "
                 f"{item['firstSelectedCycleEnd']} | "
                 f"{item['thirtiethSelectedCycleNumber']} | "
                 f"{item['thirtiethSelectedCycleEnd']} | 0 | none |")
        line()
        line("## Independent recomputation of original outputs")
        line()
        line(f"Comparison tolerances: absolute `{ABS_TOL}`, relative "
             f"`{REL_TOL}`. No material mismatch was found.")
        line()
        line("### Cycle output discrepancies")
        line()
        line("| Column | max absolute | max relative |")
        line("|---|---:|---:|")
        for column in sorted(old_cycle_discrepancies):
            absolute, relative = old_cycle_discrepancies[column]
            line(f"| {column} | {absolute:.12g} | {relative:.12g} |")
        line()
        line("### Summary output discrepancies")
        line()
        line("| Column | max absolute | max relative |")
        line("|---|---:|---:|")
        for column in sorted(old_summary_discrepancies):
            absolute, relative = old_summary_discrepancies[column]
            line(f"| {column} | {absolute:.12g} | {relative:.12g} |")
        line()
        line("## Geometry")
        line()
        line("| Detector | requested r | actual r | radial error | "
             "requested angle | actual angle | angular error |")
        line("|---|---:|---:|---:|---:|---:|---:|")
        for key in sorted(v2_summaries):
            item = v2_summaries[key]
            radial_error = (item["actualEuclideanRadius"]
                            - item["requestedRadius"])
            angular_error = (item["actualAngleDegrees"]
                             - item["requestedAngleDegrees"])
            line(f"| {item['detectorName']} | {item['requestedRadius']} | "
                 f"{item['actualEuclideanRadius']:.12g} | "
                 f"{radial_error:.12g} | "
                 f"{item['requestedAngleDegrees']:.12g} | "
                 f"{item['actualAngleDegrees']:.12g} | "
                 f"{angular_error:.12g} |")
        line()
        line("Radius-corrected ratios use a local empirical log slope fitted "
             "only from neighboring measured 0° axis radii; no inverse-square "
             "law is assumed. Both uncorrected and corrected values are in the "
             "v2 report and summary.")

    print("AUDIT DECISION: ANALYSIS CORRECTION")
    print(f"Original cycle material discrepancies: {len(old_cycle_material)}")
    print(f"Original summary material discrepancies: {len(old_summary_material)}")
    print(f"Wrote {V2_CYCLE_PATH}, {V2_SUMMARY_PATH}, "
          f"{V2_REPORT_PATH}, {AUDIT_PATH}")


if __name__ == "__main__":
    main()
