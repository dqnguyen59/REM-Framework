#!/usr/bin/env python3
"""Validate Gravity Test 2B and compare angular response at radii 20 and 60."""

import argparse
import csv
import math
import statistics
from pathlib import Path

ZERO_TOLERANCE = 1.0e-30
UNCHANGED_RATIO_TOLERANCE = 0.05
TAGS = ("b00", "b30", "b45", "b60", "b90")
A_TAGS = ("a00", "a30", "a45", "a60", "a90")
CONFIG_FIELDS = (
    "receiverInitialNodeX", "receiverInitialNodeY", "receiverInitialNodeZ",
    "receiverOffsetX", "receiverOffsetY", "receiverOffsetZ",
    "receiverEuclideanRadius", "receiverPlanarAngleDegrees",
    "receiverManhattanDistance", "receiverActiveX", "receiverActiveY",
    "receiverActiveZ", "receiverAmplitudeX", "receiverAmplitudeY",
    "receiverAmplitudeZ", "receiverAmplitudeLength",
    "receiverPhaseIncrementZ", "gradientGain", "accelerationGain",
    "transportMomentumGain", "transportMomentumMax", "transferThreshold",
)


def num(row, key):
    return float(row[key])


def flag(row, key):
    return row[key].lower() == "true"


def first_nonzero(rows, key):
    for row in rows:
        if abs(num(row, key)) > ZERO_TOLERANCE:
            return int(row["updateNumber"])
    return None


def slope(rows, key):
    xs = [num(row, "updateNumber") for row in rows]
    ys = [num(row, key) for row in rows]
    mx, my = statistics.fmean(xs), statistics.fmean(ys)
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sum(
        (x - mx) ** 2 for x in xs)


def load(name, filename, expected_rows):
    with open(filename, newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    assert len(rows) == expected_rows, f"{name}: {len(rows)} rows"
    assert [int(row["updateNumber"]) for row in rows] == list(
        range(1, expected_rows + 1)), f"{name}: update sequence"
    initial = tuple(rows[0][f"receiverInitialNode{axis}"] for axis in "XYZ")
    geometry = tuple(rows[0][key] for key in (
        "receiverOffsetX", "receiverOffsetY", "receiverOffsetZ",
        "receiverEuclideanRadius", "receiverPlanarAngleDegrees",
        "receiverManhattanDistance"))
    for index, row in enumerate(rows):
        assert tuple(row[f"receiverInitialNode{axis}"] for axis in "XYZ") == initial
        assert tuple(row[key] for key in (
            "receiverOffsetX", "receiverOffsetY", "receiverOffsetZ",
            "receiverEuclideanRadius", "receiverPlanarAngleDegrees",
            "receiverManhattanDistance")) == geometry
        assert not flag(row, "nodeMoved")
        assert not flag(row, "transferOccurred")
        assert flag(row, "transferSuppressed")
        assert not flag(row, "momentumClampedAny")
        assert not flag(row, "thresholdCrossedAny")
        for key, item in row.items():
            if key in {"runId", "experimentName", "receiverConfiguration",
                       "receiverName", "dominantThresholdAxis"}:
                continue
            if item.lower() in {"true", "false"}:
                continue
            assert math.isfinite(float(item)), f"{name}: non-finite {key}"
        for axis in "XYZ":
            before = num(row, "pressureBefore" + axis)
            increment = num(row, "pressureIncrement" + axis)
            after = num(row, "pressureAfter" + axis)
            assert abs(before + increment - after) <= ZERO_TOLERANCE
            assert increment == num(row, "momentumAfter" + axis)
            if index:
                assert before == num(rows[index - 1], "pressureAfter" + axis)
    return rows


def base_metrics(rows):
    increments = [num(row, "pressureIncrementRadial") for row in rows]
    cycles = [row for row in rows if flag(row, "cycleCompleted")]
    return {
        "radius": num(rows[0], "receiverEuclideanRadius"),
        "angle": num(rows[0], "receiverPlanarAngleDegrees"),
        "manhattan": int(num(rows[0], "receiverManhattanDistance")),
        "cycles": len(cycles),
        "cycle_updates": [int(row["updateNumber"]) for row in cycles],
        "first_raw": first_nonzero(rows, "rawGradientRadial"),
        "first_cycle": first_nonzero(rows, "cycleGradientAfterRadial"),
        "first_acceleration": first_nonzero(rows, "accelerationRadial"),
        "first_pressure_increment": first_nonzero(
            rows, "pressureIncrementRadial"),
        "final_pressure": num(rows[-1], "pressureAfterRadial"),
        "pressure_slope": slope(rows, "pressureAfterRadial"),
        "mean_increment": statistics.fmean(increments),
        "median_increment": statistics.median(increments),
        "min_increment": min(increments),
        "max_increment": max(increments),
        "inward": sum(item < -ZERO_TOLERANCE for item in increments),
        "outward": sum(item > ZERO_TOLERANCE for item in increments),
        "near_zero": sum(abs(item) <= ZERO_TOLERANCE for item in increments),
        "exact_zero": sum(item == 0 for item in increments),
        "max_raw_radial": max(abs(num(row, "rawGradientRadial")) for row in rows),
        "max_cycle_radial": max(
            abs(num(row, "cycleGradientAfterRadial")) for row in rows),
        "max_cycle_tangential": max(
            num(row, "cycleGradientAfterPlanarTangentialMagnitude")
            for row in rows),
        "max_acceleration_radial": max(
            abs(num(row, "accelerationRadial")) for row in rows),
        "max_momentum_radial": max(
            abs(num(row, "momentumAfterRadial")) for row in rows),
        "max_tangential_pressure": max(
            num(row, "pressureAfterPlanarTangentialMagnitude") for row in rows),
        "clamped": sum(flag(row, "momentumClampedAny") for row in rows),
        "threshold": sum(flag(row, "thresholdCrossedAny") for row in rows),
        "final_cycle_radial": num(cycles[-1], "cycleGradientAfterRadial"),
        "final_cycle_tangential": num(
            cycles[-1], "cycleGradientAfterPlanarTangentialMagnitude"),
    }, cycles


def window_metrics(rows, cycles):
    final_magnitude = abs(num(cycles[-1], "cycleGradientAfterRadial"))
    threshold = 0.95 * final_magnitude
    selected = next(row for row in cycles
                    if abs(num(row, "cycleGradientAfterRadial")) >= threshold)
    start = int(selected["updateNumber"])
    window = rows[start - 1:]
    midpoint = len(window) // 2
    halves = (window[:midpoint], window[midpoint:])

    def mean_abs(subset, key):
        return statistics.fmean(abs(num(row, key)) for row in subset)

    result = {
        "start_update": start,
        "samples": len(window),
        "mean_increment": statistics.fmean(
            num(row, "pressureIncrementRadial") for row in window),
        "pressure_slope": slope(window, "pressureAfterRadial"),
        "mean_abs_raw": mean_abs(window, "rawGradientRadial"),
        "mean_abs_cycle": mean_abs(window, "cycleGradientAfterRadial"),
        "mean_acceleration": statistics.fmean(
            num(row, "accelerationRadial") for row in window),
        "mean_momentum": statistics.fmean(
            num(row, "momentumAfterRadial") for row in window),
        "mean_tangential_pressure": statistics.fmean(
            num(row, "pressureAfterPlanarTangentialMagnitude") for row in window),
        "pressure_accumulation":
            num(window[-1], "pressureAfterRadial") -
            num(window[0], "pressureBeforeRadial"),
    }
    result["first_half_slope"] = slope(halves[0], "pressureAfterRadial")
    result["second_half_slope"] = slope(halves[1], "pressureAfterRadial")
    result["half_slope_relative_difference"] = relative(
        result["first_half_slope"], result["second_half_slope"])
    result["first_half_mean_cycle"] = mean_abs(
        halves[0], "cycleGradientAfterRadial")
    result["second_half_mean_cycle"] = mean_abs(
        halves[1], "cycleGradientAfterRadial")
    return result


def relative(a, b):
    denominator = (abs(a) + abs(b)) / 2
    return abs(abs(a) - abs(b)) / denominator if denominator else 0.0


def normalized(items, key):
    axis = (abs(items["b00"][key]) + abs(items["b90"][key])) / 2
    return {tag: abs(items[tag][key]) / axis for tag in TAGS}


def print_mapping(title, mapping):
    print(f"\n{title}")
    for key, item in mapping.items():
        print(f"  {key}: {item}")


def main():
    parser = argparse.ArgumentParser()
    for tag in TAGS:
        parser.add_argument(f"--{tag}-on", required=True)
        parser.add_argument(f"--{tag}-off", required=True)
    args = vars(parser.parse_args())
    print("Numerical zero tolerance:", ZERO_TOLERANCE)
    print("Approximately-unchanged ratio tolerance:",
          UNCHANGED_RATIO_TOLERANCE)

    rows = {key: load(key, path, 1500) for key, path in args.items()}
    metrics, cycles, windows = {}, {}, {}
    for key, run_rows in rows.items():
        metrics[key], cycles[key] = base_metrics(run_rows)
        print_mapping(key, metrics[key])
    for tag in TAGS:
        for field in CONFIG_FIELDS:
            assert rows[tag + "_on"][0][field] == rows[tag + "_off"][0][field]
        phase_difference = max(
            abs(num(on, "receiverPhaseZ") - num(off, "receiverPhaseZ"))
            for on, off in zip(rows[tag + "_on"], rows[tag + "_off"]))
        print(f"\n{tag} pair phase maximum difference: {phase_difference}")
        print(f"{tag} cycle schedules equal: "
              f"{metrics[tag + '_on']['cycle_updates'] == metrics[tag + '_off']['cycle_updates']}")
        windows[tag] = window_metrics(rows[tag + "_on"], cycles[tag + "_on"])
        print_mapping(tag + " stabilized window", windows[tag])

    stage_values = {}
    for tag in TAGS:
        base = metrics[tag + "_on"]
        window = windows[tag]
        stage_values[tag] = {
            "max_raw": base["max_raw_radial"],
            "stabilized_raw": window["mean_abs_raw"],
            "final_cycle": base["final_cycle_radial"],
            "stabilized_cycle": window["mean_abs_cycle"],
            "stabilized_acceleration": window["mean_acceleration"],
            "stabilized_momentum": window["mean_momentum"],
            "stabilized_pressure_slope": window["pressure_slope"],
            "final_pressure": base["final_pressure"],
        }
    print("\nRadius-60 angular ratios")
    ratio_sets = {}
    for stage in next(iter(stage_values.values())):
        ratio_sets[stage] = normalized(stage_values, stage)
        print(stage + ": " + str(ratio_sets[stage]))

    print("\nRadius-60 symmetry")
    for first, second in (("b00", "b90"), ("b30", "b60")):
        print(first + " versus " + second)
        fields = {
            "final_pressure": (
                metrics[first + "_on"]["final_pressure"],
                metrics[second + "_on"]["final_pressure"]),
            "stabilized_pressure_slope": (
                windows[first]["pressure_slope"], windows[second]["pressure_slope"]),
            "stabilized_mean_increment": (
                windows[first]["mean_increment"], windows[second]["mean_increment"]),
            "max_raw": (
                metrics[first + "_on"]["max_raw_radial"],
                metrics[second + "_on"]["max_raw_radial"]),
            "final_cycle": (
                metrics[first + "_on"]["final_cycle_radial"],
                metrics[second + "_on"]["final_cycle_radial"]),
            "max_tangential_pressure": (
                metrics[first + "_on"]["max_tangential_pressure"],
                metrics[second + "_on"]["max_tangential_pressure"]),
        }
        for name, pair in fields.items():
            print(f"  {name} relative difference: {relative(*pair)}")
        print("  cycle count difference:",
              metrics[first + "_on"]["cycles"] -
              metrics[second + "_on"]["cycles"])
        print("  cycle update differences:",
              [a - b for a, b in zip(
                  metrics[first + "_on"]["cycle_updates"],
                  metrics[second + "_on"]["cycle_updates"])])

    print("\nTangential response")
    for tag in TAGS:
        item = metrics[tag + "_on"]
        radial_cycle = abs(item["final_cycle_radial"])
        radial_pressure = abs(item["final_pressure"])
        print(tag, {
            "final_cycle_radial": item["final_cycle_radial"],
            "final_cycle_tangential": item["final_cycle_tangential"],
            "cycle_tangential_to_radial":
                item["final_cycle_tangential"] / radial_cycle,
            "max_tangential_pressure": item["max_tangential_pressure"],
            "pressure_tangential_to_radial":
                item["max_tangential_pressure"] / radial_pressure,
        })

    a_files = {
        tag: Path("measurements", "gravity-test-2a") /
             f"gravity-test-2a-{tag}-source-on-run-001.csv"
        for tag in A_TAGS
    }
    a_metrics = {
        tag: base_metrics(load(tag, path, 500))[0]
        for tag, path in a_files.items()
    }
    comparison_stages = {
        "raw": "max_raw_radial",
        "cycle": "max_cycle_radial",
        "acceleration": "max_acceleration_radial",
        "pressure_slope": "pressure_slope",
        "final_pressure": "final_pressure",
    }
    print("\nRadius-20 versus radius-60 ratios")
    for stage, key in comparison_stages.items():
        a_axis = (abs(a_metrics["a00"][key]) + abs(a_metrics["a90"][key])) / 2
        b_axis = (abs(metrics["b00_on"][key]) +
                  abs(metrics["b90_on"][key])) / 2
        print(stage)
        for a_tag, b_tag in zip(A_TAGS, TAGS):
            ratio20 = abs(a_metrics[a_tag][key]) / a_axis
            ratio60 = abs(metrics[b_tag + "_on"][key]) / b_axis
            difference = abs(ratio60 - ratio20)
            if difference <= UNCHANGED_RATIO_TOLERANCE:
                direction = "approximately unchanged"
            elif abs(1 - ratio60) < abs(1 - ratio20):
                direction = "moved toward 1"
            else:
                direction = "moved farther from 1"
            print(f"  {b_tag}: r20={ratio20}, r60={ratio60}, {direction}")

    gates = {
        "all runs complete cycles": all(item["cycles"] > 0
                                         for item in metrics.values()),
        "source-on cycle gradients": all(
            metrics[tag + "_on"]["first_cycle"] is not None for tag in TAGS),
        "source-on pressure": all(
            metrics[tag + "_on"]["final_pressure"] != 0 for tag in TAGS),
        "no clamping": all(item["clamped"] == 0 for item in metrics.values()),
        "no thresholds": all(item["threshold"] == 0 for item in metrics.values()),
    }
    print_mapping("Acceptance gates", gates)
    if not all(gates.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
