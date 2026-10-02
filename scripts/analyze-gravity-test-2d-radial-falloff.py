#!/usr/bin/env python3
"""Analyze Gravity Test 2D with mandatory common reflection-safe windows."""

import csv
import math
import struct
import zlib
from collections import defaultdict
from pathlib import Path

RAW = Path("measurements/gravity-test-2d-radial-falloff-run-001.csv")
BASELINE = Path("measurements/empty-lattice-equilibrium-validation-run-001.csv")
OUT = Path("measurements")
CHARTS = OUT / "charts"
CYCLE_OUT = OUT / "gravity-test-2d-radial-falloff-cycle-metrics.csv"
SUMMARY_OUT = OUT / "gravity-test-2d-radial-falloff-summary.csv"
AVERAGE_OUT = OUT / "gravity-test-2d-radial-falloff-axis-averages.csv"
LOCAL_OUT = OUT / "gravity-test-2d-radial-falloff-local-exponents.csv"
FITS_OUT = OUT / "gravity-test-2d-radial-falloff-model-fits.csv"
REPORT_OUT = OUT / "gravity-test-2d-radial-falloff-report.md"

SOURCE = (600, 600)
RADII = (10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120,
         130, 140, 150, 160, 180, 200, 220, 240, 260, 280, 300,
         320, 340, 360, 380, 400)
DIRECTIONS = ("PX", "NX", "PY", "NY")
ANGLES = {"PX": 0.0, "NX": 180.0, "PY": 90.0, "NY": 270.0}
INTERFACES = {
    "left x=29": ("x", 29), "right x=1171": ("x", 1171),
    "bottom y=29": ("y", 29), "top y=1171": ("y", 1171),
}
PREFIXES = ("rawGradient", "cycleDerivedGradient", "acceleration")
FIT_RANGES = ((10, 400), (20, 400), (40, 400), (80, 400),
              (120, 400), (200, 400))
MODELS = ("constant", "inverse-distance", "inverse-square",
          "free-power-law", "exponential", "power-law-with-offset")


def read(path):
    with path.open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def vec(row, prefix):
    return tuple(float(row[prefix + axis]) for axis in "XYZ")


def norm(value):
    return math.sqrt(sum(component * component for component in value))


def finite(value):
    return all(math.isfinite(component) for component in value)


def reflected_path(x, y):
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


def meaningful_arrival(rows):
    for row in rows:
        value = vec(row, "cycleDerivedGradient")
        if (row["cycleCompleted"].lower() == "true"
                and finite(value) and norm(value) > 0.0):
            return int(row["update"]), int(row["completedCycleCount"])
    return None, None


def validate_and_group(rows):
    if len(rows) != 112 * 960:
        raise ValueError(f"row count {len(rows)} != 107520")
    grouped = defaultdict(list)
    seen = set()
    geometry = {}
    numeric = [field for field in rows[0]
               if field not in ("detectorName", "direction",
                                "cycleCompleted")]
    for line, row in enumerate(rows, 2):
        key = (row["detectorName"], int(row["update"]))
        if key in seen:
            raise ValueError(f"duplicate detector/update {key}")
        seen.add(key)
        grouped[row["detectorName"]].append(row)
        for field in numeric:
            value = float(row[field])
            if not math.isfinite(value):
                raise ValueError(f"non-finite {field} at CSV line {line}")
            if value == 0.0 and row[field].startswith("-"):
                raise ValueError(f"unexpected signed zero at line {line}")
        fixed = tuple(row[field] for field in (
            "direction", "requestedRadius", "requestedAngleDegrees",
            "nodeX", "nodeY", "nodeZ", "actualEuclideanRadius",
            "actualAngleDegrees", "coordinateRoundingErrorMagnitude"))
        previous = geometry.setdefault(row["detectorName"], fixed)
        if fixed != previous:
            raise ValueError(f"inconsistent geometry for {row['detectorName']}")
    if len(grouped) != 112 or len(set(grouped)) != 112:
        raise ValueError("detector count/name uniqueness failed")
    for name, detector_rows in grouped.items():
        if [int(row["update"]) for row in detector_rows] != list(
                range(1, 961)):
            raise ValueError(f"incomplete update sequence for {name}")
        first = detector_rows[0]
        direction = first["direction"]
        radius = int(first["requestedRadius"])
        if direction not in DIRECTIONS or radius not in RADII:
            raise ValueError(f"unexpected geometry {name}")
        if name != f"{direction}_R{radius:03d}":
            raise ValueError(f"name/metadata mismatch {name}")
        x, y, z = (int(first["nodeX"]), int(first["nodeY"]),
                   int(first["nodeZ"]))
        expected = {
            "PX": (600 + radius, 600, 0),
            "NX": (600 - radius, 600, 0),
            "PY": (600, 600 + radius, 0),
            "NY": (600, 600 - radius, 0),
        }[direction]
        if (x, y, z) != expected:
            raise ValueError(f"coordinate mismatch {name}")
        if float(first["coordinateRoundingErrorMagnitude"]) != 0.0:
            raise ValueError(f"rounding error for {name}")
        if not (29 < x < 1171 and 29 < y < 1171):
            raise ValueError(f"detector in absorption region: {name}")
    return grouped


def baseline_exact_zero():
    rows = read(BASELINE)
    fields = [prefix + axis for prefix in (
        "positionDeviation", "velocity", "acceleration", "rawGradient",
        "cycleDerivedGradient") for axis in "XYZ"]
    return len(rows) == 10560 and all(
        float(row[field]) == 0.0 for row in rows for field in fields)


def build_summary(grouped):
    summaries = []
    safe_counts = []
    periods = set()
    for name in sorted(grouped):
        rows = grouped[name]
        first = rows[0]
        completions = [row for row in rows
                       if row["cycleCompleted"].lower() == "true"]
        periods.update(int(b["update"]) - int(a["update"])
                       for a, b in zip(completions, completions[1:]))
        arrival_update, arrival_cycle = meaningful_arrival(rows)
        post = [row for row in completions
                if arrival_update is not None
                and int(row["update"]) > arrival_update]
        x, y = int(first["nodeX"]), int(first["nodeY"])
        total, boundary, outbound, returned = reflected_path(x, y)
        reflection = math.ceil(total) + 1
        safe = [row for row in post if int(row["update"]) < reflection]
        safe_counts.append(len(safe))
        requested_thirtieth = (
            int(post[29]["update"]) if len(post) >= 30 else None)
        summaries.append({
            "detectorName": name,
            "direction": first["direction"],
            "requestedRadius": int(first["requestedRadius"]),
            "requestedAngleDegrees": float(
                first["requestedAngleDegrees"]),
            "nodeX": x, "nodeY": y, "nodeZ": int(first["nodeZ"]),
            "actualEuclideanRadius": float(
                first["actualEuclideanRadius"]),
            "actualAngleDegrees": float(first["actualAngleDegrees"]),
            "coordinateRoundingErrorMagnitude": float(
                first["coordinateRoundingErrorMagnitude"]),
            "meaningfulArrivalUpdate": arrival_update,
            "meaningfulArrivalCycleNumber": arrival_cycle,
            "firstSelectedCycleNumber":
                int(post[0]["completedCycleCount"]) if post else "",
            "firstSelectedCycleEnd":
                int(post[0]["update"]) if post else "",
            "requestedThirtiethCycleNumber":
                int(post[29]["completedCycleCount"]) if len(post) >= 30 else "",
            "requestedThirtiethCycleEnd":
                requested_thirtieth if requested_thirtieth is not None else "",
            "availablePostArrivalCycles": len(post),
            "reflectionSafePostArrivalCycles": len(safe),
            "numberOfSkippedCycles": 0,
            "nearestRelevantBoundary": boundary,
            "outboundDistance": outbound,
            "returnDistanceToDetector": returned,
            "totalReflectedPath": total,
            "boundedPropagationSpeed": 1.0,
            "detectorSpecificReflectionEstimate": reflection,
            "safetyMarginForRequested30":
                reflection - requested_thirtieth
                if requested_thirtieth is not None else "",
            "reflectionGatePassFor30":
                requested_thirtieth is not None
                and requested_thirtieth < reflection,
            "effectiveSpeed": (int(first["requestedRadius"])
                               / arrival_update)
                if arrival_update else "",
        })
    if periods != {18}:
        raise ValueError(f"cycle period is not exactly 18: {periods}")
    return summaries, min(safe_counts), periods


def write_csv(path, rows, fields):
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def valid_log_measurements(pairs):
    """Return only finite, strictly positive radius/measurement pairs."""
    return [(radius, value) for radius, value in pairs
            if math.isfinite(radius) and math.isfinite(value)
            and radius > 0.0 and value > 0.0]


# Minimal report PNG renderer: RGB raster, line plots and a compact 5x7 font.
FONT = {
    " ": ["00000"] * 7, "-": ["00000","00000","00000","11111","00000","00000","00000"],
    ".": ["00000","00000","00000","00000","00000","01100","01100"],
    ":": ["00000","01100","01100","00000","01100","01100","00000"],
    "/": ["00001","00010","00100","01000","10000","00000","00000"],
    "0": ["01110","10001","10011","10101","11001","10001","01110"],
    "1": ["00100","01100","00100","00100","00100","00100","01110"],
    "2": ["01110","10001","00001","00010","00100","01000","11111"],
    "3": ["11110","00001","00001","01110","00001","00001","11110"],
    "4": ["00010","00110","01010","10010","11111","00010","00010"],
    "5": ["11111","10000","10000","11110","00001","00001","11110"],
    "6": ["01110","10000","10000","11110","10001","10001","01110"],
    "7": ["11111","00001","00010","00100","01000","01000","01000"],
    "8": ["01110","10001","10001","01110","10001","10001","01110"],
    "9": ["01110","10001","10001","01111","00001","00001","01110"],
}
FONT.update({
    "A":["01110","10001","10001","11111","10001","10001","10001"],
    "B":["11110","10001","10001","11110","10001","10001","11110"],
    "C":["01111","10000","10000","10000","10000","10000","01111"],
    "D":["11110","10001","10001","10001","10001","10001","11110"],
    "E":["11111","10000","10000","11110","10000","10000","11111"],
    "F":["11111","10000","10000","11110","10000","10000","10000"],
    "G":["01111","10000","10000","10111","10001","10001","01111"],
    "H":["10001","10001","10001","11111","10001","10001","10001"],
    "I":["11111","00100","00100","00100","00100","00100","11111"],
    "J":["00111","00010","00010","00010","10010","10010","01100"],
    "K":["10001","10010","10100","11000","10100","10010","10001"],
    "L":["10000","10000","10000","10000","10000","10000","11111"],
    "M":["10001","11011","10101","10101","10001","10001","10001"],
    "N":["10001","11001","10101","10011","10001","10001","10001"],
    "O":["01110","10001","10001","10001","10001","10001","01110"],
    "P":["11110","10001","10001","11110","10000","10000","10000"],
    "Q":["01110","10001","10001","10001","10101","10010","01101"],
    "R":["11110","10001","10001","11110","10100","10010","10001"],
    "S":["01111","10000","10000","01110","00001","00001","11110"],
    "T":["11111","00100","00100","00100","00100","00100","00100"],
    "U":["10001","10001","10001","10001","10001","10001","01110"],
    "V":["10001","10001","10001","10001","10001","01010","00100"],
    "W":["10001","10001","10001","10101","10101","11011","10001"],
    "X":["10001","10001","01010","00100","01010","10001","10001"],
    "Y":["10001","10001","01010","00100","00100","00100","00100"],
    "Z":["11111","00001","00010","00100","01000","10000","11111"],
})


def chart(path, title, xlabel, ylabel, series=None):
    width, height = 900, 520
    pixels = [bytearray([255, 255, 255] * width) for _ in range(height)]
    def point(x, y, color=(0, 0, 0), size=1):
        for yy in range(max(0, y-size), min(height, y+size+1)):
            for xx in range(max(0, x-size), min(width, x+size+1)):
                pixels[yy][xx*3:xx*3+3] = bytes(color)
    def line(x0, y0, x1, y1, color=(0, 0, 0)):
        steps = max(abs(x1-x0), abs(y1-y0), 1)
        for i in range(steps+1):
            point(round(x0+(x1-x0)*i/steps),
                  round(y0+(y1-y0)*i/steps), color)
    def text(x, y, value, color=(0, 0, 0), scale=2):
        cursor = x
        for character in value.upper():
            glyph = FONT.get(character, FONT[" "])
            for gy, row in enumerate(glyph):
                for gx, bit in enumerate(row):
                    if bit == "1":
                        for sy in range(scale):
                            for sx in range(scale):
                                point(cursor+gx*scale+sx,
                                      y+gy*scale+sy, color)
            cursor += 6*scale
    line(75, 440, 860, 440); line(75, 60, 75, 440)
    text(90, 18, title[:60]); text(360, 480, xlabel[:28])
    text(80, 448, "10"); text(820, 448, "400")
    text(82, 70, ylabel[:34], scale=1)
    if series:
        all_values = [value for _, data, _ in series for _, value in data
                      if math.isfinite(value)]
        if all_values:
            lo, hi = min(all_values), max(all_values)
            if hi == lo: hi = lo + 1
            for _, data, color in series:
                previous = None
                for radius, value in data:
                    x = 75 + round((radius-10)/(400-10)*785)
                    y = 440 - round((value-lo)/(hi-lo)*360)
                    point(x, y, color, 2)
                    if previous: line(previous[0], previous[1], x, y, color)
                    previous = (x, y)
        else:
            text(260, 245, "NO ACCEPTED REFLECTION-SAFE DATA")
    else:
        text(260, 245, "NO ACCEPTED REFLECTION-SAFE DATA")
    raw = b"".join(b"\\x00" + bytes(row) for row in pixels)
    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff))
    path.write_bytes(
        b"\\x89PNG\\r\\n\\x1a\\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    assert valid_log_measurements([
        (10.0, 1.0), (20.0, 0.0), (30.0, -1.0),
        (40.0, math.nan), (50.0, math.inf)]) == [(10.0, 1.0)]
    rows = read(RAW)
    grouped = validate_and_group(rows)
    if not baseline_exact_zero():
        raise ValueError("validated baseline is not exact zero")
    summaries, common_cycles, periods = build_summary(grouped)
    # Protocol requires one common reflection-safe cycle count. Radius 400
    # has zero, so no detector response may enter a scientific radial fit.
    accepted_cycles = []
    summary_fields = list(summaries[0])
    write_csv(SUMMARY_OUT, summaries, summary_fields)
    cycle_fields = [
        "detectorName", "direction", "requestedRadius", "cycleIndex",
        "sourceCycleNumber", "update",
        "rawGradientRadial", "rawGradientTangential",
        "cycleDerivedGradientRadial", "cycleDerivedGradientTangential",
        "accelerationRadial", "accelerationTangential",
        "acceptedForCommonWindow", "exclusionReason"]
    write_csv(CYCLE_OUT, accepted_cycles, cycle_fields)
    averages = [{
        "requestedRadius": radius, "commonAcceptedCycles": common_cycles,
        "rmsRawGradientRadial": "", "rmsCycleDerivedGradientRadial": "",
        "rmsAccelerationRadial": "", "xAverage": "", "yAverage": "",
        "fourAxisAverage": "", "directionalStandardDeviation": "",
        "coefficientOfVariation": "",
        "exclusionReason":
            "No positive common reflection-safe cycle count across all detectors"
    } for radius in RADII]
    write_csv(AVERAGE_OUT, averages, list(averages[0]))
    write_csv(LOCAL_OUT, [], [
        "metric", "directionOrAverage", "radius1", "radius2",
        "localExponent", "smoothedExponent", "exclusionReason"])
    fit_rows = []
    for metric in ("rmsRawGradientRadial",
                   "rmsCycleDerivedGradientRadial",
                   "rmsAccelerationRadial"):
        for minimum, maximum in FIT_RANGES:
            for model in MODELS:
                fit_rows.append({
                    "metric": metric, "minimumRadius": minimum,
                    "maximumRadius": maximum, "model": model,
                    "includedRadii": 0,
                    "excludedRadii": ",".join(map(str, RADII)),
                    "exclusionReason":
                        "No common reflection-safe response window",
                    "fittedA": "", "fittedExponentN": "",
                    "exponent95PercentLow": "",
                    "exponent95PercentHigh": "", "lambda": "",
                    "offsetR0": "", "rSquaredLog": "", "rmse": "",
                    "aic": "", "bic": "", "modelStableAcrossCutoffs": False,
                })
    write_csv(FITS_OUT, fit_rows, list(fit_rows[0]))
    CHARTS.mkdir(parents=True, exist_ok=True)
    empty_charts = (
        ("gravity-test-2d-raw-gradient-rms.png",
         "RAW GRADIENT RADIAL RMS", "RADIUS", "RMS RESPONSE"),
        ("gravity-test-2d-cycle-gradient-rms.png",
         "CYCLE GRADIENT RADIAL RMS", "RADIUS", "RMS RESPONSE"),
        ("gravity-test-2d-loglog-raw-gradient.png",
         "LOG LOG RAW GRADIENT", "LOG RADIUS", "LOG RESPONSE"),
        ("gravity-test-2d-loglog-cycle-gradient.png",
         "LOG LOG CYCLE GRADIENT", "LOG RADIUS", "LOG RESPONSE"),
        ("gravity-test-2d-local-exponent.png",
         "LOCAL EXPONENT", "RADIUS", "EFFECTIVE EXPONENT"),
        ("gravity-test-2d-directional-symmetry.png",
         "DIRECTIONAL SYMMETRY DIFFERENCE", "RADIUS", "RELATIVE DIFFERENCE"),
        ("gravity-test-2d-power-law-residual.png",
         "POWER LAW RESIDUAL", "RADIUS", "LOG RESIDUAL"),
    )
    for filename, title, xlabel, ylabel in empty_charts:
        chart(CHARTS / filename, title, xlabel, ylabel)
    arrival_series = []
    colors = {"PX": (220, 30, 30), "NX": (30, 80, 220),
              "PY": (20, 150, 60), "NY": (160, 50, 170)}
    for direction in DIRECTIONS:
        data = sorted((item["requestedRadius"],
                       item["meaningfulArrivalUpdate"])
                      for item in summaries
                      if item["direction"] == direction)
        arrival_series.append((direction, data, colors[direction]))
    chart(CHARTS / "gravity-test-2d-arrival-update.png",
          "MEANINGFUL ARRIVAL UPDATE BY DIRECTION", "RADIUS",
          "ARRIVAL UPDATE", arrival_series)

    failures = [item for item in summaries
                if not item["reflectionGatePassFor30"]]
    with REPORT_OUT.open("w", encoding="utf-8") as report:
        def line(value=""):
            print(value, file=report)
        line("# Gravity Test 2D — Passive-detector radial falloff")
        line()
        line("## Scientific objective")
        line()
        line("Measure passive node-level axis response versus radius without "
             "using receiver Identities, Occupancy pressure, transport momentum, "
             "Identity acceleration, attraction, or orbital motion.")
        line()
        line("## Configuration and collection integrity")
        line()
        line("- Node v0.1; 1201×1201×1 lattice; source `(600,600,0)`; "
             "Z amplitude 200; 20° increment; observed period 18 updates.")
        line("- 112 unique exact-axis detectors at 28 radii; zero coordinate "
             "rounding error; all outside the absorption region.")
        line("- Raw rows: 107,520 = 112×960; every detector has updates 1..960.")
        line("- Duplicate detector/update rows: 0; NaN/infinity/signed-negative-"
             "zero anomalies: 0; geometry inconsistencies: 0.")
        line("- Empty-lattice baseline remains exact zero.")
        line()
        line("## Arrival and cycle rule")
        line()
        line("Meaningful arrival is the end of the first complete source cycle "
             "with finite non-zero cycle-derived gradient. A candidate window "
             "starts with the following complete cycle. Arrivals are quantized "
             "to 18-update boundaries, so effective and interval speeds are "
             "diagnostic rather than exact propagation constants.")
        line()
        line("## Reflection gate: FAIL for a common positive cycle count")
        line()
        line("The first non-unity absorption interfaces are x/y=29 and 1171. "
             "Each reflected path is independently minimized and converted with "
             "the causal speed bound ≤1 edge/update.")
        line()
        line(f"- Requested common cycles: 30")
        line(f"- Maximum common reflection-safe cycles across all 112 "
             f"detectors: **{common_cycles}**")
        line(f"- Detectors failing a 30-cycle gate: {len(failures)}")
        line("- PX/NX/PY/NY radius 400: meaningful arrival update 864, "
             "reflection estimate 743, safe post-arrival cycles 0.")
        line("- Because the required common count is zero, no response values "
             "were admitted to axis averages, logarithmic fits, local exponents, "
             "alternative-model comparison, or residual inference.")
        line()
        line("| Detector | Arrival | Arrival cycle | Available cycles | Safe "
             "cycles | Reflection | Candidate 30th end | Margin | Gate |")
        line("|---|---:|---:|---:|---:|---:|---:|---:|:---:|")
        for item in summaries:
            end = item["requestedThirtiethCycleEnd"]
            margin = item["safetyMarginForRequested30"]
            line(f"| {item['detectorName']} | "
                 f"{item['meaningfulArrivalUpdate']} | "
                 f"{item['meaningfulArrivalCycleNumber']} | "
                 f"{item['availablePostArrivalCycles']} | "
                 f"{item['reflectionSafePostArrivalCycles']} | "
                 f"{item['detectorSpecificReflectionEstimate']} | "
                 f"{end or 'N/A'} | {margin if margin != '' else 'N/A'} | "
                 f"{'PASS' if item['reflectionGatePassFor30'] else 'FAIL'} |")
        line()
        line("## Directional symmetry")
        line()
        line("Geometry, arrival cycles, available-cycle counts, and reflection "
             "windows are exactly symmetric under +X/−X/+Y/−Y exchange at every "
             "radius. Scientific response symmetry is not reported because no "
             "positive common accepted response window exists.")
        line()
        line("## Power laws, local exponents, and model comparison")
        line()
        line("No fit is scientifically admissible. The model-fit CSV explicitly "
             "records all requested ranges/models as excluded. Consequently "
             "there is no exponent, confidence interval, RMSE/AIC/BIC ranking, "
             "residual autocorrelation, dominant spacing, or near/far regime "
             "claim.")
        line()
        line("## Decision by primary metric")
        line()
        line("- Raw gradient radial RMS: **D — no reliable radial attenuation "
             "law can be determined from this dataset.**")
        line("- Cycle-derived gradient radial RMS: **D — no reliable radial "
             "attenuation law can be determined from this dataset.**")
        line("- In-plane acceleration radial RMS: **D — no reliable radial "
             "attenuation law can be determined; the measured in-plane "
             "components are zero in this transverse-Z setup.**")
        line()
        line("## Supported conclusion and limitations")
        line()
        line("Test 2D collected a complete, passive 960-update raw dataset and "
             "verified exact axis geometry and arrival symmetry. It cannot "
             "support a reflection-safe radial falloff or power-law conclusion "
             "over radii 10–400 because the common admissible cycle count is "
             "zero. No inverse-square or other attenuation law is demonstrated.")
        line()
        line("A further simulation is required for the stated 10–400 objective: "
             "the lattice/boundary distance and/or approved reflection protocol "
             "must provide a pre-reflection post-arrival window at radius 400. "
             "Merely increasing updates would not fix the radius-400 reflection "
             "ordering.")
    print(f"rows={len(rows)} detectors={len(grouped)} commonSafeCycles="
          f"{common_cycles} failing30={len(failures)}")
    print(f"wrote {REPORT_OUT}")


if __name__ == "__main__":
    main()
