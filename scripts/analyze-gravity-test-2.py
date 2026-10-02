#!/usr/bin/env python3
"""Validate and summarize the seven approved Gravity Test 2 runs."""

import argparse
import csv
import math
from collections import defaultdict

ZERO_TOLERANCE = 1.0e-30
VECTOR_STAGES = (
    "rawGradient", "scaledGradient", "cycleGradientAfter", "acceleration",
    "momentumCandidate", "momentumAfter", "pressureIncrement", "pressureAfter",
)


def number(row, key):
    return float(row[key])


def truth(row, key):
    return row[key].lower() == "true"


def vector_max(rows, prefix):
    return max(abs(number(row, prefix + axis)) for row in rows for axis in "XYZ")


def first_nonzero(rows, columns):
    for row in rows:
        if any(abs(number(row, column)) > ZERO_TOLERANCE for column in columns):
            return int(row["updateNumber"])
    return None


def slope(rows, column):
    xs = [number(row, "updateNumber") for row in rows]
    ys = [number(row, column) for row in rows]
    x_mean = sum(xs) / len(xs)
    y_mean = sum(ys) / len(ys)
    denominator = sum((x - x_mean) ** 2 for x in xs)
    return sum((x - x_mean) * (y - y_mean)
               for x, y in zip(xs, ys)) / denominator


def load(label, filename):
    with open(filename, newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    updates = [int(row["updateNumber"]) for row in rows]
    assert len(rows) == 500, f"{label}: expected 500 rows, got {len(rows)}"
    assert updates == list(range(1, 501)), f"{label}: invalid update sequence"
    assert not any(truth(row, "nodeMoved") for row in rows), f"{label}: receiver moved"
    assert not any(truth(row, "transferOccurred") for row in rows), f"{label}: transfer occurred"
    assert all(truth(row, "transferSuppressed") for row in rows), f"{label}: suppression absent"
    for row in rows:
        for key, value in row.items():
            if key in {"runId", "experimentName", "receiverConfiguration",
                       "receiverName", "dominantThresholdAxis"}:
                continue
            if value.lower() in {"true", "false"}:
                continue
            assert math.isfinite(float(value)), f"{label}: non-finite {key} at {row['updateNumber']}"
        for axis in "XYZ":
            before = number(row, "pressureBefore" + axis)
            increment = number(row, "pressureIncrement" + axis)
            after = number(row, "pressureAfter" + axis)
            assert abs((before + increment) - after) <= ZERO_TOLERANCE
            assert increment == number(row, "momentumAfter" + axis)
    for previous, current in zip(rows, rows[1:]):
        for axis in "XYZ":
            assert number(previous, "pressureAfter" + axis) == number(
                current, "pressureBefore" + axis), (
                    f"{label}: pressure discontinuity before update "
                    f"{current['updateNumber']}")
    return rows


def summarize(label, rows):
    cycle_updates = [int(row["updateNumber"]) for row in rows
                     if truth(row, "cycleCompleted")]
    increments = [number(row, "pressureIncrementRadial") for row in rows]
    exact_zero = sum(value == 0.0 for value in increments)
    near_zero = sum(abs(value) <= ZERO_TOLERANCE for value in increments)
    summary = {
        "cycles": len(cycle_updates),
        "cycle_updates": cycle_updates,
        "mean_cycle_updates": (
            sum(b - a for a, b in zip(cycle_updates, cycle_updates[1:]))
            / (len(cycle_updates) - 1) if len(cycle_updates) > 1 else None),
        "first_raw": first_nonzero(rows, [f"rawGradient{x}" for x in "XYZ"]),
        "first_cycle_gradient": first_nonzero(
            rows, [f"cycleGradientAfter{x}" for x in "XYZ"]),
        "first_acceleration": first_nonzero(rows, [f"acceleration{x}" for x in "XYZ"]),
        "first_pressure_increment": first_nonzero(
            rows, [f"pressureIncrement{x}" for x in "XYZ"]),
        "first_pressure": first_nonzero(rows, [f"pressureAfter{x}" for x in "XYZ"]),
        "clamped_updates": sum(truth(row, "momentumClampedAny") for row in rows),
        "max_raw": vector_max(rows, "rawGradient"),
        "max_scaled": vector_max(rows, "scaledGradient"),
        "max_cycle_gradient": vector_max(rows, "cycleGradientAfter"),
        "max_acceleration": vector_max(rows, "acceleration"),
        "max_momentum_candidate": vector_max(rows, "momentumCandidate"),
        "max_momentum_after": vector_max(rows, "momentumAfter"),
        "final_pressure": tuple(number(rows[-1], f"pressureAfter{x}") for x in "XYZ"),
        "final_radial_pressure": number(rows[-1], "pressureAfterRadial"),
        "min_radial_increment": min(increments),
        "max_radial_increment": max(increments),
        "inward": sum(value < -ZERO_TOLERANCE for value in increments),
        "outward": sum(value > ZERO_TOLERANCE for value in increments),
        "near_zero": near_zero,
        "exact_zero": exact_zero,
        "pressure_slope": slope(rows, "pressureAfterRadial"),
        "threshold_crossings": sum(truth(row, "thresholdCrossedAny") for row in rows),
    }
    print(f"\n{label}:")
    for key, value in summary.items():
        print(f"  {key}: {value}")
    return summary


def compare(location, on_rows, off_rows):
    aligned_cycles = [
        int(a["updateNumber"]) for a, b in zip(on_rows, off_rows)
        if truth(a, "cycleCompleted") and truth(b, "cycleCompleted")
    ]
    cycle_alignment = [
        truth(a, "cycleCompleted") == truth(b, "cycleCompleted")
        for a, b in zip(on_rows, off_rows)
    ]
    phase_difference = max(abs(number(a, "receiverPhaseZ") -
                               number(b, "receiverPhaseZ"))
                           for a, b in zip(on_rows, off_rows))
    print(f"\n{location} source-on versus source-off:")
    print(f"  cycle completion fully aligned: {all(cycle_alignment)}")
    print(f"  matching completed-cycle updates: {aligned_cycles}")
    print(f"  maximum phase difference: {phase_difference}")
    for column in ("pressureAfterRadial", "pressureIncrementRadial",
                   "cycleGradientAfterRadial"):
        differences = [number(a, column) - number(b, column)
                       for a, b in zip(on_rows, off_rows)]
        print(f"  diagnostic {column} difference: "
              f"final={differences[-1]} mean={sum(differences) / len(differences)} "
              f"min={min(differences)} max={max(differences)}")
    for column in ("rawGradientRadial", "accelerationRadial",
                   "momentumAfterRadial"):
        differences = [number(a, column) - number(b, column)
                       for a, b in zip(on_rows, off_rows)]
        print(f"  diagnostic {column} difference max_abs="
              f"{max(abs(value) for value in differences)}")
    by_cycle = defaultdict(list)
    for row in on_rows:
        if truth(row, "cycleCompleted"):
            by_cycle[int(row["completedCycleCount"])].append(
                number(row, "cycleGradientAfterRadial"))
    off_by_cycle = {
        int(row["completedCycleCount"]): number(row, "cycleGradientAfterRadial")
        for row in off_rows if truth(row, "cycleCompleted")
    }
    cycle_differences = {
        cycle: values[-1] - off_by_cycle[cycle]
        for cycle, values in by_cycle.items() if cycle in off_by_cycle
    }
    print(f"  diagnostic cycle-indexed gradient differences: {cycle_differences}")


def zero_gate(rows):
    assert any(abs(number(row, f"rawGradient{x}")) > ZERO_TOLERANCE
               for row in rows for x in "XYZ"), "zero gate: raw field stayed zero"
    assert all(number(row, "receiverAmplitudeLength") == 0.0 for row in rows)
    for prefix in ("acceleration", "momentumDelta", "momentumAfter",
                   "pressureIncrement", "pressureAfter"):
        assert vector_max(rows, prefix) == 0.0, f"zero gate: nonzero {prefix}"
    print("\nZero-receiver gate: PASS")
    print("  completed-cycle updates:",
          [int(row["updateNumber"]) for row in rows if truth(row, "cycleCompleted")])


def main():
    parser = argparse.ArgumentParser()
    for option in ("e10_on", "e10_off", "ne14_on", "ne14_off",
                   "e20_on", "e20_off", "e10_zero"):
        parser.add_argument("--" + option.replace("_", "-"), required=True)
    args = parser.parse_args()
    files = vars(args)
    print("Numerical zero tolerance:", ZERO_TOLERANCE)
    data = {label: load(label, filename) for label, filename in files.items()}
    summaries = {label: summarize(label, rows) for label, rows in data.items()}
    for location in ("e10", "ne14", "e20"):
        compare(location, data[location + "_on"], data[location + "_off"])
    zero_gate(data["e10_zero"])
    responsive = [summaries[key] for key in summaries if key != "e10_zero"]
    gates = {
        "responsive cycles complete": all(item["cycles"] > 0 for item in responsive),
        "cycle gradients update": all(item["first_cycle_gradient"] is not None
                                      for item in responsive),
        "momentum not dominated by clamping":
            all(item["clamped_updates"] < 250 for item in responsive),
        "zero-amplitude gate": True,
    }
    print("\nAcceptance gates:")
    for gate, passed in gates.items():
        print(f"  {gate}: {'PASS' if passed else 'FAIL'}")
    if not all(gates.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
