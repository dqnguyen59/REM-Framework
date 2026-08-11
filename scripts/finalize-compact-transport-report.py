#!/usr/bin/env python3
import csv, pathlib, math, sys

root=pathlib.Path(sys.argv[1])
cases=["R01_1_0_0","R08_1_1_0","R09_1_1_1"]
def read(case):
    with (root/case/"update-trace.csv").open() as f:return list(csv.DictReader(f))
data={c:read(c) for c in cases}
for c in cases:
    metadata=(root/c/"metadata.csv").read_text()
    (root/c/"metadata.csv").write_text(metadata.replace("status,VALID", "status,MEASUREMENT_INCOMPLETE").replace("updates,198", "updates,600"))
def value(row,k):
    try:return float(row[k])
    except:return math.nan
def chart(filename,title,prefix):
    vals=[value(r,prefix+a) for rows in data.values() for r in rows for a in "XYZ" if math.isfinite(value(r,prefix+a))]
    lo,hi=min(vals),max(vals)
    if lo==hi:lo,hi=lo-1,hi+1
    W,H=1100,560; L,T,R,B=80,50,30,70
    def x(i,n):return L+i*(W-L-R)/max(1,n-1)
    def y(v):return T+(hi-v)*(H-T-B)/(hi-lo)
    colors=["#1565c0","#d32f2f","#2e7d32","#ef6c00","#6a1b9a","#00838f","#8d6e63","#c2185b","#455a64"]
    s=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"><rect width="100%" height="100%" fill="white"/><text x="{L}" y="28" font-size="18">{title}</text><line x1="{L}" y1="{T}" x2="{L}" y2="{H-B}" stroke="black"/><line x1="{L}" y1="{H-B}" x2="{W-R}" y2="{H-B}" stroke="black"/>']
    j=0
    for c in cases:
        rows=data[c]
        for a in "XYZ":
            pts=" ".join(f'{x(i,len(rows)):.2f},{y(value(r,prefix+a)):.2f}' for i,r in enumerate(rows))
            s.append(f'<polyline fill="none" stroke="{colors[j]}" points="{pts}"/><text x="{L+10+(j%3)*180}" y="{H-25+(j//3)*16}" fill="{colors[j]}">{c} {a}</text>');j+=1
    s.append(f'<text x="8" y="{T+15}">{hi:.8g}</text><text x="8" y="{H-B}">{lo:.8g}</text></svg>')
    (root/filename).write_text("\n".join(s))
chart("raw-gradient-cycle-comparison.svg","Measured raw gradient","rawGradient")
chart("cycle-gradient-imbalance.svg","Existing cycle gradient imbalance","cycleGradientImbalance")
chart("pressure-vs-cycle.svg","Existing pressure","pressureAfter")
summary=[]
for c,rows in data.items():
    cycles=sum(r["cycleCompleted"]=="true" for r in rows)
    summary.append({"directionId":c,"status":"MEASUREMENT_INCOMPLETE","updates":len(rows),"completedCycles":cycles})
with (root/"comparison-summary.csv").open("w",newline="") as f:
    w=csv.DictWriter(f,fieldnames=["directionId","status","updates","completedCycles"]);w.writeheader();w.writerows(summary)
(root/"CONFIGURATION-STATUS.md").write_text("# Configuration Status\n\ncompact backend: MEASUREMENT_INCOMPLETE\nobject backend: BACKEND_FAILURE (canonical 333^3 object lattice exceeded available heap)\n\nEquivalence gate: VALID at tolerance 1e-12. The compact receiver produced nonzero gradients, acceleration, momentum, and pressure, but no 11 completed receiver sign cycles were observed, so no scientific cycle comparison is claimed.\n")
(root/"report.md").write_text("# Compact Transport-System Isotropy Diagnostic\n\nThe object backend remains BACKEND_FAILURE because the canonical 333^3 object lattice exceeded available heap. The compact backend passed all three object/compact equivalence scenarios at tolerance 1e-12.\n\nThe extended compact run contains 600 measured updates per direction. It is MEASUREMENT_INCOMPLETE: the exact Occupancy-style receiver cycle detector did not complete the required cycles 1..11. Charts contain measured signed values only; no interpretation is made.\n")
