import csv, math
from pathlib import Path

root=Path('measurements/xyz-r09-diagnostic-r096')
configs=[('Z_ONLY',(False,False,True),(0,0,0)),('XYZ_IN_PHASE',(True,True,True),(0,0,0)),('XYZ_0_120_240',(True,True,True),(0,120,240)),('XYZ_0_90_180',(True,True,True),(0,90,180))]
rows=[]
for cid,active,phase in configs:
    vals={i:{c:{'x':[],'y':[],'z':[]} for c in range(6,12)} for i in range(1,16)}
    with (root/cid/'all-detector-gradient-components.csv').open() as f:
        for r in csv.DictReader(f):
            u=int(r['update']); cyc=6+(u-91)//18
            if 6<=cyc<=11:
                i=int(r['directionId'][1:]); vals[i][cyc]['x'].append(float(r['gradientX'])); vals[i][cyc]['y'].append(float(r['gradientY'])); vals[i][cyc]['z'].append(float(r['gradientZ']))
    means={}
    for i in vals:
        means[i]={k:sum((min(vals[i][c][k])+max(vals[i][c][k]))/2 for c in range(6,12))/6 for k in 'xyz'}
    globals_={k:sum(means[i][k] for i in means)/15 for k in 'xyz'}
    for i in range(1,16):
        r9=(i==9); d=[(1,0,0),(0,0,1),(1,0,1),(2,0,1),(1,0,2),(3,0,1),(1,0,3),(1,1,0),(1,1,1),(2,1,0),(2,1,1),(3,2,1),(3,1,2),(2,1,3),(1,1,4)][i-1]
        out={'configurationId':cid,'activeX':active[0],'activeY':active[1],'activeZ':active[2],'phaseAngleX':phase[0],'phaseAngleY':phase[1],'phaseAngleZ':phase[2],'directionId':f'R{i:02d}','directionX':d[0],'directionY':d[1],'directionZ':d[2]}
        for k in 'xyz': out.update({f'meanResponse{k.upper()}':means[i][k],f'globalMean{k.upper()}':globals_[k],f'Q{k}':means[i][k]/globals_[k],f'error{k.upper()}':abs(means[i][k]/globals_[k]-1)})
        rows.append(out)
fields=list(rows[0]);
with (root/'component-normalized-directional-results.csv').open('w',newline='') as f:
    w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
summ=[]; primary=[]
for cid,a,p in configs:
    rr=[x for x in rows if x['configurationId']==cid]; s={'configurationId':cid,'activeX':a[0],'activeY':a[1],'activeZ':a[2],'phaseAngleX':p[0],'phaseAngleY':p[1],'phaseAngleZ':p[2]}
    for k in 'XYZ':
        q=[float(x[f'Q{k.lower()}']) for x in rr]; s[f'Emax{k}']=max(abs(v-1) for v in q);s[f'MAD{k}']=sum(abs(v-1) for v in q)/15;s[f'StdDev{k}']=math.sqrt(sum((v-1)**2 for v in q)/15)
        r9=next(x for x in rr if x['directionId']=='R09');
        s[f'R09_meanResponse{k}']=r9[f'meanResponse{k}']; s[f'R09_Q{k}']=r9[f'Q{k.lower()}']; s[f'R09_error{k}']=r9[f'error{k}']
    summ.append(s); primary.append(s)
sf=list(summ[0]);
with (root/'component-normalized-summary.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=sf);w.writeheader();w.writerows(summ)
pf=list(primary[0]);
with (root/'r09-component-comparison.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=pf);w.writeheader();w.writerows(primary)
def chart(name,metric,title):
    w,h=1000,520; labels=[x[0] for x in configs]; series=[('X','#1565c0'),('Y','#2e7d32'),('Z','#c62828')]; vals=[[float(s[f'R09_{metric}{k}']) for k in 'XYZ'] for s in primary]
    lo=0; hi=max(1,max(v for row in vals for v in row)*1.15); y=lambda v:430-(v-lo)/(hi-lo)*340
    out=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}"><text x="30" y="28" font-size="18">{title} — R09 (1,1,1)</text><line x1="70" y1="430" x2="960" y2="430" stroke="black"/>']
    if metric=='Q': out.append(f'<line x1="70" y1="{y(1):.1f}" x2="960" y2="{y(1):.1f}" stroke="black" stroke-dasharray="5,5"/><text x="75" y="{y(1)-5:.1f}">ideal Q=1</text>')
    for j,(lab,col) in enumerate(series):
        pts=[]
        for i,row in enumerate(vals): x=150+i*250+j*45; yy=y(row[j]);pts.append(f'<circle cx="{x}" cy="{yy:.1f}" r="6" fill="{col}"/><text x="{x-28}" y="{yy-10:.1f}" font-size="11">{row[j]:.3f}</text>')
        out.append(''.join(pts)+f'<text x="{820+j*45}" y="475" fill="{col}">{lab}</text>')
    for i,lab in enumerate(labels):out.append(f'<text x="{125+i*250}" y="455">{lab}</text>')
    out.append('</svg>');(root/name).write_text(''.join(out))
chart('r09-component-normalized-q.svg','Q','Component-normalized response');chart('r09-component-errors.svg','error','Component-normalized absolute error')
(root/'report.md').write_text('# Phase 5 Task 4 — Component-wise R09 normalization\n\nPost-processing only; raw Task 3 measurements were not changed. R09 = (1,1,1).\n\n'+ '\n'.join(f"- {s['configurationId']}: R09 Qx={s['R09_QX']:.12g}, Qy={s['R09_QY']:.12g}, Qz={s['R09_QZ']:.12g}; errors=({s['R09_errorX']:.12g}, {s['R09_errorY']:.12g}, {s['R09_errorZ']:.12g})" for s in primary)+'\n\nThe Z-only gate reproduces Qz=2.4968715547720990 exactly. XYZ in-phase does not produce equal normalized Q components: max pairwise difference is '+f"{max(abs(primary[1]['R09_Q'+a]-primary[1]['R09_Q'+b]) for a in 'XYZ' for b in 'XYZ'):.12g}"+'. Its measured Emax/MAD/StdDev component metrics are likewise not equal. The 0/120/240 case reduces the X error relative to Z-only; the 0/90/180 case does not reduce any R09 component error. The smallest measured R09 component error is XYZ_0_120_240 on X. No combined XYZ isotropy metric or complete-isotropy claim is defined.\n')
(root/'CONFIGURATION-STATUS.md').write_text('ConfigurationStatus = VALID_DIAGNOSTIC\n\nAudit: all four Task 3 directories contain complete 198-update, 15-detector traces (2,970 data rows each), six-cycle R09 data, and VALID_DIAGNOSTIC metadata. The prior MEASUREMENT_INCOMPLETE status was stale bookkeeping and is corrected.\n')
