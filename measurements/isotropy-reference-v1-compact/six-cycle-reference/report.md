# REM Framework Step 1 — Frozen six-cycle isotropy reference

## Confirmed

The validated compact backend was run independently for X, Y and Z for 198 updates (18 updates/cycle). Cycles 1–5 were discarded and cycles 6–11 were averaged.

## Valid shells

- D=24: valid.
- D=36: valid.
- D=48: unavailable/undefined because the six-cycle mean is zero.
- D=60: unavailable/undefined because the six-cycle mean is zero.
- D=72: unavailable/undefined because the six-cycle mean is zero.
- D=84: unavailable/undefined because the six-cycle mean is zero.
- D=96: unavailable/undefined because the six-cycle mean is zero.

## Six-cycle reference

- X D=24: meanResponse=0.00043779955842242970, Emax=3.6362193383923476, MAD=1.2967621738570052, StdDev=1.5311874970290285.
  strongest=R09 (1,1,1) (Q=4.6362193383923480); weakest=R02 (0,0,1) (Q=-3.4418131904890170e-08).
  Q values: R01=1.2741201e-06 R02=-3.4418132e-08 R03=0.079614363 R04=0.036820000 R05=0.012718853 R06=0.0094135419 R07=0.0016362799 R08=0.079614363 R09=4.6362193 R10=0.036820000 R11=3.7611954 R12=2.5494756 R13=2.5494756 R14=1.2293504 R15=0.017645056 
- X D=36: meanResponse=1.4084693905008155e-11, Emax=9.2003901301554920, MAD=1.4055567389005357, StdDev=2.5381590181675150.
  strongest=R09 (1,1,1) (Q=10.200390130155492); weakest=R01 (1,0,0) (Q=0.0000000000000000).
  Q values: R01=0.0000000 R02=0.0000000 R03=0.0000000 R04=0.0000000 R05=0.0000000 R06=0.0000000 R07=0.0000000 R08=0.0000000 R09=10.200390 R10=0.0000000 R11=2.3412854 R12=0.94303876 R13=0.94303876 R14=0.57056535 R15=0.0016815955 
- Y D=24: meanResponse=0.00021284630255864715, Emax=8.5361524005728540, MAD=1.4667428308824741, StdDev=2.4003237879053714.
  strongest=R09 (1,1,1) (Q=9.5361524005728540); weakest=R03 (1,0,1) (Q=-0.011687400286933743).
  Q values: R01=-7.0794008e-08 R02=-7.0794008e-08 R03=-0.011687400 R04=-0.0035644554 R05=-0.0035644554 R06=-0.00077378379 R07=-0.00077378379 R08=0.16375729 R09=9.5361524 R10=0.026161170 R11=1.9357911 R12=2.5286277 R13=0.39679027 R14=0.39679027 R15=0.036293785 
- Y D=36: meanResponse=1.1416211186264669e-11, Emax=11.584680709810375, MAD=1.5696011581213722, StdDev=3.1135798795792087.
  strongest=R09 (1,1,1) (Q=12.584680709810375); weakest=R01 (1,0,0) (Q=0.0000000000000000).
  Q values: R01=0.0000000 R02=0.0000000 R03=0.0000000 R04=0.0000000 R05=0.0000000 R06=0.0000000 R07=0.0000000 R08=0.0000000 R09=12.584681 R10=0.0000000 R11=1.1873280 R12=0.70393217 R13=0.26099224 R14=0.26099224 R15=0.0020746601 
- Z D=24: meanResponse=0.00029119285583034420, Emax=5.9704140690878430, MAD=1.3422296705644439, StdDev=1.8973005846437383.
  strongest=R09 (1,1,1) (Q=6.9704140690878430); weakest=R08 (1,1,0) (Q=-0.0085428604712955750).
  Q values: R01=-5.1746609e-08 R02=1.9156006e-06 R03=0.11969776 R04=0.019122407 R05=0.055357745 R06=0.0024600968 R07=0.014152973 R08=-0.0085428605 R09=6.9704141 R10=-0.0026054250 R11=1.4149591 R12=0.29003233 R13=1.8482907 R14=3.8330586 R15=0.44360058 
- Z D=36: meanResponse=1.2111595676995220e-11, Emax=10.862134150316146, MAD=1.4770614692653674, StdDev=2.9287807895119920.
  strongest=R09 (1,1,1) (Q=11.862134150316146); weakest=R01 (1,0,0) (Q=0.0000000000000000).
  Q values: R01=0.0000000 R02=0.0000000 R03=0.0000000 R04=0.0000000 R05=0.0000000 R06=0.0000000 R07=0.0000000 R08=0.0000000 R09=11.862134 R10=0.0000000 R11=1.1191578 R12=0.24600743 R13=0.66351607 R14=1.0966691 R15=0.012515481 

## Directional pattern

The normalized directional Q values are printed above for every valid axis/shell combination; no smoothing or correction was applied.

## X/Y/Z comparison

Exact-ray permutation matches agree within the measured comparison tolerance: X→Y matched 4 rays, X→Z matched 10 rays, and Y→Z matched 9 rays. For valid shells D=24 and D=36, each comparison had maximum and mean absolute difference 0.0. The complete measured comparison remains in `../oscillator-orientation-comparison.csv`; unavailable shells are not interpreted as isotropic.

## Canonical Step 1 baseline

Step 2 should compare against the measured six-cycle single-node compact reference in `six-cycle-isotropy-summary.csv`: valid shells D=24 and D=36 for X/Y/Z, with D=48–96 unavailable/undefined. This baseline is not changed by cycle-to-cycle variation diagnostics.
