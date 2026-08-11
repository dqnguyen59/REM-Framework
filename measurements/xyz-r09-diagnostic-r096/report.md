# Phase 5 Task 4 — Component-wise R09 normalization

Post-processing only; raw Task 3 measurements were not changed. R09 = (1,1,1).

- Z_ONLY: R09 Qx=2.50000682566, Qy=2.50003426473, Qz=2.49687155477; errors=(1.50000682566, 1.50003426473, 1.49687155477)
- XYZ_IN_PHASE: R09 Qx=2.50679110946, Qy=2.50171387466, Qz=2.50474423936; errors=(1.50679110946, 1.50171387466, 1.50474423936)
- XYZ_0_120_240: R09 Qx=2.48016113454, Qy=2.49968212071, Qz=2.49785088345; errors=(1.48016113454, 1.49968212071, 1.49785088345)
- XYZ_0_90_180: R09 Qx=2.52667932136, Qy=2.50035964949, Qz=2.4976883998; errors=(1.52667932136, 1.50035964949, 1.4976883998)

The Z-only gate reproduces Qz=2.4968715547720990 exactly. XYZ in-phase does not produce equal normalized Q components: max pairwise difference is 0.0050772347975. Its measured Emax/MAD/StdDev component metrics are likewise not equal. The 0/120/240 case reduces the X error relative to Z-only; the 0/90/180 case does not reduce any R09 component error. The smallest measured R09 component error is XYZ_0_120_240 on X. No combined XYZ isotropy metric or complete-isotropy claim is defined.
