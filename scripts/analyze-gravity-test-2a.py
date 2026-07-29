#!/usr/bin/env python3
"""Validate and analyze the ten Gravity Test 2A angular runs."""

import argparse
import csv
import math
import statistics

ZERO_TOLERANCE = 1.0e-30
ANGLES = ("a00", "a30", "a45", "a60", "a90")
ANGLE_LABELS = {"a00": 0.0, "a30": 30.4655449195, "a45": 45.0,
                "a60": 59.5344550805, "a90": 90.0}
CONFIGURATION_FIELDS = (
    "receiverInitialNodeX", "receiverInitialNodeY", "receiverInitialNodeZ",
    "receiverOffsetX", "receiverOffsetY", "receiverOffsetZ",
    "receiverEuclideanRadius", "receiverPlanarAngleDegrees",
    "receiverManhattanDistance", "receiverActiveX", "receiverActiveY",
    "receiverActiveZ", "receiverAmplitudeX", "receiverAmplitudeY",
    "receiverAmplitudeZ", "receiverAmplitudeLength",
    "receiverPhaseIncrementZ", "gradientGain", "accelerationGain",
    "transportMomentumGain", "transportMomentumMax", "transferThreshold",
)


def value(row, name):
    return float(row[name])


def boolean(row, name):
    return row[name].lower() == "true"


def first_nonzero(rows, column):
    for row in rows:
        if abs(value(row, column)) > ZERO_TOLERANCE:
            return int(row["updateNumber"])
    return None


def slope(rows, column):
    xs = [value(row, "updateNumber") for row in rows]
    ys = [value(row, column) for row in rows]
    mx, my = statistics.fmean(xs), statistics.fmean(ys)
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sum(
        (x - mx) ** 2 for x in xs)


def load(label, filename):
    with open(filename, newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    assert len(rows) == 500, f"{label}: row count {len(rows)}"
    assert [int(row["updateNumber"]) for row in rows] == list(range(1, 501)), (
        f"{label}: update sequence")
    assert not any(boolean(row, "nodeMoved") for row in rows), f"{label}: movement"
    assert not any(boolean(row, "transferOccurred") for row in rows), f"{label}: transfer"
    assert all(boolean(row, "transferSuppressed") for row in rows), (
        f"{label}: suppression")
    for index, row in enumerate(rows):
        for key, item in row.items():
            if key in {"runId", "experimentName", "receiverConfiguration",
                       "receiverName", "dominantThresholdAxis"}:
                continue
            if item.lower() in {"true", "false"}:
                continue
            assert math.isfinite(float(item)), f"{label}: {key} non-finite"
        for axis in "XYZ":
            before = value(row, "pressureBefore" + axis)
            increment = value(row, "pressureIncrement" + axis)
            after = value(row, "pressureAfter" + axis)
            assert abs(before + increment - after) <= ZERO_TOLERANCE
            assert increment == value(row, "momentumAfter" + axis)
            if index:
                assert before == value(rows[index - 1], "pressureAfter" + axis)
    return rows


def metrics(rows):
    increments = [value(row, "pressureIncrementRadial") for row in rows]
    cycle_rows = [row for row in rows if boolean(row, "cycleCompleted")]
    result = {
        "radius": value(rows[0], "receiverEuclideanRadius"),
        "angle": value(rows[0], "receiverPlanarAngleDegrees"),
        "manhattan": int(value(rows[0], "receiverManhattanDistance")),
        "cycles": len(cycle_rows),
        "cycle_updates": [int(row["updateNumber"]) for row in cycle_rows],
        "first_raw_radial": first_nonzero(rows, "rawGradientRadial"),
        "first_cycle_radial": first_nonzero(rows, "cycleGradientAfterRadial"),
        "first_acceleration_radial": first_nonzero(rows, "accelerationRadial"),
        "first_pressure_increment_radial":
            first_nonzero(rows, "pressureIncrementRadial"),
        "final_pressure_radial": value(rows[-1], "pressureAfterRadial"),
        "pressure_slope": slope(rows, "pressureAfterRadial"),
        "mean_increment": statistics.fmean(increments),
        "median_increment": statistics.median(increments),
        "min_increment": min(increments),
        "max_increment": max(increments),
        "inward": sum(item < -ZERO_TOLERANCE for item in increments),
        "outward": sum(item > ZERO_TOLERANCE for item in increments),
        "near_zero": sum(abs(item) <= ZERO_TOLERANCE for item in increments),
        "exact_zero": sum(item == 0.0 for item in increments),
        "max_raw_radial": max(abs(value(row, "rawGradientRadial")) for row in rows),
        "max_cycle_radial": max(
            abs(value(row, "cycleGradientAfterRadial")) for row in rows),
        "max_cycle_tangential": max(
            value(row, "cycleGradientAfterPlanarTangentialMagnitude")
            for row in rows),
        "max_acceleration_radial": max(
            abs(value(row, "accelerationRadial")) for row in rows),
        "max_momentum_radial": max(
            abs(value(row, "momentumAfterRadial")) for row in rows),
        "max_pressure_tangential": max(
            value(row, "pressureAfterPlanarTangentialMagnitude") for row in rows),
        "clamped": sum(boolean(row, "momentumClampedAny") for row in rows),
        "threshold": sum(boolean(row, "thresholdCrossedAny") for row in rows),
    }
    return result, cycle_rows


def relative(a, b):
    mean = (abs(a) + abs(b)) / 2
    return abs(abs(a) - abs(b)) / mean if mean else 0.0


def compare_pair(tag, on_rows, off_rows, on, off):
    for field in CONFIGURATION_FIELDS:
        assert on_rows[0][field] == off_rows[0][field], (
            f"{tag}: receiver configuration differs: {field}")
    phase_delta = max(abs(value(a, "receiverPhaseZ") -
                          value(b, "receiverPhaseZ"))
                      for a, b in zip(on_rows, off_rows))
    cycle_aligned = on["cycle_updates"] == off["cycle_updates"]
    diagnostics = {}
    for column in ("rawGradientRadial", "cycleGradientAfterRadial",
                   "accelerationRadial", "momentumAfterRadial",
                   "pressureIncrementRadial", "pressureAfterRadial"):
        differences = [value(a, column) - value(b, column)
                       for a, b in zip(on_rows, off_rows)]
        diagnostics[column] = {
            "final": differences[-1],
            "mean": statistics.fmean(differences),
            "min": min(differences),
            "max": max(differences),
        }
    on_cycles = {int(row["completedCycleCount"]): row
                 for row in on_rows if boolean(row, "cycleCompleted")}
    off_cycles = {int(row["completedCycleCount"]): row
                  for row in off_rows if boolean(row, "cycleCompleted")}
    cycle_differences = [
        (cycle, int(on_cycles[cycle]["updateNumber"]),
         int(off_cycles[cycle]["updateNumber"]),
         value(on_cycles[cycle], "cycleGradientAfterRadial") -
         value(off_cycles[cycle], "cycleGradientAfterRadial"))
        for cycle in sorted(on_cycles.keys() & off_cycles.keys())
    ]
    return {
        "cycle_aligned": cycle_aligned,
        "phase_delta": phase_delta,
        "diagnostics": diagnostics,
        "cycle_differences": cycle_differences,
        "delta_final_pressure":
            on["final_pressure_radial"] - off["final_pressure_radial"],
        "delta_slope": on["pressure_slope"] - off["pressure_slope"],
        "delta_mean_increment": on["mean_increment"] - off["mean_increment"],
        "delta_max_cycle_radial":
            on["max_cycle_radial"] - off["max_cycle_radial"],
    }


def print_metrics(label, item):
    print(f"\n{label}")
    for key, item_value in item.items():
        print(f"  {key}: {item_value}")


def print_cycle_table(tag, rows):
    print(f"\n{tag} source-on completed cycles")
    print("cycle,update,phaseZ,cycleGradientRadial,accelerationRadial,"
          "momentumAfterRadial,pressureAfterRadial")
    for row in rows:
        print(",".join((
            row["completedCycleCount"], row["updateNumber"], row["receiverPhaseZ"],
            row["cycleGradientAfterRadial"], row["accelerationRadial"],
            row["momentumAfterRadial"], row["pressureAfterRadial"])))


def main():
    parser = argparse.ArgumentParser()
    for tag in ANGLES:
        parser.add_argument(f"--{tag}-on", required=True)
        parser.add_argument(f"--{tag}-off", required=True)
    args = vars(parser.parse_args())
    print("Numerical zero tolerance:", ZERO_TOLERANCE)
    rows = {key: load(key, filename) for key, filename in args.items()}
    summaries, cycles = {}, {}
    for key, run_rows in rows.items():
        summaries[key], cycles[key] = metrics(run_rows)
        print_metrics(key, summaries[key])

    comparisons = {}
    for tag in ANGLES:
        comparisons[tag] = compare_pair(
            tag, rows[tag + "_on"], rows[tag + "_off"],
            summaries[tag + "_on"], summaries[tag + "_off"])
        print_metrics(tag + " source-on-minus-source-off diagnostic",
                      comparisons[tag])
        print_cycle_table(tag, cycles[tag + "_on"])

    magnitudes = {
        tag: abs(comparisons[tag]["delta_final_pressure"]) for tag in ANGLES}
    axis_mean = (magnitudes["a00"] + magnitudes["a90"]) / 2
    print("\nAngular summary")
    print("tag,angle,radius,manhattan,deltaFinalPressure,magnitude,"
          "deltaSlope,ratioToAxisMean,absoluteRelativeDifference")
    for tag in ANGLES:
        ratio = magnitudes[tag] / axis_mean
        print(f"{tag},{ANGLE_LABELS[tag]},"
              f"{summaries[tag + '_on']['radius']},"
              f"{summaries[tag + '_on']['manhattan']},"
              f"{comparisons[tag]['delta_final_pressure']},{magnitudes[tag]},"
              f"{comparisons[tag]['delta_slope']},{ratio},{abs(ratio - 1)}")

    print("\nCubic symmetry")
    for first, second in (("a00", "a90"), ("a30", "a60")):
        print(f"{first} versus {second}")
        for field in ("delta_final_pressure", "delta_slope",
                      "delta_mean_increment", "delta_max_cycle_radial"):
            print(f"  {field} relative difference: "
                  f"{relative(comparisons[first][field], comparisons[second][field])}")
        print("  completed-cycle-count difference:",
              summaries[first + "_on"]["cycles"] -
              summaries[second + "_on"]["cycles"])
        first_updates = summaries[first + "_on"]["cycle_updates"]
        second_updates = summaries[second + "_on"]["cycle_updates"]
        print("  cycle-update differences:",
              [a - b for a, b in zip(first_updates, second_updates)])

    gates = {
        "responsive cycles": all(summaries[tag + "_on"]["cycles"] > 0 and
                                 summaries[tag + "_off"]["cycles"] > 0
                                 for tag in ANGLES),
        "source-on cycle gradient": all(
            summaries[tag + "_on"]["first_cycle_radial"] is not None
            for tag in ANGLES),
        "source-on pressure": all(
            summaries[tag + "_on"]["final_pressure_radial"] != 0
            for tag in ANGLES),
        "no clamping": all(item["clamped"] == 0 for item in summaries.values()),
        "no threshold crossings": all(
            item["threshold"] == 0 for item in summaries.values()),
    }
    print("\nAcceptance gates")
    for name, passed in gates.items():
        print(f"  {name}: {'PASS' if passed else 'FAIL'}")
    if not all(gates.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
