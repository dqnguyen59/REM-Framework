package remframework.geometry.discovery.realization.session;

/** Scientific diagnostics for one immutable initialization coordinate snapshot. */
public record InitializationMeasurement(String snapshot,double edgeError,double facePlanarityError,
        double faceAngleError,double faceDistanceError,int convexityViolations,
        int selfIntersections,String qualificationState) { }
