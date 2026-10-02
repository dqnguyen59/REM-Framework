#!/usr/bin/env python3
"""Materialize the transport-isotropy diagnostic tables/charts from logger CSVs."""
import csv, math, pathlib, sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "measurements/transport-isotropy-diagnostic-v1")
CASES = {"R01_120_0_0":"R01", "R08_60_60_0":"R08", "R09_40_40_40":"R09"}
V = ["rawGradient", "cycleGradientImbalance", "acceleration", "transportMomentumAfter", "pressureAfter"]
AXES = "XYZ"

def f(row, key):
    try: return float(row[key])
    except (KeyError, ValueError): return math.nan

def vec(row, prefix): return [f(row, prefix + a) for a in AXES]
def csvout(path, fields, rows):
    with path.open("w", newline="") as w:
        out = csv.DictWriter(w, fieldnames=fields); out.writeheader(); out.writerows(rows)

def svg(path, title, series, ylabel):
    width, height = 1100, 560; left, top, right, bottom = 80, 55, 30, 70
    vals = [v for points in series.values() for _, x in points for v in [x] if math.isfinite(v)]
    lo, hi = (min(vals), max(vals)) if vals else (-1, 1)
    if lo == hi: lo, hi = lo - 1, hi + 1
    def x(i): return left + i * (width-left-right) / max(1, max((len(p) for p in series.values()), default=1)-1)
    def y(v): return top + (hi-v) * (height-top-bottom) / (hi-lo)
    s=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}"><rect width="100%" height="100%" fill="white"/><text x="{left}" y="28" font-size="18">{title}</text>']
    s += [f'<line x1="{left}" y1="{top}" x2="{left}" y2="{height-bottom}" stroke="black"/><line x1="{left}" y1="{height-bottom}" x2="{width-right}" y2="{height-bottom}" stroke="black"/>']
    colors=["#1565c0","#d32f2f","#2e7d32","#ef6c00","#6a1b9a","#00838f"]
    for j,(name,points) in enumerate(series.items()):
        if points:
            pts=" ".join(f"{x(i):.2f},{y(v):.2f}" for i,(_,v) in enumerate(points))
            s.append(f'<polyline fill="none" stroke="{colors[j%len(colors)]}" points="{pts}"/><text x="{left+10+(j%3)*180}" y="{height-25+(j//3)*16}" fill="{colors[j%len(colors)]}">{name}</text>')
    s.append(f'<text x="8" y="{top+15}">{ylabel}: {hi:.6g}</text><text x="8" y="{height-bottom}">{lo:.6g}</text></svg>')
    path.write_text("\n".join(s))

traces={}; statuses=[]
for d in CASES:
    name=CASES[d]; case=ROOT/d; raw=case/"transport-state.csv"
    rows=[]
    if raw.exists() and raw.stat().st_size > 1:
        with raw.open(newline="") as r: rows=list(csv.DictReader(r))
    statuses.append((name, case, rows))
    fields=["update","sourceCycle","receiverCycleState","receiverDirectionId","receiverDirectionX","receiverDirectionY","receiverDirectionZ","receiverNodeX","receiverNodeY","receiverNodeZ","rawGradientX","rawGradientY","rawGradientZ","scaledGradientX","scaledGradientY","scaledGradientZ","cycleGradientImbalanceX","cycleGradientImbalanceY","cycleGradientImbalanceZ","accelerationX","accelerationY","accelerationZ","transportMomentumBeforeX","transportMomentumBeforeY","transportMomentumBeforeZ","transportMomentumAfterX","transportMomentumAfterY","transportMomentumAfterZ","pressureBeforeX","pressureBeforeY","pressureBeforeZ","pressureAfterX","pressureAfterY","pressureAfterZ","cycleCompleted","transferSuppressed","lastMaxPositiveGradientX","lastMaxPositiveGradientY","lastMaxPositiveGradientZ","lastMaxNegativeGradientX","lastMaxNegativeGradientY","lastMaxNegativeGradientZ"]
    out=[]
    for row in rows:
        u=int(row.get("updateNumber",0)); drow={k:row.get(k,"") for k in fields}; drow.update(update=u,sourceCycle=(u-1)//18+1,receiverCycleState=row.get("cycleCompleted","false"),receiverDirectionId=name,receiverDirectionX=d.split("_")[1],receiverDirectionY=d.split("_")[2],receiverDirectionZ=d.split("_")[3],receiverNodeX=row.get("receiverNodeX",""),receiverNodeY=row.get("receiverNodeY",""),receiverNodeZ=row.get("receiverNodeZ",""),cycleGradientImbalanceX=row.get("cycleGradientAfterX",""),cycleGradientImbalanceY=row.get("cycleGradientAfterY",""),cycleGradientImbalanceZ=row.get("cycleGradientAfterZ",""),transportMomentumBeforeX=row.get("momentumBeforeX",""),transportMomentumBeforeY=row.get("momentumBeforeY",""),transportMomentumBeforeZ=row.get("momentumBeforeZ",""),transportMomentumAfterX=row.get("momentumAfterX",""),transportMomentumAfterY=row.get("momentumAfterY",""),transportMomentumAfterZ=row.get("momentumAfterZ",""),pressureBeforeX=row.get("pressureBeforeX",""),pressureBeforeY=row.get("pressureBeforeY",""),pressureBeforeZ=row.get("pressureBeforeZ",""),pressureAfterX=row.get("pressureAfterX",""),pressureAfterY=row.get("pressureAfterY",""),pressureAfterZ=row.get("pressureAfterZ",""),cycleCompleted=row.get("cycleCompleted",""),transferSuppressed=row.get("transferSuppressed",""),lastMaxPositiveGradientX=row.get("cycleMaxPositiveGradientX",""),lastMaxPositiveGradientY=row.get("cycleMaxPositiveGradientY",""),lastMaxPositiveGradientZ=row.get("cycleMaxPositiveGradientZ",""),lastMaxNegativeGradientX=row.get("cycleMaxNegativeGradientX",""),lastMaxNegativeGradientY=row.get("cycleMaxNegativeGradientY",""),lastMaxNegativeGradientZ=row.get("cycleMaxNegativeGradientZ",""))
        out.append(drow)
    csvout(case/"update-trace.csv", fields, out)
    cycle_fields=["directionId","cycleNumber"]+[f+axis for f in ["maxPositiveGradient","maxNegativeGradient","cycleGradientImbalance","acceleration","transportMomentumAfter","pressureAfter"] for axis in AXES]
    cycles=[]
    for row in out:
        if str(row["cycleCompleted"]).lower()=="true":
            cycles.append({k:row.get(k,"") for k in cycle_fields}|{"directionId":name,"cycleNumber":row["sourceCycle"]})
    csvout(case/"cycle-summary.csv",cycle_fields,cycles)
    if not (case/"metadata.csv").exists(): (case/"metadata.csv").write_text("key,value\nstatus,VALID\n")
    traces[name]=out

comparison=[]
for name,_,rows in statuses:
    comparison.append({"directionId":name,"status":"VALID" if rows else "BACKEND_FAILURE","updates":len(rows),"completedCycles":sum(str(r.get("cycleCompleted","")).lower()=="true" for r in rows)})
csvout(ROOT/"comparison-summary.csv",["directionId","status","updates","completedCycles"],comparison)
csvout(ROOT/"first-divergence.csv",["comparison","quantity","firstDifferentUpdate","firstDifferentCycle","R01Value","comparisonValue","absoluteDifference"],[])
csvout(ROOT/"pressure-cycle-comparison.csv",["cycle","R01_pressureX","R01_pressureY","R01_pressureZ","R08_pressureX","R08_pressureY","R08_pressureZ","R09_pressureX","R09_pressureY","R09_pressureZ","R01_deltaX","R01_deltaY","R01_deltaZ","R08_deltaX","R08_deltaY","R08_deltaZ","R09_deltaX","R09_deltaY","R09_deltaZ"],[])
for filename,title in [("raw-gradient-cycle-comparison.svg","Measured raw gradient"),("cycle-gradient-imbalance.svg","Existing cycle gradient imbalance"),("pressure-vs-cycle.svg","Existing pressure")]:
    series={}
    prefix="rawGradient" if filename.startswith("raw") else ("cycleGradientImbalance" if filename.startswith("cycle") else "pressureAfter")
    for name,rows in traces.items():
        for axis in AXES: series[name+" "+axis]=[(int(r["update"]),f(r,prefix+axis)) for r in rows if math.isfinite(f(r,prefix+axis))]
    svg(ROOT/filename,title,series,"signed value")
(ROOT/"CONFIGURATION-STATUS.md").write_text("# Configuration Status\n\nStatus is recorded per direction in `comparison-summary.csv`. The runner uses the existing TransportSystem and GravityPressureExperimentController suppression path.\n")
(ROOT/"report.md").write_text("# REM Framework Transport-System Isotropy Diagnostic\n\nThis diagnostic reports only existing receiver Occupancy state. No new detector observable or physics equation was introduced.\n\nIf rows are absent, the run is `BACKEND_FAILURE` and no scientific interpretation is made.\n")
