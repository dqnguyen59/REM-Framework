package remframework.lattice;

import remframework.helpers.Constants;

public final class SystemCubicLatticeModel {
    private SystemCubicLatticeModel() {
    }

    public static LatticeModel create(boolean periodic) {
        return new CubicLatticeModel(Constants.EP_DEFAULT_DISTANCE,
                CubicLatticeModel.SYSTEM_OFFSETS, periodic, null);
    }
}
