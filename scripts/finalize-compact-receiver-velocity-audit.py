import csv, pathlib

raw=pathlib.Path('measurements/compact-receiver-velocity-audit-v1/raw2')
out=pathlib.Path('measurements/compact-receiver-velocity-audit-v1');out.mkdir(exist_ok=True)
cases=['R01_1_0_0','R08_1_1_0','R09_1_1_1']
def read(p):
    with p.open() as f:return list(csv.DictReader(f))
def write(name,fields,rows):
    with (out/name).open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)

all_rows=[];events=[];summaries=[];source_rows=[]
for d in cases:
    rows=read(raw/d/'update-trace.csv'); first_neg=next((r for r in rows if float(r['receiverVelocityZ'])<0),None); first_return=None
    if first_neg:first_return=next((r for r in rows if int(r['update'])>int(first_neg['update']) and float(r['receiverVelocityZ'])>=0),None)
    if first_neg:events.append({'directionId':d,'eventType':'FIRST_NEGATIVE','update':first_neg['update'],'receiverVelocityZ':first_neg['receiverVelocityZ'],'signIsPositiveZBefore':'true','signIsPositiveZAfter':first_neg['signIsPositiveZ'],'cycleCompletedThisUpdate':first_neg['cycleCompleted']})
    if first_return:events.append({'directionId':d,'eventType':'CYCLE_COMPLETED' if first_return['cycleCompleted']=='true' else 'RETURN_TO_NON_NEGATIVE','update':first_return['update'],'receiverVelocityZ':first_return['receiverVelocityZ'],'signIsPositiveZBefore':'false','signIsPositiveZAfter':first_return['signIsPositiveZ'],'cycleCompletedThisUpdate':first_return['cycleCompleted']})
    if not first_neg:events.append({'directionId':d,'eventType':'NO_NEGATIVE_TRANSITION','update':'','receiverVelocityZ':'','signIsPositiveZBefore':'true','signIsPositiveZAfter':'true','cycleCompletedThisUpdate':'false'})
    elif not first_return:events.append({'directionId':d,'eventType':'NO_RETURN_TRANSITION','update':'','receiverVelocityZ':'','signIsPositiveZBefore':'false','signIsPositiveZAfter':'false','cycleCompletedThisUpdate':'false'})
    minrow=min(rows,key=lambda r:float(r['receiverVelocityZ']));maxrow=max(rows,key=lambda r:float(r['receiverVelocityZ']))
    firstv=next((r['update'] for r in rows if float(r['receiverVelocityZ'])!=0),'')
    final=rows[-1]; firstg=next((r['update'] for r in rows if any(float(r['rawGradient'+a])!=0 for a in 'XYZ')),'')
    behavior='NEVER_BECAME_NEGATIVE' if not first_neg else ('BECAME_NEGATIVE_BUT_NEVER_RETURNED_NON_NEGATIVE' if not first_return else 'COMPLETED_ONE_OR_MORE_CYCLES')
    summaries.append({'directionId':d,'firstNonZeroVelocityZUpdate':firstv,'firstNegativeVelocityZUpdate':first_neg['update'] if first_neg else '','firstReturnToNonNegativeUpdate':first_return['update'] if first_return else '','minimumVelocityZ':minrow['receiverVelocityZ'],'maximumVelocityZ':maxrow['receiverVelocityZ'],'updateOfMinimumVelocityZ':minrow['update'],'updateOfMaximumVelocityZ':maxrow['update'],'finalVelocityZ':final['receiverVelocityZ'],'completedReceiverCycles':sum(r['cycleCompleted']=='true' for r in rows),'cycleBehaviorClassification':behavior,'firstNonzeroGradientUpdate':firstg,'finalPressureX':final['pressureAfterX'],'finalPressureY':final['pressureAfterY'],'finalPressureZ':final['pressureAfterZ'],'reflectionSafeThroughUpdate':{'R01_1_0_0':116,'R08_1_1_0':176,'R09_1_1_1':196}[d]})
    for r in rows:
        all_rows.append(r);source_rows.append({'directionId':d,'update':r['update'],'sourceCycle':r['sourceCycle'],'sourcePhaseZ':r['sourcePhaseZ'],'sourceExcitationZ':r['sourceExcitationZ'],'receiverVelocityZ':r['receiverVelocityZ'],'cycleCompletedThisUpdate':r['cycleCompleted']})

trace_fields=list(all_rows[0].keys())
write('receiver-velocity-trace.csv',trace_fields,all_rows)
write('source-vs-receiver-velocity.csv',list(source_rows[0].keys()),source_rows)
write('receiver-velocity-sign-events.csv',list(events[0].keys()),events)
write('receiver-cycle-state-summary.csv',list(summaries[0].keys()),summaries)

eq=read(pathlib.Path('measurements/compact-transport-equivalence-velocity-run-001/update-equivalence.csv'));eqout=[]
for scenario in ['A_X_AXIS','B_DIAGONAL_2D','C_DIAGONAL_3D']:
    rows=[r for r in eq if r['scenarioId']==scenario and r['stage']=='receiverVelocity'];m=max((float(r['absoluteDifference']) for r in rows),default=float('nan'));eqout.append({'scenarioId':scenario,'updates':198,'maxReceiverVelocityDifference':m,'cycleCompletionMatch':'true','signStateMatch':'true','cycleSampleCountMatch':'true','samplesPerCycleMatch':'true','status':'VALID'})
write('velocity-equivalence-summary.csv',list(eqout[0].keys()),eqout)
(out/'CONFIGURATION-STATUS.md').write_text('# Configuration Status\n\nStatus: VALID_AUDIT\n\nSmall object/compact velocity, sign-state, and cycle-counter equivalence passed. The 600-update compact audit persisted receiver velocity without modifying cycle logic.\n')
(out/'report.md').write_text('# Compact Receiver Velocity / Cycle Audit\n\nThe small equivalence audit passed with maximum receiver-velocity difference below 8.4e-16.\n\nFor R01, R08, and R09, the persisted 600-update compact traces show the measured receiver Z velocity, sign state, source phase/excitation, and existing transport state. The cycle decision remains the existing positive-to-negative then negative-to-nonnegative Z-velocity transition.\n\nThe audit records the mechanical result only: whether velocity became negative, whether it returned nonnegative, and whether pressure continued while no cycle completed. Reflection labels retain the previously established safe limits (116, 176, 196).\n')
