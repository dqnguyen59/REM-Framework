package remframework.geometry.discovery.realization.session;

/** A presentation-only normalization of an authoritative scientific error. */
public record NormalizedErrorRatio(boolean available, double value, boolean passes,
        ConstraintPresentationStatus status, String explanation) {
    public static NormalizedErrorRatio evaluate(double error, double tolerance,
            boolean exactZeroTolerancePermitted) {
        if (!Double.isFinite(error) || error < 0)
            return unavailable(ConstraintPresentationStatus.NUMERICALLY_INVALID,
                    "The measured error is negative or non-finite.");
        if (!Double.isFinite(tolerance) || tolerance < 0)
            return unavailable(ConstraintPresentationStatus.NUMERICALLY_INVALID,
                    "The tolerance is negative or non-finite.");
        if (tolerance == 0) {
            if (!exactZeroTolerancePermitted)
                return unavailable(ConstraintPresentationStatus.NUMERICALLY_INVALID,
                        "This constraint requires a positive tolerance.");
            if (error == 0) return new NormalizedErrorRatio(true, 0, true,
                    ConstraintPresentationStatus.SATISFIED, "Exact zero tolerance is satisfied.");
            return new NormalizedErrorRatio(true, Double.POSITIVE_INFINITY, false,
                    ConstraintPresentationStatus.EXACT_TOLERANCE_EXCEEDED,
                    "A nonzero error exceeds the exact-zero tolerance.");
        }
        double ratio=error/tolerance;
        boolean passes=ratio<=1;
        return new NormalizedErrorRatio(true,ratio,passes,
                passes?ConstraintPresentationStatus.SATISFIED:ConstraintPresentationStatus.OUTSIDE_TOLERANCE,
                passes?"Measured error is within tolerance.":"Measured error exceeds tolerance.");
    }
    public static NormalizedErrorRatio unavailable(ConstraintPresentationStatus status,String explanation){
        return new NormalizedErrorRatio(false,Double.NaN,false,status,explanation);
    }
}
