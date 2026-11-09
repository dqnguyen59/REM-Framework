import csv,json,math
from pathlib import Path
from collections import defaultdict
import numpy as np

out=Path('measurements/diamond-shells/amplitude-audit-1794176597851')
run=Path('measurements/diamond-shells/transverse-1794176597851')
def read(path):
    with path.open(newline='') as f:return list(csv.DictReader(f))
def stats(values):
    a=np.asarray(values,dtype=float)
    return dict(count=len(a),minimum=float(a.min()),maximum=float(a.max()),mean=float(a.mean()),spread_percent=float(100*np.ptp(a)/abs(a.mean())))
def key(r):return tuple(int(r[k]) for k in ('orientation','shell','ray'))
actual=read(run/'probes.csv');replay=read(out/'replayed-probes.csv');geometry=read(out/'probe-stencils.csv');raw=read(out/'raw-node-peaks.csv')
actual_by={key(r):r for r in actual};geom=defaultdict(list)
for r in geometry:geom[key(r)].append(r)
trace_index={key(r):i for i,r in enumerate(actual)}
actual_signals=np.zeros((len(actual),154));replayed_signals=np.zeros_like(actual_signals)
with (run/'traces.csv').open(newline='') as f:
    for r in csv.DictReader(f):actual_signals[trace_index[key(r)],int(r['update'])]=float(r['parallel_displacement'])
with (out/'replayed-traces.csv').open(newline='') as f:
    for r in csv.DictReader(f):replayed_signals[trace_index[key(r)],int(r['update'])]=float(r['signal'])
ids=sorted({int(r['node_id']) for r in geometry});id_to_row={n:i for i,n in enumerate(ids)}
signals=np.zeros((len(ids),154))
with (out/'stencil-node-traces.csv').open(newline='') as f:
    for r in csv.DictReader(f):signals[id_to_row[int(r['node_id'])],int(r['update'])]=float(r['displacement'])
by_n=defaultdict(list)
for r in raw:by_n[int(r['radius_squared_integer'])].append(r)
groups=[]
for n,rows in by_n.items():
    if len(rows)>=48:groups.append(dict(radius=math.sqrt(n/3),radius_squared_integer=n,**stats([float(r['peak']) for r in rows])))
groups.sort(key=lambda r:abs(r['radius']-42))
reference_n=groups[0]['radius_squared_integer'];reference_r=math.sqrt(reference_n/3)
ref_ids=[int(r['node_id']) for r in by_n[reference_n] if int(r['node_id']) in id_to_row]
reference=np.mean(signals[[id_to_row[n] for n in ref_ids]],axis=0)
means={radius:np.mean([float(r['arrival_half_peak']) for r in actual if float(r['radius'])==radius]) for radius in (40,41,42)}
speed=2/(means[42]-means[40]);time=np.arange(154)
def radial(r,speed=speed):return reference_r/r*np.interp(time-(r-reference_r)/speed,time,reference,left=0,right=0)
ideal_point_peak=float(np.max(np.abs(radial(42))))
diagnostics=[];runtime_errors=[];timing_errors=[]
for p in replay:
    k=key(p);a=actual_by[k];stencil=geom[k];radius=float(p['radius']);total=np.zeros(154)
    rr=[];ww=[];spatial=[]
    for g in stencil:
        weight=float(g['weight']);r=float(g['node_radius'])
        total+=weight*radial(r);rr.append(r);ww.append(weight)
        spatial.append([float(g[n]) for n in ('dx','dy','dz')])
    delta=np.asarray(rr)-radius;weights=np.asarray(ww);synthetic_peak=float(np.max(np.abs(total)))
    runtime_errors.append(abs(float(p['peak'])-float(a['peak']))/float(a['peak']))
    timing_errors.append(float(a['arrival_half_peak'])-float(p['arrival']))
    diagnostics.append(dict(orientation=k[0],shell=k[1],ray=k[2],radius=radius,
        runtime_peak=float(a['peak']),replay_peak=float(p['peak']),
        weighted_node_peaks=float(p['weighted_node_peaks']),coherence_ratio=float(p['coherence_ratio']),
        synthetic_radial_peak=synthetic_peak,
        radial_variance=float(np.sum(weights*delta**2)),mean_node_radius_offset=float(np.sum(weights*delta))))
q=[r for r in diagnostics if r['radius']==42]
qactual=[r for r in actual if float(r['radius'])==42]
location=defaultdict(list)
for a in qactual:location[tuple(round(float(a[k]),6) for k in ('nx','ny','nz'))].append(a)
location_spreads=[stats([float(a['peak']) for a in rows])['spread_percent'] for rows in location.values() if len(rows)>1]
correlation=float(np.corrcoef([r['runtime_peak'] for r in q],[r['synthetic_radial_peak'] for r in q])[0,1])
orbit=defaultdict(list)
for r in raw:
    xyz=[round(float(r[k])*math.sqrt(3)) for k in ('dx','dy','dz')]
    product=xyz[0]*xyz[1]*xyz[2];sgn=0 if product==0 else (1 if product>0 else -1)
    orbit[(*sorted(map(abs,xyz)),sgn)].append(float(r['peak']))
orbit_stats=[stats(v) for v in orbit.values() if len(v)>1]
validation=dict(max_relative_peak_error=max(runtime_errors),max_peak_error_percent=100*max(runtime_errors),
    source_time_origin_offset_min=min(timing_errors),source_time_origin_offset_max=max(timing_errors),
    max_arrival_error_after_one_update_origin_alignment=max(abs(x-1) for x in timing_errors),
    maximum_spread_same_physical_receiver_different_polarization=max(location_spreads),
    maximum_leakage_rms_ratio=max(float(r['leakage_rms_ratio']) for r in qactual),
    maximum_raw_peak_spread_within_tetrahedral_symmetry_orbit=max(x['spread_percent'] for x in orbit_stats))
trace_errors=np.max(np.abs(actual_signals[:,1:]-replayed_signals[:,:153]),axis=1)/np.max(np.abs(actual_signals),axis=1)
validation['maximum_aligned_waveform_error_percent']=float(100*trace_errors.max())
shape=[]
for p in actual:
    if float(p['radius'])!=42:continue
    a=np.abs(actual_signals[trace_index[key(p)]]);j=int(a.argmax())
    curvature=a[j-1]-2*a[j]+a[j+1];offset=.5*(a[j-1]-a[j+1])/curvature
    shape.append(dict(parabolic_peak=float(a[j]-.25*(a[j-1]-a[j+1])*offset),
        energy_norm=float(np.linalg.norm(a)),absolute_area=float(a.sum())))
result=dict(validation=validation,actual_peak_spread=stats([r['runtime_peak'] for r in q]),
    weighted_node_peak_spread=stats([r['weighted_node_peaks'] for r in q]),
    interpolation_peak_retention=stats([r['coherence_ratio'] for r in q]),
    synthetic_isotropic_input=dict(definition='u(r,t)=r0/r * reference(t-(r-r0)/c); identical point signal at every equal-radius target',
        reference_radius=reference_r,reference_node_count=len(ref_ids),speed=speed,point_peak=ideal_point_peak,
        interpolated_peak_spread=stats([r['synthetic_radial_peak'] for r in q]),
        correlation_with_actual_probe_peaks=correlation),
    nearest_exact_raw_radius_groups=groups[:8])
result['waveform_shape_checks']={k:stats([r[k] for r in shape]) for k in shape[0]}
# Compare symmetry-equivalent probe directions. A tetrahedral sign/permutation
# orbit is keyed by sorted absolute coordinates and the sign of their product.
direction_by={key(r):[float(r[k]) for k in ('nx','ny','nz')] for r in actual}
probe_orbits=defaultdict(list)
for r in q:
    xyz=direction_by[(r['orientation'],r['shell'],r['ray'])]
    product=math.prod(xyz);sgn=0 if abs(product)<1e-12 else (1 if product>0 else -1)
    orbit_key=(*sorted(round(abs(v),6) for v in xyz),sgn)
    probe_orbits[orbit_key].append(r)
result['probe_symmetry_orbits']=dict(
    maximum_actual_peak_spread_percent=max(stats([r['runtime_peak'] for r in a])['spread_percent'] for a in probe_orbits.values() if len(a)>1),
    maximum_synthetic_radial_peak_spread_percent=max(stats([r['synthetic_radial_peak'] for r in a])['spread_percent'] for a in probe_orbits.values() if len(a)>1))
# Reference-profile sensitivity: these are controlled radial inputs, not a
# quantitative attribution of the real spread to interpolation alone.
synthetic_sensitivity=[]
for scale in (.8,1,1.2):
    peaks=[]
    for r in q:
        total=np.zeros(154)
        for g in geom[(r['orientation'],r['shell'],r['ray'])]:
            total+=float(g['weight'])*radial(float(g['node_radius']),speed*scale)
        peaks.append(float(np.max(np.abs(total))))
    synthetic_sensitivity.append(dict(speed_scale=scale,**stats(peaks)))
result['synthetic_speed_sensitivity']=synthetic_sensitivity
# Exact bulk Fourier-mode recurrence implied by the existing Node v0.1 order.
tetra=np.array([[1,1,1],[1,-1,-1],[-1,1,-1],[-1,-1,1]],dtype=float)/math.sqrt(3)
modes=[]
for name,direction in [('axis',[1,0,0]),('face diagonal',[1,1,0]),('body diagonal',[1,1,1])]:
    q=np.array(direction,dtype=float);q/=np.linalg.norm(q)
    lam=-4+abs(np.exp(1j*(tetra@q)).sum())
    s=.9746794344808963**2/4*lam
    roots=np.roots([1,-(2+1.5*s),1+.5*s])
    rho=float(abs(roots[0]));expected=math.sqrt(1+.5*s)
    assert abs(rho-expected)<1e-12
    modes.append(dict(direction=name,wavenumber_magnitude=1.,laplacian_eigenvalue=float(lam),
        step_eigenvalue=float(s),amplitude_retention_per_update=rho,amplitude_after_100_updates=rho**100))
result['existing_integrator_mode_example']=modes
(out/'amplitude-analysis.json').write_text(json.dumps(result,indent=2))
with (out/'probe-amplitude-diagnostics.csv').open('w',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(diagnostics[0]));writer.writeheader();writer.writerows(diagnostics)
with (out/'raw-exact-radius-groups.csv').open('w',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(groups[0]));writer.writeheader();writer.writerows(sorted(groups,key=lambda r:r['radius']))
print(json.dumps(result,indent=2))

f=lambda x:f'{x:.6f}'
report=f'''# Amplitude-spread investigation

The period-6 radius-42 survey reports **18.627066% peak-amplitude spread**. This audit confirms two contributions: a direction-dependent sampling/filtering effect and a direction-dependent response already present at unaveraged equal-radius lattice nodes. They are separate controls with different populations and must not be added or subtracted to assign shares of the measured 18.6%.

| Control | Sample population | Peak-amplitude spread |
| --- | --- | ---: |
| Original application probes | 416 interpolated targets at radius 42 | 18.627066% |
| Perfectly radial test signal through the same stencils | Same 416 radius-42 targets | {f(result['synthetic_isotropic_input']['interpolated_peak_spread']['spread_percent'])}% |
| Actual unaveraged node signals | 432 nodes, each at radius {groups[0]['radius']:.9f} | {f(groups[0]['spread_percent'])}% |

## 1. Probe averaging is a significant contributor

The production probe takes a weighted average of node displacements and then finds the peak. It does not average already-computed node peaks. The stencil contains nodes at slightly different radii and phases. At radius 42, the observed peak is between {100*result['interpolation_peak_retention']['minimum']:.3f}% and {100*result['interpolation_peak_retention']['maximum']:.3f}% of the weighted average of individual node peaks. Thus peak reduction varies from about 1.6% to 10.1% with direction. This is a coherent-signal averaging effect, not a missing-node-distance error.

The radial control defines u(r,t)=(r0/r)*f(t-(r-r0)/c), using a mean raw-node waveform at r0={reference_r:.9f} and c={speed:.9f} bonds/update. Its point signal is identical at every equal-radius target by construction. Production interpolation alone then generates {f(result['synthetic_isotropic_input']['interpolated_peak_spread']['spread_percent'])}% spread. Changing this control's propagation speed by +/-20% gives {f(synthetic_sensitivity[-1]['spread_percent'])}% to {f(synthetic_sensitivity[0]['spread_percent'])}% spread. These model-dependent controls establish a sampling effect; 8.61% is not a calibrated share that can simply be subtracted from 18.63%.

There is also a symmetry issue: the interpolator chooses one FCC primitive-grid basis for trilinear interpolation, then averages the two sublattices. It reproduces the target coordinate, but its smoothing footprint does not preserve all tetrahedral sign/permutation symmetries around the source. Among symmetry-related receiver directions, the radial control has up to {f(result['probe_symmetry_orbits']['maximum_synthetic_radial_peak_spread_percent'])}% variation. Actual raw-node symmetry-orbit variation is below {validation['maximum_raw_peak_spread_within_tetrahedral_symmetry_orbit']:.8f}%. Consequently, part of the directional probe response is introduced by sampling geometry that is not rotation-neutral even under lattice symmetries.

Relevant source: src/main/java/remframework/geometry/discovery/research/diamond/DiamondReceiverSampling.java:37-52 and src/main/java/remframework/opengl/model/TransverseIdentitySurvey.java:314.

## 2. Improving interpolation alone cannot be assumed to reach 1%

Without interpolation, 432 nodes at exactly r=sqrt(5291/3)={groups[0]['radius']:.9f} have peak displacement between {groups[0]['minimum']:.9e} and {groups[0]['maximum']:.9e} bonds: {groups[0]['spread_percent']:.6f}% spread. The nearby exact-radius group at r={groups[2]['radius']:.9f} contains {groups[2]['count']} nodes and has {groups[2]['spread_percent']:.6f}% spread. These controls have different angular populations from the 416 interpolated probes, so their percentages are not directly interchangeable. They nevertheless show that directional peak variation survives without probe averaging.

This is variation in the simulated response. It does not isolate how much originates in finite-wavelength lattice dispersion, the point-source spatial spectrum, or the current time-update scheme.

## 3. The existing integrator attenuates modes even with absorption off

Node v0.1 updates velocity before position, and then adds another half acceleration to position. With displacement u, its unforced bulk update is a_t=c*L*u_t, v_(t+1)=v_t+a_t, u_(t+1)=u_t+v_(t+1)+a_t/2, where c=gamma*k^2/4. Eliminating velocity gives u_(t+1)=(2+1.5*s)*u_t-(1+0.5*s)*u_(t-1) for a graph eigenmode with s=c*lambda.

For an oscillating mode, the characteristic-root magnitude is sqrt(1+0.5*s), below one for negative s. This is attenuation from the existing update order, rather than an enabled boundary absorber. No new damping was introduced in this audit.

On the diamond acoustic branch, lambda(q)=-4+abs(sum(exp(i*q dot e_j))), with the four unit tetrahedral bond vectors e_j. At finite wavenumber this depends on direction as well as magnitude. For the illustrative magnitude |q|=1 inverse bond:

| Direction | Graph eigenvalue | Mode retention per update | Mode amplitude after 100 updates |
| --- | ---: | ---: | ---: |
'''
for m in modes:report+=f"| {m['direction']} | {m['laplacian_eigenvalue']:.9f} | {m['amplitude_retention_per_update']:.9f} | {m['amplitude_after_100_updates']:.9f} |\n"
report+='''
This exact bulk-mode calculation identifies a mechanism for directional attenuation. It does not assign a numerical fraction of the measured pulse-amplitude spread to that mechanism, because the pulse combines many modes. A controlled integrator comparison would be needed for that attribution. The production mechanics were not changed.

Relevant source: src/main/java/remframework/opengl/model/entity/Node.java:374-396, src/main/java/remframework/opengl/model/Lattice.java:123-129, and src/main/java/remframework/lattice/CompactDiamondMechanics.java:65-85.

## Excluded or bounded explanations

'''
report+=f'''- Source polarization/projection: repeated physical receiver positions under different source polarizations agree within {validation['maximum_spread_same_physical_receiver_different_polarization']:.8f}% peak spread. Maximum perpendicular-to-parallel RMS ratio is {validation['maximum_leakage_rms_ratio']:.3e}. The componentwise linear update preserves source polarization in this experiment.
- Integer peak sampling: a parabolic three-sample peak refinement changes the measured spread from 18.6271% to 18.4074%. It does not explain the large discrepancy.
- Pulse shape: the L2 waveform norm has 8.4379% spread, and absolute time-integrated displacement has 5.3026% spread. These are diagnostic quantities, not replacements for the agreed peak-amplitude criterion. Different pulse widths help explain why peak variation exceeds integrated-signal variation.
- Distance: interpolated target distances agree to floating-point precision. The raw equal-radius controls additionally remove interpolation-distance differences.
- Reflections: the same conservative graph dependency guard was retained. Raw audit nodes lie within the already-validated receiver-radius-plus-stencil bound; all samples are at or before update 153. No later samples were used to estimate peaks.
- Incomplete pulses: the period-6 application reference has 416/416 completed pulses on all three shells.

## Why timing can pass while amplitude fails

The arrival definition uses 50% of each receiver's own peak. Multiplying a waveform by a direction-dependent gain changes its amplitude but leaves that constant-fraction arrival unchanged. Similar arrival times therefore do not force similar amplitudes. The current code implements this explicitly in src/main/java/remframework/opengl/model/TransversePulseAnalysis.java:42.

## Recommended next tests

1. Add an optional symmetry-preserving probe interpolator, with nonnegative weights, correct physical coordinates, and a matched physical smoothing scale. Validate it against the radial control and lattice-symmetry controls before interpreting new amplitude results. Keep the existing probe as a comparison.
2. Re-measure the residual using raw equal-radius node shells as well as both probe methods. Do not compensate each direction to force equal amplitudes.
3. Compare the current update with a mode-amplitude-preserving integrator in an isolated diagnostic. Any canonical mechanics change requires a separate implementation decision.
4. On the larger machine, test farther radii and longer-wavelength sources with the same complete-pulse rule. The present data does not establish a distance at which amplitude spread will fall below 1%.

## Replay validation and reproducibility

The existing CompactDiamondMechanics backend was used at 120 cubed, period 6, one cycle, force coefficient 0.01, k=0.9746794344808963, gamma=1, and no absorption. A single Cartesian response was sampled at all 1248 receiver positions; the production linear componentwise operator permits comparison with all 26 source polarizations. Peak disagreement with the original application is at most {validation['max_peak_error_percent']:.9f}%. No amplitude scaling was fitted.

The offline force is applied with a one-update-different source-time origin. Measured arrival offsets are {validation['source_time_origin_offset_min']:.9f} to {validation['source_time_origin_offset_max']:.9f} updates. After the common one-update alignment, arrival disagreement is at most {validation['max_arrival_error_after_one_update_origin_alignment']:.3e} update. Peak and equal-radius amplitude comparisons do not depend on this common time shift.

Validation: CompactDiamondMechanicsTest (3 tests) and TransversePulseAnalysisTest (5 tests) passed with no failures/errors. The diagnostic Java helper compiled successfully. No application source or preset was modified for this investigation.

Original reference: measurements/diamond-shells/transverse-1794176597851/configuration.properties, probes.csv, summary.csv, windows.csv and traces.csv. These files were read without overwriting them.

New diagnostic outputs in this directory: reference-configuration.properties, replayed-probes.csv, replayed-traces.csv, stencil-node-traces.csv, probe-stencils.csv, raw-node-peaks.csv, raw-exact-radius-groups.csv, probe-amplitude-diagnostics.csv, amplitude-analysis.json and amplitude-investigation.md. Reproduction helpers are scripts/diamond/AmplitudeMechanicsAudit.java and scripts/diamond/analyze-amplitude.py.
'''
(out/'amplitude-investigation.md').write_text(report)
