#!/usr/bin/env python3
"""Analyze matched post-arrival cycles for Gravity Test 2C."""

import csv
import glob
import math
import statistics
from pathlib import Path

RADII = (20, 40, 60, 80, 100, 120)
ANGLES = (0, 30, 45, 60, 90)
UPDATES = 960
CYCLES_REQUIRED = 30
ABSOLUTE_ARRIVAL_FLOOR = 1.0e-15
NOISE_MULTIPLIER = 100.0
SYMMETRY_REL_TOLERANCE = 1.0e-8
SYMMETRY_ABS_TOLERANCE = 1.0e-18
SOURCE = (600, 600, 0)
PHYSICAL_REFLECTION_UPDATE = 1080
ABSORPTION_ZONE_RETURN_UPDATE = 1020

MEASUREMENTS = Path("measurements")
SUMMARY_PATH = MEASUREMENTS / "gravity-test-2c-summary.csv"
CYCLE_PATH = MEASUREMENTS / "gravity-test-2c-cycle-metrics.csv"
ANALYSIS_PATH = MEASUREMENTS / "gravity-test-2c-analysis.txt"


def f(row, key):
    return float(row[key])


def b(row, key):
    return row[key].lower() == "true"


def vector(row, prefix):
    return tuple(f(row, prefix + axis) for axis in "XYZ")


def dot(a, b_value):
    return sum(x * y for x, y in zip(a, b_value))


def subtract(a, b_value):
    return tuple(x - y for x, y in zip(a, b_value))


def rms(values):
    values = list(values)
    return math.sqrt(statistics.fmean(value * value for value in values))


def find_complete_file(radius, angle, state):
    pattern = str(MEASUREMENTS /
                  f"gravity-test-2c-r{radius:03d}-a{angle:02d}-source-{state}-run-*.csv")
    complete = []
    for filename in sorted(glob.glob(pattern)):
        with open(filename, newline="", encoding="utf-8") as stream:
            rows = list(csv.DictReader(stream))
        if len(rows) == UPDATES:
            complete.append((filename, rows))
    if not complete:
        raise AssertionError(f"No complete file: r={radius} a={angle} {state}")
    return complete[-1]


def find_complete_consolidated_file(state):
    pattern = str(MEASUREMENTS /
                  f"gravity-test-2c-all-receivers-source-{state}-run-*.csv")
    complete = []
    for filename in sorted(glob.glob(pattern)):
        with open(filename, newline="", encoding="utf-8") as stream:
            rows = list(csv.DictReader(stream))
        if len(rows) == UPDATES * len(RADII) * len(ANGLES):
            complete.append((filename, rows))
    if not complete:
        raise AssertionError(f"No complete consolidated source-{state} file")
    return complete[-1]


def validate_rows(label, rows):
    assert len(rows) == UPDATES
    assert [int(row["updateNumber"]) for row in rows] == list(range(1, UPDATES + 1))
    geometry = tuple(rows[0][key] for key in (
        "receiverInitialNodeX", "receiverInitialNodeY", "receiverInitialNodeZ",
        "receiverOffsetX", "receiverOffsetY", "receiverOffsetZ",
        "receiverEuclideanRadius", "receiverPlanarAngleDegrees",
        "receiverManhattanDistance"))
    for index, row in enumerate(rows):
        assert tuple(row[key] for key in (
            "receiverInitialNodeX", "receiverInitialNodeY", "receiverInitialNodeZ",
            "receiverOffsetX", "receiverOffsetY", "receiverOffsetZ",
            "receiverEuclideanRadius", "receiverPlanarAngleDegrees",
            "receiverManhattanDistance")) == geometry
        assert not b(row, "nodeMoved"), f"{label}: movement"
        assert not b(row, "transferOccurred"), f"{label}: transfer"
        assert b(row, "transferSuppressed"), f"{label}: suppression"
        assert not b(row, "momentumClampedAny"), f"{label}: clamp"
        assert not b(row, "thresholdCrossedAny"), f"{label}: threshold"
        for key, value in row.items():
            if key in {"runId", "experimentName", "receiverConfiguration",
                       "receiverName", "dominantThresholdAxis"}:
                continue
            if value.lower() in {"true", "false"}:
                continue
            assert math.isfinite(float(value)), f"{label}: non-finite {key}"
        for axis in "XYZ":
            before = f(row, "pressureBefore" + axis)
            increment = f(row, "pressureIncrement" + axis)
            after = f(row, "pressureAfter" + axis)
            assert abs(before + increment - after) <= 1.0e-30
            assert increment == f(row, "momentumAfter" + axis)
            if index:
                assert before == f(rows[index - 1], "pressureAfter" + axis)


def project_pair_check(radius, angle):
    on_path = Path("samples") / (
        f"gravity-test-2c-r{radius:03d}-a{angle:02d}-source-on.qfs")
    off_path = Path("samples") / (
        f"gravity-test-2c-r{radius:03d}-a{angle:02d}-source-off.qfs")
    on_lines = on_path.read_text(encoding="utf-8").splitlines()
    off_lines = off_path.read_text(encoding="utf-8").splitlines()
    differences = [(a, b_value) for a, b_value in zip(on_lines, off_lines)
                   if a != b_value]
    assert differences == [("amplitude.z = 200.0", "amplitude.z = 0.0")]


def consolidated_project_pair_check():
    on_path = Path("samples/gravity-test-2c-all-receivers-source-on.qfs")
    off_path = Path("samples/gravity-test-2c-all-receivers-source-off.qfs")
    on_lines = on_path.read_text(encoding="utf-8").splitlines()
    off_lines = off_path.read_text(encoding="utf-8").splitlines()
    differences = [(a, b_value) for a, b_value in zip(on_lines, off_lines)
                   if a != b_value]
    assert differences == [("amplitude.z = 200.0", "amplitude.z = 0.0")]


def basis(row):
    displacement = (f(row, "receiverOffsetX"), f(row, "receiverOffsetY"), 0.0)
    length = math.hypot(displacement[0], displacement[1])
    radial = (displacement[0] / length, displacement[1] / length, 0.0)
    tangent = (-radial[1], radial[0], 0.0)
    return radial, tangent


def cycle_noise(rows, radial, tangent):
    cycle_rows = [row for row in rows if b(row, "cycleCompleted")]
    radial_values = [dot(vector(row, "cycleGradientAfter"), radial)
                     for row in cycle_rows]
    tangential_values = [dot(vector(row, "cycleGradientAfter"), tangent)
                         for row in cycle_rows]
    return {
        "raw_rms": rms(dot(vector(row, "rawGradient"), radial) for row in cycle_rows),
        "cycle_rms": rms(radial_values),
        "cycle_tangential_rms": rms(tangential_values),
        "acceleration_rms": rms(
            dot(vector(row, "acceleration"), radial) for row in cycle_rows),
        "pressure_cumulative": dot(vector(rows[-1], "pressureAfter"), radial),
    }


def matched_cycles(on_rows, off_rows):
    radial, tangent = basis(on_rows[0])
    noise = cycle_noise(off_rows, radial, tangent)
    threshold = max(NOISE_MULTIPLIER * noise["cycle_rms"],
                    ABSOLUTE_ARRIVAL_FLOOR)
    completed = [(index, row) for index, row in enumerate(on_rows)
                 if b(row, "cycleCompleted")]
    arrival_position = next(
        position for position, (_, row) in enumerate(completed)
        if abs(dot(vector(row, "cycleGradientAfter"), radial)) > threshold)
    chosen = completed[arrival_position:arrival_position + CYCLES_REQUIRED]
    assert len(chosen) == CYCLES_REQUIRED
    cycle_data = []
    previous_completion_index = (
        completed[arrival_position - 1][0] if arrival_position else -1)
    for cycle_index, (row_index, row) in enumerate(chosen, 1):
        interval = on_rows[previous_completion_index + 1:row_index + 1]
        momentum_before = vector(interval[0], "momentumBefore")
        momentum_after = vector(interval[-1], "momentumAfter")
        pressure_before = vector(interval[0], "pressureBefore")
        pressure_after = vector(interval[-1], "pressureAfter")
        momentum_change = subtract(momentum_after, momentum_before)
        pressure_increment = subtract(pressure_after, pressure_before)
        raw = vector(row, "rawGradient")
        cycle_gradient = vector(row, "cycleGradientAfter")
        acceleration = vector(row, "acceleration")
        cycle_data.append({
            "update": int(row["updateNumber"]),
            "cycle_index": cycle_index,
            "raw": raw,
            "cycle_gradient": cycle_gradient,
            "acceleration": acceleration,
            "momentum_before": momentum_before,
            "momentum_after": momentum_after,
            "momentum_change": momentum_change,
            "pressure_before": pressure_before,
            "pressure_after": pressure_after,
            "pressure_increment": pressure_increment,
            "raw_radial": dot(raw, radial),
            "raw_tangential": dot(raw, tangent),
            "cycle_radial": dot(cycle_gradient, radial),
            "cycle_tangential": dot(cycle_gradient, tangent),
            "acceleration_radial": dot(acceleration, radial),
            "acceleration_tangential": dot(acceleration, tangent),
            "momentum_change_radial": dot(momentum_change, radial),
            "momentum_change_tangential": dot(momentum_change, tangent),
            "pressure_increment_radial": dot(pressure_increment, radial),
            "pressure_increment_tangential": dot(pressure_increment, tangent),
        })
        previous_completion_index = row_index
    return threshold, noise, cycle_data


def summarize(cycles, noise, threshold, first_row, last_row):
    radial_raw = [item["raw_radial"] for item in cycles]
    radial_cycle = [item["cycle_radial"] for item in cycles]
    radial_acceleration = [item["acceleration_radial"] for item in cycles]
    radial_momentum = [item["momentum_change_radial"] for item in cycles]
    radial_pressure = [item["pressure_increment_radial"] for item in cycles]
    tangential_cycle = [item["cycle_tangential"] for item in cycles]
    tangential_momentum = [item["momentum_change_tangential"] for item in cycles]
    tangential_pressure = [item["pressure_increment_tangential"] for item in cycles]
    return {
        "actualRadius": f(first_row, "receiverEuclideanRadius"),
        "actualAngleDegrees": f(first_row, "receiverPlanarAngleDegrees"),
        "manhattanDistance": int(f(first_row, "receiverManhattanDistance")),
        "receiverX": int(f(first_row, "receiverInitialNodeX")),
        "receiverY": int(f(first_row, "receiverInitialNodeY")),
        "arrivalThreshold": threshold,
        "arrivalUpdate": cycles[0]["update"],
        "windowEndUpdate": cycles[-1]["update"],
        "peakAbsRadialRawGradient": max(abs(value) for value in radial_raw),
        "rmsRadialRawGradient": rms(radial_raw),
        "meanSignedRadialCycleGradient": statistics.fmean(radial_cycle),
        "rmsRadialCycleGradient": rms(radial_cycle),
        "meanSignedRadialAcceleration": statistics.fmean(radial_acceleration),
        "rmsRadialAcceleration": rms(radial_acceleration),
        "meanRadialMomentumChangePerCycle": statistics.fmean(radial_momentum),
        "cumulativeRadialMomentumChange": sum(radial_momentum),
        "meanRadialPressureIncrementPerCycle": statistics.fmean(radial_pressure),
        "cumulativeRadialPressureIncrement": sum(radial_pressure),
        "meanSignedTangentialCycleGradient": statistics.fmean(tangential_cycle),
        "rmsTangentialCycleGradient": rms(tangential_cycle),
        "cumulativeTangentialMomentumChange": sum(tangential_momentum),
        "cumulativeTangentialPressureIncrement": sum(tangential_pressure),
        "lifetimeFinalRadialPressure": f(last_row, "pressureAfterRadial"),
        "sourceOffRmsRawGradient": noise["raw_rms"],
        "sourceOffRmsCycleGradient": noise["cycle_rms"],
        "sourceOffRmsAcceleration": noise["acceleration_rms"],
        "sourceOffCumulativePressure": noise["pressure_cumulative"],
        "cycleSignalToNoise": rms(radial_cycle) / noise["cycle_rms"]
        if noise["cycle_rms"] else math.inf,
        "tangentialToRadialCycleRms":
            rms(tangential_cycle) / rms(radial_cycle),
    }


PRIMARY_RATIO_METRICS = {
    "rawGradient": "rmsRadialRawGradient",
    "cycleGradient": "rmsRadialCycleGradient",
    "acceleration": "rmsRadialAcceleration",
    "momentumChange": "cumulativeRadialMomentumChange",
    "pressureIncrement": "cumulativeRadialPressureIncrement",
}


def ratios_by_radius(summaries):
    results = {}
    for radius in RADII:
        axis = summaries[(radius, 0)]
        results[radius] = {}
        for metric, key in PRIMARY_RATIO_METRICS.items():
            denominator = abs(axis[key])
            values = {angle: abs(summaries[(radius, angle)][key]) / denominator
                      for angle in ANGLES}
            error = math.sqrt(statistics.fmean(
                (values[angle] - 1.0) ** 2 for angle in ANGLES if angle))
            results[radius][metric] = (values, error)
    return results


def symmetry_difference(a, b_value):
    absolute = abs(a - b_value)
    scale = (abs(a) + abs(b_value)) / 2
    relative = absolute / scale if scale else 0.0
    passed = absolute <= SYMMETRY_ABS_TOLERANCE or relative <= SYMMETRY_REL_TOLERANCE
    return absolute, relative, passed


def fit_models(points):
    xs = [point[0] for point in points]
    ys = [abs(point[1]) for point in points]

    def fixed(power):
        weights = [x ** (-power) for x in xs]
        coefficient = sum(y * w for y, w in zip(ys, weights)) / sum(
            w * w for w in weights)
        predictions = [coefficient * w for w in weights]
        error = rms(y - prediction for y, prediction in zip(ys, predictions))
        return coefficient, error

    positive = [(x, y) for x, y in zip(xs, ys) if y > 0]
    lx = [math.log(x) for x, _ in positive]
    ly = [math.log(y) for _, y in positive]
    mx, my = statistics.fmean(lx), statistics.fmean(ly)
    slope_value = sum((x - mx) * (y - my) for x, y in zip(lx, ly)) / sum(
        (x - mx) ** 2 for x in lx)
    exponent = -slope_value
    coefficient = math.exp(my + exponent * mx)
    predictions = [coefficient / (x ** exponent) for x in xs]
    error = rms(y - prediction for y, prediction in zip(ys, predictions))
    return {"inverseR": fixed(1), "inverseR2": fixed(2),
            "freePower": (coefficient, exponent, error)}


def convergence(values):
    distances = [abs(value - 1.0) for value in values]
    if all(b_value <= a + 1.0e-12 for a, b_value in zip(distances, distances[1:])):
        return "moves monotonically toward 1"
    if distances[-1] < distances[0] and sum(
            b_value < a for a, b_value in zip(distances, distances[1:])) >= 3:
        return "moves generally toward 1 but oscillates"
    if max(values) - min(values) <= 0.05:
        return "remains approximately constant"
    if distances[-1] > distances[0] and sum(
            b_value > a for a, b_value in zip(distances, distances[1:])) >= 3:
        return "moves away from 1"
    return "shows no clear trend"


def write_csv_outputs(summaries, cycle_rows):
    summary_fields = ["requestedRadius", "requestedAngle"] + list(
        next(iter(summaries.values())).keys())
    with SUMMARY_PATH.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=summary_fields)
        writer.writeheader()
        for (radius, angle), values in sorted(summaries.items()):
            writer.writerow({"requestedRadius": radius, "requestedAngle": angle,
                             **values})
    fields = [
        "requestedRadius", "requestedAngle", "update", "cycle_index",
        "rawGradientX", "rawGradientY", "rawGradientZ",
        "cycleGradientX", "cycleGradientY", "cycleGradientZ",
        "accelerationX", "accelerationY", "accelerationZ",
        "momentumBeforeX", "momentumBeforeY", "momentumBeforeZ",
        "momentumAfterX", "momentumAfterY", "momentumAfterZ",
        "pressureBeforeX", "pressureBeforeY", "pressureBeforeZ",
        "pressureAfterX", "pressureAfterY", "pressureAfterZ",
        "rawRadial", "rawTangential", "cycleRadial", "cycleTangential",
        "accelerationRadial", "accelerationTangential",
        "momentumChangeRadial", "momentumChangeTangential",
        "pressureIncrementRadial", "pressureIncrementTangential",
    ]
    with CYCLE_PATH.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for radius, angle, item in cycle_rows:
            row = {"requestedRadius": radius, "requestedAngle": angle,
                   "update": item["update"], "cycle_index": item["cycle_index"]}
            for name, source in (
                    ("rawGradient", item["raw"]),
                    ("cycleGradient", item["cycle_gradient"]),
                    ("acceleration", item["acceleration"]),
                    ("momentumBefore", item["momentum_before"]),
                    ("momentumAfter", item["momentum_after"]),
                    ("pressureBefore", item["pressure_before"]),
                    ("pressureAfter", item["pressure_after"])):
                for axis, value in zip("XYZ", source):
                    row[name + axis] = value
            for output, source in (
                    ("rawRadial", "raw_radial"),
                    ("rawTangential", "raw_tangential"),
                    ("cycleRadial", "cycle_radial"),
                    ("cycleTangential", "cycle_tangential"),
                    ("accelerationRadial", "acceleration_radial"),
                    ("accelerationTangential", "acceleration_tangential"),
                    ("momentumChangeRadial", "momentum_change_radial"),
                    ("momentumChangeTangential", "momentum_change_tangential"),
                    ("pressureIncrementRadial", "pressure_increment_radial"),
                    ("pressureIncrementTangential",
                     "pressure_increment_tangential")):
                row[output] = item[source]
            writer.writerow(row)


def svg_plot(filename, title, x_values, series, log_x=False, log_y=False):
    width, height, margin = 900, 560, 70
    colors = ("#2563eb", "#dc2626", "#16a34a", "#9333ea", "#ea580c")
    transform_x = math.log10 if log_x else lambda value: value
    transform_y = math.log10 if log_y else lambda value: value
    tx = [transform_x(value) for value in x_values]
    all_y = [transform_y(abs(value) if log_y else value)
             for values in series.values() for value in values
             if not log_y or value != 0]
    xmin, xmax = min(tx), max(tx)
    ymin, ymax = min(all_y), max(all_y)
    if ymax == ymin:
        ymax += 1
    def px(value):
        return margin + (transform_x(value) - xmin) / (xmax - xmin) * (
            width - 2 * margin)
    def py(value):
        transformed = transform_y(abs(value) if log_y else value)
        return height - margin - (transformed - ymin) / (ymax - ymin) * (
            height - 2 * margin)
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">',
        '<rect width="100%" height="100%" fill="white"/>',
        f'<text x="{width/2}" y="30" text-anchor="middle" '
        f'font-family="sans-serif" font-size="18">{title}</text>',
        f'<line x1="{margin}" y1="{height-margin}" x2="{width-margin}" '
        f'y2="{height-margin}" stroke="black"/>',
        f'<line x1="{margin}" y1="{margin}" x2="{margin}" '
        f'y2="{height-margin}" stroke="black"/>',
    ]
    for index, (label, values) in enumerate(series.items()):
        points = " ".join(f"{px(x)},{py(y)}" for x, y in zip(x_values, values))
        color = colors[index % len(colors)]
        parts.append(f'<polyline points="{points}" fill="none" '
                     f'stroke="{color}" stroke-width="2"/>')
        parts.append(f'<text x="{width-180}" y="{60+20*index}" '
                     f'font-family="sans-serif" font-size="13" fill="{color}">'
                     f'{label}</text>')
    parts.append('</svg>')
    Path(filename).write_text("\n".join(parts), encoding="utf-8")


def write_plots(summaries, ratios):
    for metric, filename, title in (
            ("cycleGradient", "gravity-test-2c-cycle-ratios.svg",
             "Cycle-gradient angular ratios"),
            ("acceleration", "gravity-test-2c-acceleration-ratios.svg",
             "Acceleration angular ratios"),
            ("pressureIncrement", "gravity-test-2c-pressure-ratios.svg",
             "Pressure-increment angular ratios")):
        svg_plot(MEASUREMENTS / filename, title, RADII, {
            f"A{angle:02d}": [ratios[radius][metric][0][angle]
                              for radius in RADII]
            for angle in ANGLES if angle})
    svg_plot(MEASUREMENTS / "gravity-test-2c-isotropy-error.svg",
             "Isotropy error versus radius", RADII, {
                 metric: [ratios[radius][metric][1] for radius in RADII]
                 for metric in PRIMARY_RATIO_METRICS})
    svg_plot(MEASUREMENTS / "gravity-test-2c-tangential-ratio.svg",
             "Tangential-to-radial cycle RMS", RADII, {
                 f"A{angle:02d}": [
                     summaries[(radius, angle)]["tangentialToRadialCycleRms"]
                     for radius in RADII]
                 for angle in ANGLES})
    svg_plot(MEASUREMENTS / "gravity-test-2c-radial-loglog.svg",
             "Radial cycle RMS versus radius", RADII, {
                 f"A{angle:02d}": [
                     summaries[(radius, angle)]["rmsRadialCycleGradient"]
                     for radius in RADII]
                 for angle in ANGLES}, log_x=True, log_y=True)


def main():
    summaries = {}
    all_cycle_rows = []
    selected_files = {}
    consolidated_project_pair_check()
    on_file, all_on_rows = find_complete_consolidated_file("on")
    off_file, all_off_rows = find_complete_consolidated_file("off")
    on_by_receiver = {}
    off_by_receiver = {}
    for row in all_on_rows:
        on_by_receiver.setdefault(row["receiverName"], []).append(row)
    for row in all_off_rows:
        off_by_receiver.setdefault(row["receiverName"], []).append(row)
    assert set(on_by_receiver) == set(off_by_receiver)
    assert len(on_by_receiver) == len(RADII) * len(ANGLES)
    for radius in RADII:
        for angle in ANGLES:
            receiver_name = f"R{radius:03d}_A{angle:02d}"
            on_rows = on_by_receiver[receiver_name]
            off_rows = off_by_receiver[receiver_name]
            selected_files[(radius, angle)] = (on_file, off_file)
            validate_rows(on_file, on_rows)
            validate_rows(off_file, off_rows)
            threshold, noise, cycles = matched_cycles(on_rows, off_rows)
            summary = summarize(
                cycles, noise, threshold, on_rows[0], on_rows[-1])
            assert summary["windowEndUpdate"] < ABSORPTION_ZONE_RETURN_UPDATE
            assert summary["cycleSignalToNoise"] > NOISE_MULTIPLIER
            summaries[(radius, angle)] = summary
            all_cycle_rows.extend((radius, angle, item) for item in cycles)

    ratios = ratios_by_radius(summaries)
    write_csv_outputs(summaries, all_cycle_rows)
    write_plots(summaries, ratios)

    with ANALYSIS_PATH.open("w", encoding="utf-8") as report:
        def line(value=""):
            print(value, file=report)
        line("Gravity Test 2C: Multi-Radius Isotropy Convergence Test")
        line(f"Arrival threshold = max({NOISE_MULTIPLIER} * source-off cycle RMS, "
             f"{ABSOLUTE_ARRIVAL_FLOOR})")
        line(f"Symmetry tolerances: relative={SYMMETRY_REL_TOLERANCE}, "
             f"absolute={SYMMETRY_ABS_TOLERANCE}")
        line(f"Source={SOURCE}; runtime={UPDATES}; physical reflection="
             f"{PHYSICAL_REFLECTION_UPDATE}; absorption-zone path="
             f"{ABSORPTION_ZONE_RETURN_UPDATE}")
        line("Configuration verification: PASS; every pair differs only by source amplitude.")
        line("\nReceiver coordinates, arrival, noise, and matched-window metrics")
        for key in sorted(summaries):
            line(f"{key}: files={selected_files[key]}")
            for name, value in summaries[key].items():
                line(f"  {name}: {value}")
        line("\nAngular ratios and isotropy errors")
        for radius in RADII:
            line(f"radius {radius}")
            for metric, (values, error) in ratios[radius].items():
                line(f"  {metric}: ratios={values}; E={error}")
        line("\nSymmetry and signed tangential checks")
        symmetry_pass = True
        for radius in RADII:
            for first, second in ((0, 90), (30, 60)):
                for key in PRIMARY_RATIO_METRICS.values():
                    result = symmetry_difference(
                        summaries[(radius, first)][key],
                        summaries[(radius, second)][key])
                    symmetry_pass &= result[2]
                    line(f"r={radius} {first}/{second} {key}: "
                         f"absolute={result[0]}, relative={result[1]}, pass={result[2]}")
            tangent = symmetry_difference(
                summaries[(radius, 30)]["meanSignedTangentialCycleGradient"],
                -summaries[(radius, 60)]["meanSignedTangentialCycleGradient"])
            symmetry_pass &= tangent[2]
            line(f"r={radius} signed tangent A30/-A60: absolute={tangent[0]}, "
                 f"relative={tangent[1]}, pass={tangent[2]}")
        line("\nDistance-scaling diagnostic fits")
        for angle in ANGLES:
            for metric, key in PRIMARY_RATIO_METRICS.items():
                fit = fit_models([
                    (summaries[(radius, angle)]["actualRadius"],
                     summaries[(radius, angle)][key]) for radius in RADII])
                line(f"angle={angle} metric={metric}: {fit}")
        line("\nConvergence assessment")
        isotropy_errors_decrease = {}
        for metric in PRIMARY_RATIO_METRICS:
            errors = [ratios[radius][metric][1] for radius in RADII]
            isotropy_errors_decrease[metric] = convergence(
                [1.0 + error for error in errors])
            line(f"{metric} isotropy errors={errors}; "
                 f"assessment={isotropy_errors_decrease[metric]}")
            for angle in ANGLES[1:]:
                values = [ratios[radius][metric][0][angle] for radius in RADII]
                line(f"  A{angle}: {values}; {convergence(values)}")
        line("\nAcceptance gates")
        gates = {
            "60 complete source-on/off runs": len(summaries) == 30,
            "30 post-arrival cycles each": len(all_cycle_rows) == 900,
            "matched windows before absorption-zone return": all(
                item["windowEndUpdate"] < ABSORPTION_ZONE_RETURN_UPDATE
                for item in summaries.values()),
            "source-on signal above noise": all(
                item["cycleSignalToNoise"] > NOISE_MULTIPLIER
                for item in summaries.values()),
            "symmetry": symmetry_pass,
        }
        for name, passed in gates.items():
            line(f"  {name}: {'PASS' if passed else 'FAIL'}")
        if not all(gates.values()):
            raise SystemExit("Acceptance gate failed; see analysis report")


if __name__ == "__main__":
    main()
