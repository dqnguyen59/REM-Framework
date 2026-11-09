import csv
from pathlib import Path
from reportlab.graphics.shapes import Drawing, String, Rect
from reportlab.graphics.charts.lineplots import LinePlot
from reportlab.graphics import renderSVG
from reportlab.lib import colors

base=Path('measurements/diamond-shells/transverse-1794174331487')
out=Path('measurements/diamond-shells/period-comparison-1794176597851')
signals={4:[],5:[]}
with (base/'traces.csv').open(newline='') as handle:
    for row in csv.DictReader(handle):
        if row['orientation']=='7' and row['shell']=='2' and int(row['ray']) in signals:
            signals[int(row['ray'])].append((int(row['update']),float(row['parallel_displacement'])))
drawing=Drawing(1020,440)
drawing.add(Rect(0,0,1020,440,fillColor=colors.white,strokeColor=None))
drawing.add(String(30,410,'Radius 42: why 46 probes miss the quiet check by one update',fontName='Helvetica-Bold',fontSize=17))
drawing.add(String(30,386,'Period 8, one-cycle source, orientation 8 of 26. Values normalized to each probe\'s peak.',fontSize=11))
series=[]
for ray,values in signals.items():
    peak=max(abs(y) for _,y in values)
    series.append([(x,100*abs(y)/peak) for x,y in values])
for left,xmin,xmax,ymax,title in [(65,80,153,105,'Full recorded pulse'),(570,134,154,2.1,'Tail detail: final 16 samples are updates 138-153')]:
    chart=LinePlot()
    chart.x=left;chart.y=105;chart.width=400;chart.height=240
    clipped=[]
    for s in series:
        visible=[]
        for i,(x,y) in enumerate(s):
            if x<xmin or y>ymax:
                continue
            if i and s[i-1][1]>ymax:
                px,py=s[i-1]
                visible.append((px+(x-px)*(ymax-py)/(y-py),ymax))
            visible.append((x,y))
        clipped.append(visible)
    chart.data=clipped
    if xmin==134:
        drawing.add(Rect(left+(138-xmin)/(xmax-xmin)*400,105,15/(xmax-xmin)*400,240,fillColor=colors.HexColor('#edf2f7'),strokeColor=None))
        chart.data.append([(134,1),(154,1)])
        chart.lines[2].strokeColor=colors.HexColor('#444444')
        chart.lines[2].strokeDashArray=[4,3]
    chart.lines[0].strokeColor=colors.HexColor('#007e87')
    chart.lines[1].strokeColor=colors.HexColor('#bd3b74')
    chart.lines[0].strokeWidth=1.8;chart.lines[1].strokeWidth=1.8
    chart.xValueAxis.valueMin=xmin;chart.xValueAxis.valueMax=xmax
    chart.xValueAxis.valueSteps=[80,100,120,140,153] if xmin==80 else [134,138,142,146,150,153]
    chart.yValueAxis.valueMin=0;chart.yValueAxis.valueMax=ymax
    chart.yValueAxis.valueSteps=[0,25,50,75,100] if xmin==80 else [0,.5,1,1.5,2]
    chart.yValueAxis.labelTextFormat='%g%%'
    chart.xValueAxis.labels.fontSize=9;chart.yValueAxis.labels.fontSize=9
    drawing.add(chart)
    drawing.add(String(left,360,title,fontName='Helvetica-Bold',fontSize=11))
    drawing.add(String(left+145,75,'Simulation update',fontSize=10))
drawing.add(String(65,45,'Ray 5: complete (last above 1% at update 137)',fillColor=colors.HexColor('#007e87'),fontSize=11))
drawing.add(String(570,45,'Ray 6: needs update 154 (last above 1% at 138)',fillColor=colors.HexColor('#bd3b74'),fontSize=11))
drawing.add(String(65,22,'Dashed line: 1% completion threshold. Safety cutoff: 153. No samples beyond the cutoff are inferred.',fontSize=10))
renderSVG.drawToFile(drawing,str(out/'period8-radius42-tail.svg'))
